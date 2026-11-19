package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import app.krista.extensions.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Durable OAuth token storage, backed by the platform {@link KeyValueStore}.
 *
 * <p>This is the <b>same</b> store the OAuth callback writes to and every catalog request reads
 * from. Using a different (e.g. in-memory) store in either place would make a token saved during
 * sign-in invisible to real business logic, so the user would be asked to re-authorise forever.
 *
 * <p>Keys are namespaced so an operator connection and a per-user connection can never collide:
 * <ul>
 *   <li>operator — {@code <invokerId>_monday_token}</li>
 *   <li>per-user — {@code <accountId>_monday_user_token}</li>
 * </ul>
 *
 * <p>Tokens are never logged. monday.com's tokens are believed not to expire until revoked and may
 * not come with a refresh token, so {@code refreshToken} is optional throughout.
 */
@Service
public class MondayTokenStore {

    private static final Logger LOG = LoggerFactory.getLogger(MondayTokenStore.class);
    private static final Gson GSON = new Gson();

    private static final String OPERATOR_SUFFIX = "_monday_token";
    private static final String USER_SUFFIX = "_monday_user_token";

    private final KeyValueStore keyValueStore;

    @Inject
    public MondayTokenStore(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    /** A stored token set. {@code refreshToken} and {@code expiresAtMillis} may be null. */
    public static final class StoredToken {
        public String accessToken;
        public String refreshToken;
        public Long expiresAtMillis;
        public String scope;

        public StoredToken() {
        }

        public StoredToken(String accessToken, String refreshToken, Long expiresAtMillis, String scope) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
            this.scope = scope;
        }

        /** True when monday told us an expiry and that moment has passed (60s safety margin). */
        public boolean isExpired() {
            return expiresAtMillis != null && System.currentTimeMillis() > (expiresAtMillis - 60_000L);
        }

        public boolean hasRefreshToken() {
            return refreshToken != null && !refreshToken.isBlank();
        }

        @Override
        public String toString() {
            // Never render the token values.
            return "StoredToken{hasAccessToken=" + (accessToken != null && !accessToken.isBlank())
                    + ", hasRefreshToken=" + hasRefreshToken()
                    + ", expiresAtMillis=" + expiresAtMillis + '}';
        }
    }

    public static String operatorKey(String invokerId) {
        return invokerId + OPERATOR_SUFFIX;
    }

    public static String userKey(String accountId) {
        return accountId + USER_SUFFIX;
    }

    public void save(String key, StoredToken token) {
        keyValueStore.put(key, GSON.toJson(token));
        LOG.info("Stored monday.com OAuth token for key {} (refreshTokenPresent={})",
                key, token.hasRefreshToken());
    }

    public StoredToken load(String key) {
        Object stored = keyValueStore.get(key);
        if (stored == null) {
            return null;
        }
        try {
            JsonObject object = JsonParser.parseString(String.valueOf(stored)).getAsJsonObject();
            StoredToken token = new StoredToken();
            token.accessToken = optString(object, "accessToken");
            token.refreshToken = optString(object, "refreshToken");
            token.scope = optString(object, "scope");
            if (object.has("expiresAtMillis") && !object.get("expiresAtMillis").isJsonNull()) {
                token.expiresAtMillis = object.get("expiresAtMillis").getAsLong();
            }
            return token.accessToken == null || token.accessToken.isBlank() ? null : token;
        } catch (RuntimeException e) {
            LOG.error("Could not deserialize a stored monday.com token for key {}", key);
            return null;
        }
    }

    public void remove(String key) {
        keyValueStore.remove(key);
    }

    private static String optString(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
    }
}
