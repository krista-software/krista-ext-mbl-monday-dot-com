package app.krista.extensions.essentials.collaboration.mblmondaydotcom;

import app.krista.extension.executor.Invoker;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * monday.com connection configuration and credentials.
 *
 * <p>Two authentication modes are supported, selected by the {@link #AUTH_TYPE} Setup-tab field:
 * <ul>
 *   <li>{@code ApiKey} — an admin pastes one monday.com API token; every user's actions run as that
 *       token's owner (Krista auth Pattern A).</li>
 *   <li>{@code OAuth} — interactive authorization-code sign-in; each Krista user connects their own
 *       monday.com account (Krista auth Pattern C).</li>
 * </ul>
 *
 * <p>Never log, serialise into a catalog response, or return in plaintext any of the secured values
 * ({@link #API_TOKEN}, {@link #CLIENT_SECRET}). {@link #toString()} is deliberately redacted.
 */
@Service
public class MondayAttributes {

    private static final Logger LOG = LoggerFactory.getLogger(MondayAttributes.class);

    public static final String AUTH_TYPE = "Auth Type";
    public static final String API_TOKEN = "API Token";
    public static final String CLIENT_ID = "Client ID";
    public static final String CLIENT_SECRET = "Client Secret";
    public static final String SCOPES = "OAuth Scopes";
    public static final String API_VERSION = "API Version";
    public static final String TIMEOUT_SECONDS = "Timeout Seconds";
    public static final String MAX_BOARDS_TO_SCAN = "Max Boards To Scan";

    public static final String AUTH_TYPE_OAUTH = "OAuth";
    public static final String AUTH_TYPE_API_KEY = "ApiKey";

    /**
     * monday.com's current stable API version. The supplied reference was introspected at the
     * 2027-01 release candidate and recommends pinning this one; it is a Setup field so an operator
     * can move it without a rebuild when monday promotes a newer stable version.
     */
    public static final String DEFAULT_API_VERSION = "2026-10";

    /**
     * Scopes approved for this build. These are authorize-URL scopes and apply to the OAuth mode
     * only; a pasted API token already carries its owner's permissions.
     */
    public static final String DEFAULT_SCOPES = "me:read users:read boards:read boards:write updates:read updates:write";

    public static final String API_BASE_URL = "https://api.monday.com/v2";
    public static final String OAUTH_AUTHORIZE_URL = "https://auth.monday.com/oauth2/authorize";
    public static final String OAUTH_TOKEN_URL = "https://auth.monday.com/oauth2/token";

    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_MAX_BOARDS_TO_SCAN = 25;

    private String authType = AUTH_TYPE_OAUTH;
    private String apiToken = "";
    private String clientId = "";
    private String clientSecret = "";
    private String scopes = DEFAULT_SCOPES;
    private String apiVersion = DEFAULT_API_VERSION;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private int maxBoardsToScan = DEFAULT_MAX_BOARDS_TO_SCAN;

    @Inject
    public MondayAttributes(Invoker invoker) {
        this(invoker == null ? Map.of() : invoker.getAttributes());
    }

    public MondayAttributes(Map<String, Object> attributes) {
        update(attributes);
    }

    public static MondayAttributes create(Invoker invoker, Map<String, Object> attributes) {
        MondayAttributes result = new MondayAttributes(invoker);
        result.update(attributes);
        return result;
    }

    public static MondayAttributes create(Map<String, Object> attributes) {
        return new MondayAttributes(attributes);
    }

    public final void update(Map<String, Object> attributes) {
        this.authType = normaliseAuthType(getString(attributes, AUTH_TYPE, AUTH_TYPE_OAUTH));
        this.apiToken = getString(attributes, API_TOKEN, "").trim();
        this.clientId = getString(attributes, CLIENT_ID, "").trim();
        this.clientSecret = getString(attributes, CLIENT_SECRET, "").trim();
        this.scopes = blankToDefault(getString(attributes, SCOPES, ""), DEFAULT_SCOPES);
        this.apiVersion = blankToDefault(getString(attributes, API_VERSION, ""), DEFAULT_API_VERSION);
        this.timeoutSeconds = getInt(attributes, TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
        this.maxBoardsToScan = getInt(attributes, MAX_BOARDS_TO_SCAN, DEFAULT_MAX_BOARDS_TO_SCAN);
        // Deliberately logs only non-secret values.
        LOG.info("monday.com attributes updated - authType={}, apiVersion={}, maxBoardsToScan={}",
                authType, apiVersion, maxBoardsToScan);
    }

    /** Serialisable form used by the attribute store. Secrets are included — the store encrypts. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(AUTH_TYPE, authType);
        map.put(API_TOKEN, apiToken);
        map.put(CLIENT_ID, clientId);
        map.put(CLIENT_SECRET, clientSecret);
        map.put(SCOPES, scopes);
        map.put(API_VERSION, apiVersion);
        map.put(TIMEOUT_SECONDS, String.valueOf(timeoutSeconds));
        map.put(MAX_BOARDS_TO_SCAN, String.valueOf(maxBoardsToScan));
        return map;
    }

    private static String normaliseAuthType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTH_TYPE_OAUTH;
        }
        return AUTH_TYPE_API_KEY.equalsIgnoreCase(raw.trim()) ? AUTH_TYPE_API_KEY : AUTH_TYPE_OAUTH;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static String getString(Map<String, Object> attributes, String key, String defaultValue) {
        Object value = attributes == null ? null : attributes.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int getInt(Map<String, Object> attributes, String key, int defaultValue) {
        String raw = getString(attributes, key, "");
        if (raw.isBlank()) {
            return defaultValue;
        }
        try {
            // Krista numeric fields arrive as Double-ish strings ("25.0") — parse tolerantly.
            int parsed = (int) Double.parseDouble(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            LOG.warn("Attribute '{}' is not numeric; falling back to {}", key, defaultValue);
            return defaultValue;
        }
    }

    public boolean isOAuthMode() {
        return AUTH_TYPE_OAUTH.equals(authType);
    }

    public boolean isApiKeyMode() {
        return AUTH_TYPE_API_KEY.equals(authType);
    }

    public String getAuthType() {
        return authType;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getScopes() {
        return scopes;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxBoardsToScan() {
        return maxBoardsToScan;
    }

    public String getApiBaseUrl() {
        return API_BASE_URL;
    }

    @Override
    public String toString() {
        // No secrets, ever — this object ends up in log lines and exception context.
        return "MondayAttributes{authType=" + authType
                + ", apiVersion=" + apiVersion
                + ", clientIdConfigured=" + !clientId.isEmpty()
                + ", apiTokenConfigured=" + !apiToken.isEmpty()
                + ", maxBoardsToScan=" + maxBoardsToScan + '}';
    }
}
