package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The single point where a monday.com failure becomes a typed {@link MondayApiException}.
 *
 * <p>Two things make monday different from an ordinary REST API and both are handled here:
 * <ol>
 *   <li>Most failures arrive as <b>HTTP 200 with a populated {@code errors} array</b>, so the status
 *       code alone is not enough to decide success.</li>
 *   <li>Throttling shows up both as HTTP 429 and as a GraphQL {@code ComplexityException} inside a
 *       200 response.</li>
 * </ol>
 *
 * <p>Nothing produced here contains a token, a client secret or an Authorization header value.
 */
public final class MondayErrorTranslator {

    private MondayErrorTranslator() {
    }

    /** Maps an HTTP transport status to a typed exception. */
    public static MondayApiException fromHttpStatus(int status, String operation, String vendorMessage) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("httpStatus", status);
        String detail = terse(vendorMessage);
        return switch (status) {
            case 401 -> new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                    "MONDAY_UNAUTHENTICATED",
                    "monday.com rejected the credentials" + withDetail(detail) + ".",
                    operation, context,
                    "Re-authorise the connection: in OAuth mode sign in again, in API-key mode paste a "
                            + "current token on the Setup tab.");
            case 403 -> new MondayApiException(MondayApiException.Kind.PERMISSION,
                    "MONDAY_FORBIDDEN",
                    "The monday.com account is not permitted to perform this operation" + withDetail(detail) + ".",
                    operation, context,
                    "Check the signed-in user's board permissions and that the app was granted the "
                            + "boards:read / boards:write scopes.");
            case 404 -> new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_NOT_FOUND",
                    "monday.com could not find the requested resource" + withDetail(detail) + ".",
                    operation, context,
                    "Verify the board or item id and that it has not been deleted.");
            case 429 -> new MondayApiException(MondayApiException.Kind.RATE_LIMIT,
                    "MONDAY_RATE_LIMITED",
                    "monday.com rate limit reached" + withDetail(detail) + ".",
                    operation, context,
                    "Retry in a few seconds, lower the Limit input, or narrow the search to one board.");
            default -> {
                if (status >= 500) {
                    yield new MondayApiException(MondayApiException.Kind.NETWORK,
                            "MONDAY_SERVER_ERROR",
                            "monday.com returned a server error (HTTP " + status + ")" + withDetail(detail) + ".",
                            operation, context,
                            "This is usually transient — retry shortly. If it persists, check status.monday.com.");
                }
                yield new MondayApiException(MondayApiException.Kind.GRAPHQL,
                        "MONDAY_HTTP_" + status,
                        "monday.com returned HTTP " + status + withDetail(detail) + ".",
                        operation, context,
                        "Check the request inputs; if they look correct, retry.");
            }
        };
    }

    /**
     * Maps a GraphQL {@code errors} array (returned inside an HTTP 200) to a typed exception.
     * Returns {@code null} when the array holds nothing actionable.
     */
    public static MondayApiException fromGraphQlErrors(JsonArray errors, JsonObject fullBody, String operation) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        String message = firstMessage(errors);
        String errorCode = extractErrorCode(errors, fullBody);
        Map<String, Object> context = new LinkedHashMap<>();
        if (errorCode != null && !errorCode.isBlank()) {
            context.put("mondayErrorCode", errorCode);
        }
        String haystack = ((errorCode == null ? "" : errorCode) + " " + message).toLowerCase(Locale.ROOT);

        if (haystack.contains("complexity")) {
            return new MondayApiException(MondayApiException.Kind.COMPLEXITY,
                    "MONDAY_COMPLEXITY_BUDGET_EXCEEDED",
                    "The query exceeded monday.com's complexity budget" + withDetail(message) + ".",
                    operation, context,
                    "Reduce Limit, set Include Column Values to false, or restrict the search to a single board.");
        }
        if (haystack.contains("ratelimit") || haystack.contains("rate limit") || haystack.contains("too many requests")) {
            return new MondayApiException(MondayApiException.Kind.RATE_LIMIT,
                    "MONDAY_RATE_LIMITED",
                    "monday.com rate limit reached" + withDetail(message) + ".",
                    operation, context,
                    "Retry in a few seconds or lower the Limit input.");
        }
        if (haystack.contains("unauthorized") || haystack.contains("unauthenticated")
                || haystack.contains("not authenticated") || haystack.contains("invalid token")) {
            return new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                    "MONDAY_UNAUTHENTICATED",
                    "monday.com rejected the credentials" + withDetail(message) + ".",
                    operation, context,
                    "Re-authorise the connection from the Setup tab and try again.");
        }
        if (haystack.contains("forbidden") || haystack.contains("permission") || haystack.contains("not allowed")) {
            return new MondayApiException(MondayApiException.Kind.PERMISSION,
                    "MONDAY_FORBIDDEN",
                    "The monday.com account lacks permission for this operation" + withDetail(message) + ".",
                    operation, context,
                    "Ask a monday.com admin to grant access to this board, or re-authorise with the "
                            + "boards:write scope.");
        }
        if (haystack.contains("resourcenotfound") || haystack.contains("not found")
                || haystack.contains("does not exist") || haystack.contains("invalidboardid")
                || haystack.contains("inviteditemid") || haystack.contains("invaliditemid")) {
            return new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_NOT_FOUND",
                    "monday.com could not find the requested board, item or group" + withDetail(message) + ".",
                    operation, context,
                    "Verify the id with Get Boards / Get Items and confirm the signed-in user can see it.");
        }
        if (haystack.contains("columnvalue") || haystack.contains("invalid value")
                || haystack.contains("invalidcolumn") || haystack.contains("correctcolumn")) {
            return new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_INVALID_COLUMN_VALUE",
                    "monday.com rejected a column value" + withDetail(message) + ".",
                    operation, context,
                    "Run Get Board Columns to see the column's type and permitted labels, then retry.");
        }
        return new MondayApiException(MondayApiException.Kind.GRAPHQL,
                errorCode == null || errorCode.isBlank() ? "MONDAY_GRAPHQL_ERROR" : "MONDAY_" + errorCode,
                "monday.com returned a GraphQL error" + withDetail(message) + ".",
                operation, context,
                "Check the request inputs against the board's configuration and retry.");
    }

    /** A board id / item id that is not a positive integer never reaches the network. */
    public static MondayApiException invalidId(String fieldName, String value, String operation) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(fieldName, value);
        return new MondayApiException(MondayApiException.Kind.VALIDATION,
                "MONDAY_INVALID_ID",
                fieldName + " must be a numeric monday.com id.",
                operation, context,
                "Supply the numeric id — Get Boards and Get Items both return it.");
    }

    public static MondayApiException missingInput(String fieldName, String operation) {
        return new MondayApiException(MondayApiException.Kind.VALIDATION,
                "MONDAY_MISSING_REQUIRED_INPUT",
                fieldName + " is required.",
                operation, Map.of("field", fieldName),
                "Provide " + fieldName + " and run the request again.");
    }

    public static MondayApiException network(String operation, Throwable cause) {
        return new MondayApiException(MondayApiException.Kind.NETWORK,
                "MONDAY_NETWORK_ERROR",
                "Could not reach monday.com.",
                operation, Map.of("cause", cause.getClass().getSimpleName()),
                "Check appliance network access to api.monday.com and retry.",
                cause);
    }

    private static String firstMessage(JsonArray errors) {
        for (JsonElement element : errors) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("message") && !object.get("message").isJsonNull()) {
                    return object.get("message").getAsString();
                }
            } else if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
        }
        return "";
    }

    private static String extractErrorCode(JsonArray errors, JsonObject fullBody) {
        for (JsonElement element : errors) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (object.has("extensions") && object.get("extensions").isJsonObject()) {
                JsonObject extensions = object.getAsJsonObject("extensions");
                if (extensions.has("code") && !extensions.get("code").isJsonNull()) {
                    return extensions.get("code").getAsString();
                }
            }
        }
        if (fullBody != null && fullBody.has("error_code") && !fullBody.get("error_code").isJsonNull()) {
            return fullBody.get("error_code").getAsString();
        }
        return null;
    }

    private static String withDetail(String detail) {
        String terse = terse(detail);
        return terse.isEmpty() ? "" : ": " + terse;
    }

    private static String terse(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() > 220 ? trimmed.substring(0, 220) + "…" : trimmed;
    }
}
