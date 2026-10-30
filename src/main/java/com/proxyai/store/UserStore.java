package com.proxyai.store;

import com.proxyai.model.UserAccount;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacenamiento EN MEMORIA de usuarios.
 *
 * Limitacion deliberada (la actividad no exige base de datos): todo el estado
 * (planes, consumo e historial) se pierde cuando el proceso se reinicia o la
 * instancia de Render se duerme por inactividad. No es persistente.
 */
@Component
public class UserStore {

    private final ConcurrentHashMap<String, UserAccount> users = new ConcurrentHashMap<>();

    public UserAccount create() {
        String id = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        UserAccount account = new UserAccount(id);
        users.put(id, account);
        return account;
    }

    /**
     * Recupera un usuario cuyo token ya fue verificado. Si el proceso se reinicio,
     * el token firmado sigue siendo valido (mismo secreto) y se recrea la cuenta vacia.
     */
    public UserAccount getOrRecreate(String verifiedId) {
        return users.computeIfAbsent(verifiedId, UserAccount::new);
    }

    public Optional<UserAccount> find(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public Collection<UserAccount> all() {
        return users.values();
    }

    public int size() {
        return users.size();
    }
}
