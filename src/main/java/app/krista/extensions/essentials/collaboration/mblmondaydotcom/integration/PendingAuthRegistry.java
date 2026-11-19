package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import app.krista.extensions.util.KeyValueStore;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Remembers which end-user a pending sign-in belongs to.
 *
 * <p>When a real end-user (or an MCP agent acting as one) invokes a catalog request and has no
 * monday.com token yet, the client provider raises {@code MustAuthorizeException}. The platform then
 * asks the request authenticator for a sign-in URL — but the authenticator is a different object,
 * invoked later, and must still know whose sign-in this is so the resulting token is stored in the
 * per-user keyspace rather than the operator's.
 *
 * <p>This tiny registry carries that one fact across the gap, keyed by invoker.
 */
@Service
public class PendingAuthRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PendingAuthRegistry.class);
    private static final String PREFIX = "monday_pending_user_";

    private final KeyValueStore keyValueStore;

    @Inject
    public PendingAuthRegistry(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    public void recordPendingUser(String invokerId, String accountId) {
        if (invokerId == null || accountId == null || accountId.isBlank()) {
            return;
        }
        keyValueStore.put(PREFIX + invokerId, accountId);
        LOG.info("Recorded a pending per-user monday.com sign-in for invoker {}", invokerId);
    }

    /** Returns the account id awaiting sign-in for this invoker, or null. */
    public String pendingUser(String invokerId) {
        if (invokerId == null) {
            return null;
        }
        Object stored = keyValueStore.get(PREFIX + invokerId);
        return stored == null ? null : String.valueOf(stored);
    }

    public void clearPendingUser(String invokerId) {
        if (invokerId != null) {
            keyValueStore.remove(PREFIX + invokerId);
        }
    }
}
