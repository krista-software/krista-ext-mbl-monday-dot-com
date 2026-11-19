package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import app.krista.extensions.util.KeyValueStore;
import com.google.gson.Gson;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

/**
 * Persists {@link MondayAttributes} as JSON in the platform {@link KeyValueStore}.
 *
 * <p>Two uses:
 * <ul>
 *   <li>{@code save(attrs, invokerId)} / {@code load(invokerId)} — the permanent per-invoker config.</li>
 *   <li>{@code save(attrs)} — a transient {@code authContextId_<uuid>} key that carries candidate
 *       config through the browser OAuth round-trip before it has been saved; the caller removes the
 *       key in a finally block.</li>
 * </ul>
 *
 * <p>KeyValueStore encrypts at rest — no additional encryption layer is added here.
 */
@Service
public class MondayAttributeStore {

    private static final Logger LOG = LoggerFactory.getLogger(MondayAttributeStore.class);
    private static final Gson GSON = new Gson();

    private final KeyValueStore keyValueStore;

    @Inject
    public MondayAttributeStore(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    /** Permanent: keyed by invoker id (operator connection) or a per-user key. */
    public void save(MondayAttributes attributes, String key) {
        keyValueStore.put(key, GSON.toJson(attributes.toMap()));
        LOG.info("Saved monday.com attributes for key {}", key);
    }

    /** Transient: a random authContextId key, removed by the caller in a finally block. */
    public String save(MondayAttributes attributes) {
        String key = "authContextId_" + UUID.randomUUID();
        keyValueStore.put(key, GSON.toJson(attributes.toMap()));
        return key;
    }

    @SuppressWarnings("unchecked")
    public MondayAttributes load(String key) {
        Object stored = keyValueStore.get(key);
        if (stored == null) {
            return null;
        }
        try {
            Map<String, Object> map = GSON.fromJson(String.valueOf(stored), Map.class);
            return MondayAttributes.create(map);
        } catch (RuntimeException e) {
            LOG.error("Failed to deserialize monday.com attributes for key {}", key, e);
            return null;
        }
    }

    public void remove(String key) {
        keyValueStore.remove(key);
    }
}
