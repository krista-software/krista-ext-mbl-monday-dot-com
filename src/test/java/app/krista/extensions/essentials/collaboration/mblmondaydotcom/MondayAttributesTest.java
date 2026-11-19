package app.krista.extensions.essentials.collaboration.mblmondaydotcom;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Connection configuration, defaults, and the no-secrets-in-toString contract. */
class MondayAttributesTest {

    private static Map<String, Object> raw(String authType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(MondayAttributes.AUTH_TYPE, authType);
        map.put(MondayAttributes.API_TOKEN, "super-secret-token-value");
        map.put(MondayAttributes.CLIENT_ID, "client-id-123");
        map.put(MondayAttributes.CLIENT_SECRET, "client-secret-456");
        return map;
    }

    @Test
    void defaultsToOAuthWhenNoAuthTypeIsSaved() {
        MondayAttributes attributes = MondayAttributes.create(new LinkedHashMap<>());
        assertTrue(attributes.isOAuthMode());
        assertFalse(attributes.isApiKeyMode());
    }

    @Test
    void recognisesApiKeyModeCaseInsensitively() {
        assertTrue(MondayAttributes.create(raw("apikey")).isApiKeyMode());
        assertTrue(MondayAttributes.create(raw("ApiKey")).isApiKeyMode());
        assertTrue(MondayAttributes.create(raw("OAuth")).isOAuthMode());
    }

    @Test
    void appliesTheApprovedScopeListAndCurrentApiVersionByDefault() {
        MondayAttributes attributes = MondayAttributes.create(new LinkedHashMap<>());
        assertEquals("2026-10", attributes.getApiVersion());
        assertTrue(attributes.getScopes().contains("boards:write"));
        assertTrue(attributes.getScopes().contains("updates:write"));
        assertTrue(attributes.getScopes().contains("me:read"));
    }

    @Test
    void anOperatorCanOverrideTheApiVersionWithoutARebuild() {
        Map<String, Object> map = raw("OAuth");
        map.put(MondayAttributes.API_VERSION, "2027-01");
        assertEquals("2027-01", MondayAttributes.create(map).getApiVersion());
    }

    @Test
    void numericFieldsTolerateTheDoubleShapedStringsKristaSends() {
        Map<String, Object> map = raw("OAuth");
        map.put(MondayAttributes.MAX_BOARDS_TO_SCAN, "40.0");
        map.put(MondayAttributes.TIMEOUT_SECONDS, "90");
        MondayAttributes attributes = MondayAttributes.create(map);
        assertEquals(40, attributes.getMaxBoardsToScan());
        assertEquals(90, attributes.getTimeoutSeconds());
    }

    @Test
    void anUnreadableNumberFallsBackToTheDefaultInsteadOfFailing() {
        Map<String, Object> map = raw("OAuth");
        map.put(MondayAttributes.MAX_BOARDS_TO_SCAN, "lots");
        assertEquals(MondayAttributes.DEFAULT_MAX_BOARDS_TO_SCAN,
                MondayAttributes.create(map).getMaxBoardsToScan());
    }

    @Test
    void toStringNeverLeaksASecret() {
        String rendered = MondayAttributes.create(raw("ApiKey")).toString();
        assertFalse(rendered.contains("super-secret-token-value"));
        assertFalse(rendered.contains("client-secret-456"));
        assertTrue(rendered.contains("apiTokenConfigured=true"));
    }

    @Test
    void roundTripsThroughTheStoreMapWithoutLosingConfiguration() {
        MondayAttributes original = MondayAttributes.create(raw("ApiKey"));
        MondayAttributes restored = MondayAttributes.create(original.toMap());
        assertEquals(original.getAuthType(), restored.getAuthType());
        assertEquals(original.getApiToken(), restored.getApiToken());
        assertEquals(original.getApiVersion(), restored.getApiVersion());
        assertEquals(original.getMaxBoardsToScan(), restored.getMaxBoardsToScan());
    }

    @Test
    void usesMondaysOfficialEndpoints() {
        assertEquals("https://api.monday.com/v2", MondayAttributes.API_BASE_URL);
        assertEquals("https://auth.monday.com/oauth2/authorize", MondayAttributes.OAUTH_AUTHORIZE_URL);
        assertEquals("https://auth.monday.com/oauth2/token", MondayAttributes.OAUTH_TOKEN_URL);
    }
}
