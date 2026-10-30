# Atrium · Backend — Plataforma de IA con Proxy de rate limiting y cuotas

Backend en **Java 21 + Spring Boot 3.5** para el taller del **Patrón Proxy** (Patrones de Software).
Simula un servicio de generación de texto y lo protege con dos proxies encadenados:
uno limita las solicitudes por minuto y el otro controla la cuota mensual de tokens.

> Frontend: repositorio `ai-quota-frontend` (React + Vite, desplegado en Vercel).

## Patrón Proxy aplicado

```
Cliente (AiController)
   │  depende solo de la interfaz AIGenerationService
   ▼
RateLimitProxyService   ── @Primary ──  ¿superó N req/min?  → 429 + Retry-After
   ▼
QuotaProxyService                       ¿cuota mensual agotada? → 402 Payment Required
   ▼                                    (si pasa: descuenta tokens del request)
MockAIGenerationService (objeto real)   Thread.sleep(1200 ms) + texto aleatorio predefinido
```

| Rol del patrón | Clase |
|---|---|
| Sujeto (interfaz común) | `service/AIGenerationService` → `generate(GenerationRequest): GenerationResponse` |
| Objeto real | `service/MockAIGenerationService` |
| Proxy 1 (control de ritmo) | `proxy/RateLimitProxyService` |
| Proxy 2 (control de cuota) | `proxy/QuotaProxyService` |
| Cliente | `controller/AiController` (recibe el bean `@Primary` y no sabe que hay proxies) |

La cadena se arma por inyección: `RateLimitProxyService` recibe `@Qualifier("quotaProxyService")`, y este recibe `@Qualifier("realAIGenerationService")`.

## Planes

| Plan | Requests/min | Tokens/mes | Precio simulado |
|---|---|---|---|
| FREE | 10 | 50 000 | $0 |
| PRO | 60 | 500 000 | $19 |
| ENTERPRISE | ilimitado | ilimitado | $199 |

Los tokens se estiman con la heurística de **~4 caracteres por token** (`TokenEstimator`). El frontend usa la misma fórmula para su estimador. El consumo de cada request es *tokens del prompt + tokens de la respuesta*.

## Endpoints

Todos los endpoints `/api/ai/**` y `/api/quota/**` requieren `Authorization: Bearer <token>`.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/session` | Crea un usuario anónimo y devuelve un token firmado. Si ya envías un token válido, lo reutiliza. |
| POST | `/api/ai/generate` | `{ "prompt": "..." }` → pasa por RateLimit → Quota → Mock. |
| GET | `/api/quota/status` | Plan, tokens usados y restantes, `resetDate` y estado del rate limit. |
| GET | `/api/quota/history` | Uso diario (tokens y requests) de los últimos 7 días. |
| POST | `/api/quota/upgrade` | Pago simulado y upgrade FREE → PRO (también PRO/FREE → ENTERPRISE, ver *Extensiones*). |
| POST | `/api/quota/simulate-usage` | *Demo:* suma `{ "tokens": N }` al consumo para probar el 402 sin enviar cientos de prompts. |
| GET | `/api/plans` | Planes y parámetros del estimador. |
| GET | `/actuator/health` | Health check (usado por Render). |

### Códigos de respuesta

| Código | `code` | Cuándo |
|---|---|---|
| 200 | — | OK. `/generate` incluye `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Reset`. |
| 400 | `VALIDATION_ERROR` / `MALFORMED_JSON` | Prompt vacío, más de 4000 caracteres o JSON inválido. |
| 401 | `UNAUTHORIZED` | Falta el token o la firma no es válida. |
| 402 | `QUOTA_EXCEEDED` | Cuota mensual agotada o insuficiente para el prompt. |
| 402 | `PAYMENT_DECLINED` | Tarjeta inválida (Luhn), vencida o tarjeta de rechazo. |
| 409 | `INVALID_PLAN_CHANGE` | Upgrade al mismo plan o a uno inferior. |
| 429 | `RATE_LIMIT_EXCEEDED` | Límite por minuto superado. Incluye el header **`Retry-After`** en segundos. |

Todos los errores comparten el formato:
`{ timestamp, status, error, code, message, path, details? }`.

### Ejemplos (curl)

```bash
API=http://localhost:8080   # o la URL pública de Render

TOKEN=$(curl -s -X POST $API/api/session | jq -r .token)

curl -s -X POST $API/api/ai/generate \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"prompt":"Explica el patrón Proxy"}' | jq

curl -s $API/api/quota/status  -H "Authorization: Bearer $TOKEN" | jq
curl -s $API/api/quota/history -H "Authorization: Bearer $TOKEN" | jq

# Provocar el 429 (plan FREE = 10/min)
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code} " -X POST $API/api/ai/generate \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"prompt":"hola"}'
done

# Provocar el 402 (agota la cuota con la herramienta de demo)
curl -s -X POST $API/api/quota/simulate-usage -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"tokens":50000}'

# Upgrade con pago simulado (4242… aprueba, 4000 0000 0000 0002 rechaza)
curl -s -X POST $API/api/quota/upgrade -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"targetPlan":"PRO","cardHolder":"Ada Lovelace","cardNumber":"4242 4242 4242 4242","expiry":"12/30","cvc":"123"}' | jq
```

## Tareas programadas (`QuotaResetScheduler`)

- `0 * * * * *`: en el segundo 0 de cada minuto reinicia los contadores de requests por minuto (ventana fija).
- `0 0 0 1 * *`: a las 00:00 del día 1 de cada mes reinicia la cuota mensual de tokens.
- `0 5 0 * * *`: cada día borra el historial de más de 31 días para acotar la memoria.

Las expresiones cron usan la zona horaria `APP_TIME_ZONE` (por defecto `America/Bogota`).

## Identidad y seguridad

- El navegador **no** elige su ID. `POST /api/session` crea el usuario en el servidor y devuelve `userId.firmaHMAC-SHA256`. Cada request verifica esa firma (`TokenService`, `AuthInterceptor`), así que un ID inventado o alterado recibe 401.
- El secreto de firma se toma de `APP_TOKEN_SECRET`. Si no existe, se genera uno aleatorio al arrancar y los tokens dejan de valer tras un reinicio.
- La creación de sesiones está limitada a 20 por minuto por IP, para que no se puedan evadir las cuotas creando usuarios sin parar.
- CORS solo permite los orígenes de `CORS_ALLOWED_ORIGINS` y expone `Retry-After` y `X-RateLimit-*` para que el frontend pueda leerlos.
- Los datos de tarjeta del pago simulado solo se validan. No se guardan ni se escriben en logs; en la respuesta solo aparecen los últimos 4 dígitos.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `PORT` | 8080 | Puerto HTTP (Render lo inyecta). |
| `APP_TOKEN_SECRET` | aleatorio | Secreto HMAC, de 32 caracteres o más. **Obligatorio en producción.** |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos, separados por coma. En producción, solo el dominio de Vercel. |
| `AI_MOCK_DELAY_MS` | 1200 | Latencia simulada del modelo. |
| `APP_TIME_ZONE` | America/Bogota | Zona horaria de los resets. |
| `APP_DEMO_TOOLS` | true | Habilita `/api/quota/simulate-usage`. |

Hay una plantilla en `.env.example`. Nunca subas un `.env` real.

## Ejecutar localmente

```bash
mvn spring-boot:run
mvn test                    # 14 pruebas: cadena de proxies, 429, 402, upgrade, CORS, auth
```

## Despliegue en Render

1. *New → Web Service* (o *Blueprint* con `render.yaml`) apuntando a este repositorio. Runtime: **Docker**.
2. Variables: `APP_TOKEN_SECRET` (el blueprint lo genera) y `CORS_ALLOWED_ORIGINS=https://<tu-app>.vercel.app`.
3. Health check path: `/actuator/health`.

## Limitaciones conocidas (decisiones deliberadas)

- **El almacenamiento es en memoria (`ConcurrentHashMap`) y no es persistente.** Planes, consumo e historial se pierden cuando el proceso se reinicia: en cada redeploy y cuando la instancia gratuita de Render se duerme tras unos 15 minutos sin tráfico. Como el secreto HMAC es estable, los tokens siguen siendo válidos y el usuario se recrea vacío en plan FREE. Para persistir habría que implementar `UserStore` sobre una base de datos (por ejemplo PostgreSQL) sin tocar los proxies.
- **Solo funciona con una instancia.** Los contadores viven en un proceso. Para escalar horizontalmente se necesitaría un almacén compartido, como Redis.
- **El rate limit es de ventana fija por minuto de reloj.** Cumple el requisito de "reset cada minuto", pero permite ráfagas en el borde entre dos ventanas. Una ventana deslizante o un *token bucket* lo evitaría.
- **Los tokens se estiman con una heurística.** No hay un tokenizador real.
- **La comprobación de cuota no es transaccional.** Dos requests simultáneas cerca del límite pueden pasar ambas; el cobro se recorta para que el consumo nunca supere el límite.

## Extensiones respecto al enunciado

- Upgrade a **ENTERPRISE**, para poder probar el plan ilimitado y su badge. El enunciado solo exige FREE → PRO, que funciona tal cual.
- Endpoint de demostración `simulate-usage`, que se puede desactivar con `APP_DEMO_TOOLS=false`.
- Endpoints `/api/session` y `/api/plans`, más headers `X-RateLimit-*`.
