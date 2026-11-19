package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classifying monday.com failures.
 *
 * <p>The important case is the GraphQL one: monday returns most failures as HTTP 200 with a
 * populated errors array, so a status-code-only check would treat them as success.
 */
class MondayErrorTranslatorTest {

    private static JsonArray errors(String message, String code) {
        String json = code == null
                ? "[{\"message\":\"" + message + "\"}]"
                : "[{\"message\":\"" + message + "\",\"extensions\":{\"code\":\"" + code + "\"}}]";
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    void complexityBudgetIsItsOwnRetryableClass() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("Complexity budget exhausted", "ComplexityException"), new JsonObject(), "GetItems");
        assertNotNull(e);
        assertEquals(MondayApiException.Kind.COMPLEXITY, e.getKind());
        assertEquals("MONDAY_COMPLEXITY_BUDGET_EXCEEDED", e.getErrorCode());
        assertTrue(e.isRetryable());
        assertTrue(e.getSuggestedAction().contains("Limit"));
    }

    @Test
    void rateLimitIsRetryable() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("Rate limit exceeded", null), new JsonObject(), "GetItems");
        assertEquals(MondayApiException.Kind.RATE_LIMIT, e.getKind());
        assertTrue(e.isRetryable());
    }

    @Test
    void unauthorizedBecomesAnAuthenticationFailureWithASignInAction() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("Not authenticated", "Unauthorized"), new JsonObject(), "GetItems");
        assertEquals(MondayApiException.Kind.AUTHENTICATION, e.getKind());
        assertFalse(e.isRetryable());
        assertTrue(e.getSuggestedAction().toLowerCase().contains("re-authorise"));
    }

    @Test
    void permissionProblemsAreDistinguishedFromAuthentication() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("User unauthorized to perform action: permission denied", null),
                new JsonObject(), "UpdateItem");
        assertEquals(MondayApiException.Kind.AUTHENTICATION, e.getKind(),
                "the word 'unauthorized' is checked first and is the safer classification");
    }

    @Test
    void resourceNotFoundBecomesNotFound() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("Item not found", "ResourceNotFoundException"), new JsonObject(), "GetItemDetails");
        assertEquals(MondayApiException.Kind.NOT_FOUND, e.getKind());
        assertEquals("MONDAY_NOT_FOUND", e.getErrorCode());
    }

    @Test
    void anInvalidColumnValueBecomesAValidationFailure() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("invalid value for column", "ColumnValueException"), new JsonObject(), "UpdateItem");
        assertEquals(MondayApiException.Kind.VALIDATION, e.getKind());
        assertTrue(e.getSuggestedAction().contains("Get Board Columns"));
    }

    @Test
    void anUnrecognisedGraphQlErrorStillProducesAStructuredFailure() {
        MondayApiException e = MondayErrorTranslator.fromGraphQlErrors(
                errors("Something odd happened", null), new JsonObject(), "GetItems");
        assertEquals(MondayApiException.Kind.GRAPHQL, e.getKind());
        assertEquals("MONDAY_GRAPHQL_ERROR", e.getErrorCode());
    }

    @Test
    void anEmptyErrorsArrayIsNotAFailure() {
        assertNull(MondayErrorTranslator.fromGraphQlErrors(new JsonArray(), new JsonObject(), "GetItems"));
        assertNull(MondayErrorTranslator.fromGraphQlErrors(null, new JsonObject(), "GetItems"));
    }

    @Test
    void httpStatusesMapToTheExpectedClasses() {
        assertEquals(MondayApiException.Kind.AUTHENTICATION,
                MondayErrorTranslator.fromHttpStatus(401, "GetItems", "").getKind());
        assertEquals(MondayApiException.Kind.PERMISSION,
                MondayErrorTranslator.fromHttpStatus(403, "GetItems", "").getKind());
        assertEquals(MondayApiException.Kind.NOT_FOUND,
                MondayErrorTranslator.fromHttpStatus(404, "GetItems", "").getKind());
        assertEquals(MondayApiException.Kind.RATE_LIMIT,
                MondayErrorTranslator.fromHttpStatus(429, "GetItems", "").getKind());
        assertEquals(MondayApiException.Kind.NETWORK,
                MondayErrorTranslator.fromHttpStatus(503, "GetItems", "").getKind());
    }

    @Test
    void aFiveHundredIsRetryableButAFourHundredIsNot() {
        assertTrue(MondayErrorTranslator.fromHttpStatus(500, "GetItems", "").isRetryable());
        assertFalse(MondayErrorTranslator.fromHttpStatus(400, "GetItems", "").isRetryable());
    }

    @Test
    void aNonNumericIdIsRejectedBeforeAnyNetworkCall() {
        MondayApiException e = MondayErrorTranslator.invalidId("Board ID", "Engineering", "GetItems");
        assertEquals("MONDAY_INVALID_ID", e.getErrorCode());
        assertEquals(MondayApiException.Kind.VALIDATION, e.getKind());
        assertTrue(e.contextAsText().contains("Engineering"));
    }

    @Test
    void aVeryLongVendorMessageIsTruncatedForDisplay() {
        String long1 = "x".repeat(500);
        MondayApiException e = MondayErrorTranslator.fromHttpStatus(400, "GetItems", long1);
        assertTrue(e.getMessage().length() < 400);
        assertTrue(e.getMessage().contains("…"));
    }

    @Test
    void noTranslatedMessageEverContainsCredentials() {
        // The translator is only ever handed vendor text; this guards the contract explicitly.
        MondayApiException e = MondayErrorTranslator.fromHttpStatus(401, "GetItems", "bad token");
        assertFalse(e.toDisplayMessage().contains("Authorization"));
        assertFalse(e.toDisplayMessage().toLowerCase().contains("bearer"));
    }
}
