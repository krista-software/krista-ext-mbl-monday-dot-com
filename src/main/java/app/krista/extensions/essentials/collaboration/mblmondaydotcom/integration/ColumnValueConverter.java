package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a user-supplied value into the exact JSON shape monday.com expects for that column's type.
 *
 * <p>Requirement §6.7. The formats implemented here are the ones documented in the supplied API
 * reference:
 * <ul>
 *   <li>{@code people} — {@code {"personsAndTeams":[{"id":123,"kind":"person"}]}} (this
 *       <b>replaces</b> the list; monday offers no append argument)</li>
 *   <li>{@code status} — {@code {"label":"Done"}}</li>
 *   <li>{@code date} — {@code {"date":"YYYY-MM-DD"}}</li>
 *   <li>{@code dropdown} — {@code {"labels":["A","B"]}}</li>
 * </ul>
 * Everything else uses monday's documented simple forms. A column type this converter does not know
 * how to write produces a clear UNSUPPORTED_COLUMN error rather than a silently wrong value.
 */
public final class ColumnValueConverter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    /** Resolves one or more owner references (name, email or numeric id) to monday person ids. */
    @FunctionalInterface
    public interface OwnerResolver {
        List<String> resolveToPersonIds(String ownerReferences);
    }

    private ColumnValueConverter() {
    }

    /**
     * Converts {@code rawValue} for {@code column}.
     *
     * @param rawValue the caller's value, already known to be non-blank.
     * @return the JSON element to place under this column's id in {@code column_values}.
     * @throws MondayApiException when the value is not valid for the column (unknown status label,
     *                            unparseable date, unsupported column type).
     */
    public static JsonElement convert(ColumnDefinition column, String rawValue,
                                      OwnerResolver ownerResolver, String operation) {
        String type = column.getLowerType();
        String value = rawValue.trim();

        return switch (type) {
            case "people", "person" -> peopleValue(column, value, ownerResolver, operation);
            case "status", "color" -> statusValue(column, value, operation);
            case "dropdown" -> dropdownValue(column, value, operation);
            case "date" -> dateValue(column, value, operation);
            case "timeline" -> timelineValue(column, value, operation);
            case "numbers", "numeric", "number" -> numberValue(column, value, operation);
            case "checkbox", "boolean" -> checkboxValue(value);
            case "email" -> twoPartValue("email", value);
            case "link" -> twoPartValue("url", value);
            case "phone" -> phoneValue(value);
            case "text", "long_text", "long-text", "longtext", "name" -> new JsonPrimitive(value);
            case "tags", "board_relation", "dependency" -> passthroughJson(column, value, operation);
            default -> passthroughJson(column, value, operation);
        };
    }

    private static JsonElement peopleValue(ColumnDefinition column, String value,
                                           OwnerResolver resolver, String operation) {
        if (resolver == null) {
            throw unsupported(column, operation, "Owner resolution is not available for this operation.");
        }
        List<String> personIds = resolver.resolveToPersonIds(value);
        if (personIds.isEmpty()) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("column", column.getTitle());
            context.put("value", value);
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_UNKNOWN_OWNER",
                    "No monday.com user matched '" + value + "'.",
                    operation, context,
                    "Use Search Users to find the exact name or email, or supply the numeric user id.");
        }
        JsonArray personsAndTeams = new JsonArray();
        for (String personId : personIds) {
            JsonObject person = new JsonObject();
            try {
                person.addProperty("id", Long.parseLong(personId));
            } catch (NumberFormatException e) {
                person.addProperty("id", personId);
            }
            person.addProperty("kind", "person");
            personsAndTeams.add(person);
        }
        JsonObject result = new JsonObject();
        // NOTE: this REPLACES the column's whole person list — monday has no append argument.
        result.add("personsAndTeams", personsAndTeams);
        return result;
    }

    private static JsonElement statusValue(ColumnDefinition column, String value, String operation) {
        if (column.hasLabels()) {
            String canonical = column.canonicalLabel(value);
            if (canonical == null) {
                throw invalidLabel(column, value, operation);
            }
            JsonObject result = new JsonObject();
            result.addProperty("label", canonical);
            return result;
        }
        // No label catalogue discovered (an unusual board) — send the caller's text as-is and let
        // monday arbitrate rather than blocking a legitimate write.
        JsonObject result = new JsonObject();
        result.addProperty("label", value);
        return result;
    }

    private static JsonElement dropdownValue(ColumnDefinition column, String value, String operation) {
        JsonArray labels = new JsonArray();
        for (String part : splitList(value)) {
            if (column.hasLabels()) {
                String canonical = column.canonicalLabel(part);
                if (canonical == null) {
                    throw invalidLabel(column, part, operation);
                }
                labels.add(canonical);
            } else {
                labels.add(part);
            }
        }
        JsonObject result = new JsonObject();
        result.add("labels", labels);
        return result;
    }

    private static JsonElement dateValue(ColumnDefinition column, String value, String operation) {
        JsonObject result = new JsonObject();
        result.addProperty("date", normaliseDate(column, value, operation));
        return result;
    }

    private static JsonElement timelineValue(ColumnDefinition column, String value, String operation) {
        List<String> parts = splitList(value);
        if (parts.size() != 2) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("column", column.getTitle());
            context.put("value", value);
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_INVALID_TIMELINE",
                    "A timeline column needs two dates.",
                    operation, context,
                    "Supply the range as 'YYYY-MM-DD,YYYY-MM-DD'.");
        }
        JsonObject result = new JsonObject();
        result.addProperty("from", normaliseDate(column, parts.get(0), operation));
        result.addProperty("to", normaliseDate(column, parts.get(1), operation));
        return result;
    }

    private static JsonElement numberValue(ColumnDefinition column, String value, String operation) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("column", column.getTitle());
            context.put("value", value);
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_INVALID_NUMBER",
                    "Column '" + column.getTitle() + "' only accepts a number.",
                    operation, context,
                    "Supply a numeric value such as 42.");
        }
        // monday's numbers column takes the value as a string.
        return new JsonPrimitive(value);
    }

    private static JsonElement checkboxValue(String value) {
        boolean checked = "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
                || "1".equals(value) || "checked".equalsIgnoreCase(value);
        JsonObject result = new JsonObject();
        result.addProperty("checked", String.valueOf(checked));
        return result;
    }

    private static JsonElement twoPartValue(String key, String value) {
        // "someone@example.com|Label" or just the raw value, which doubles as its own display text.
        String[] parts = value.split("\\|", 2);
        JsonObject result = new JsonObject();
        result.addProperty(key, parts[0].trim());
        result.addProperty("text", parts.length > 1 ? parts[1].trim() : parts[0].trim());
        return result;
    }

    private static JsonElement phoneValue(String value) {
        String[] parts = value.split("\\|", 2);
        JsonObject result = new JsonObject();
        result.addProperty("phone", parts[0].trim());
        result.addProperty("countryShortName", parts.length > 1 ? parts[1].trim() : "US");
        return result;
    }

    /**
     * Last resort for a column type with no dedicated mapping: accept raw JSON the caller supplied
     * verbatim, otherwise refuse clearly rather than guessing a shape.
     */
    private static JsonElement passthroughJson(ColumnDefinition column, String value, String operation) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JsonParser.parseString(trimmed);
            } catch (RuntimeException ignored) {
                // fall through to the error below
            }
        }
        throw unsupported(column, operation,
                "Supply the value as raw monday.com JSON for this column type, or set it in monday.com directly.");
    }

    private static MondayApiException unsupported(ColumnDefinition column, String operation, String action) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("column", column.getTitle());
        context.put("columnId", column.getId());
        context.put("columnType", column.getType());
        return new MondayApiException(MondayApiException.Kind.UNSUPPORTED_COLUMN,
                "MONDAY_UNSUPPORTED_COLUMN_TYPE",
                "Column '" + column.getTitle() + "' is of type '" + column.getType()
                        + "', which this extension cannot write directly.",
                operation, context, action);
    }

    private static MondayApiException invalidLabel(ColumnDefinition column, String value, String operation) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("column", column.getTitle());
        context.put("value", value);
        context.put("validLabels", String.join(", ", column.labelTexts()));
        return new MondayApiException(MondayApiException.Kind.VALIDATION,
                "MONDAY_INVALID_LABEL",
                "'" + value + "' is not a valid value for column '" + column.getTitle() + "'.",
                operation, context,
                "Use one of: " + String.join(", ", column.labelTexts()) + ".");
    }

    /** Accepts an ISO date, an ISO timestamp or epoch milliseconds and returns {@code yyyy-MM-dd}. */
    static String normaliseDate(ColumnDefinition column, String value, String operation) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return trimmed;
        }
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}[T ].*")) {
            return trimmed.substring(0, 10);
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return formatEpochMillis(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("column", column == null ? "" : column.getTitle());
        context.put("value", value);
        throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                "MONDAY_INVALID_DATE",
                "'" + value + "' is not a date this extension can read.",
                operation, context,
                "Supply the date as YYYY-MM-DD.");
    }

    /** Krista {@code @Field.Date} inputs arrive as epoch milliseconds. */
    public static String formatEpochMillis(long epochMillis) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Splits a comma- or semicolon-separated list, trimming blanks. */
    public static List<String> splitList(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null) {
            return parts;
        }
        for (String part : value.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
