package app.krista.extensions.essentials.collaboration.mblmondaydotcom.util;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayErrorTranslator;

/**
 * Input validation that runs <em>before</em> any network call, so a bad id or a missing required
 * field fails fast with a clear message instead of surfacing as a cryptic GraphQL error.
 */
public final class Validation {

    /** monday.com's hard ceiling for a single items page. */
    public static final int MAX_PAGE_SIZE = 500;
    public static final int DEFAULT_PAGE_SIZE = 25;
    /** Keeps a cross-board fan-out from running away; the caller can lower it, not raise it. */
    public static final int MAX_BOARDS_HARD_CAP = 100;

    private Validation() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /** Requires a non-blank value, else a structured missing-input error. */
    public static String requireText(String value, String fieldName, String operation) {
        if (isBlank(value)) {
            throw MondayErrorTranslator.missingInput(fieldName, operation);
        }
        return value.trim();
    }

    /**
     * Requires a monday.com numeric id.
     *
     * <p>monday ids are numeric; catching a non-numeric value here turns "Board ID: Engineering"
     * into an actionable message instead of a GraphQL type error.
     */
    public static String requireId(String value, String fieldName, String operation) {
        String trimmed = requireText(value, fieldName, operation);
        if (!isNumericId(trimmed)) {
            throw MondayErrorTranslator.invalidId(fieldName, trimmed, operation);
        }
        return trimmed;
    }

    /** Validates an optional id: blank passes through as null, a non-numeric value fails. */
    public static String optionalId(String value, String fieldName, String operation) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!isNumericId(trimmed)) {
            throw MondayErrorTranslator.invalidId(fieldName, trimmed, operation);
        }
        return trimmed;
    }

    public static boolean isNumericId(String value) {
        return value != null && value.matches("\\d+");
    }

    /** Clamps a caller-supplied page size into monday's permitted range rather than erroring. */
    public static int pageSize(Double requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        int value = (int) Math.round(requested);
        if (value <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    /** Clamps the cross-board fan-out width. */
    public static int boardScanCap(Double requested, int configuredDefault) {
        int fallback = configuredDefault > 0 ? configuredDefault : 25;
        if (requested == null) {
            return Math.min(fallback, MAX_BOARDS_HARD_CAP);
        }
        int value = (int) Math.round(requested);
        if (value <= 0) {
            return Math.min(fallback, MAX_BOARDS_HARD_CAP);
        }
        return Math.min(value, MAX_BOARDS_HARD_CAP);
    }

    /** Treats a null Boolean as the given default — Krista optional booleans arrive as null. */
    public static boolean booleanOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    /** A hex colour such as {@code #037f4c}; monday rejects anything else. */
    public static String optionalHexColour(String value, String fieldName, String operation) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (!trimmed.matches("#?[0-9a-fA-F]{6}")) {
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_INVALID_COLOUR",
                    fieldName + " must be a six-digit hex colour.",
                    operation, java.util.Map.of(fieldName, trimmed),
                    "Use a value such as #037f4c.");
        }
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }
}
