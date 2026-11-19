package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One column on a monday.com board, as discovered at run time.
 *
 * <p>Nothing about a column is hardcoded: the id, the title and the type all come from the board
 * itself, and for status/dropdown columns the permitted labels are parsed out of {@code settings_str}
 * so a value can be validated before it is written.
 */
public final class ColumnDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnDefinition.class);

    /** The synthetic column id monday uses for an item's own name. */
    public static final String NAME_COLUMN_ID = "name";

    private final String id;
    private final String title;
    private final String type;
    private final String description;
    private final boolean archived;
    /** Ordered label index -> label text, empty for column types that have no label catalogue. */
    private final Map<String, String> labels;

    public ColumnDefinition(String id, String title, String type, String description,
                            boolean archived, Map<String, String> labels) {
        this.id = id;
        this.title = title;
        this.type = type == null ? "" : type;
        this.description = description;
        this.archived = archived;
        this.labels = labels == null ? Map.of() : new LinkedHashMap<>(labels);
    }

    /** Builds a definition from a monday {@code Column} JSON node. */
    public static ColumnDefinition fromJson(JsonObject column) {
        String id = stringOrNull(column, "id");
        String title = stringOrNull(column, "title");
        String type = stringOrNull(column, "type");
        String description = stringOrNull(column, "description");
        boolean archived = column.has("archived") && !column.get("archived").isJsonNull()
                && column.get("archived").getAsBoolean();
        Map<String, String> labels = parseLabels(stringOrNull(column, "settings_str"), id);
        return new ColumnDefinition(id, title, type, description, archived, labels);
    }

    /**
     * Pulls the label catalogue out of a status/dropdown column's {@code settings_str}.
     *
     * <p>Status columns carry {@code {"labels":{"0":"Working on it","1":"Done"}}}; dropdown columns
     * carry {@code {"labels":[{"id":1,"name":"A"}]}}. A column type with no labels (text, date,
     * people, …) yields an empty map rather than an error.
     */
    static Map<String, String> parseLabels(String settingsStr, String columnId) {
        Map<String, String> result = new LinkedHashMap<>();
        if (settingsStr == null || settingsStr.isBlank()) {
            return result;
        }
        try {
            JsonElement parsed = JsonParser.parseString(settingsStr);
            if (!parsed.isJsonObject()) {
                return result;
            }
            JsonObject settings = parsed.getAsJsonObject();
            if (!settings.has("labels") || settings.get("labels").isJsonNull()) {
                return result;
            }
            JsonElement labelsNode = settings.get("labels");
            if (labelsNode.isJsonObject()) {
                // Status column: index -> label text.
                for (Map.Entry<String, JsonElement> entry : labelsNode.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonNull()) {
                        String text = entry.getValue().getAsString();
                        if (!text.isBlank()) {
                            result.put(entry.getKey(), text);
                        }
                    }
                }
            } else if (labelsNode.isJsonArray()) {
                // Dropdown column: [{id, name}].
                for (JsonElement element : labelsNode.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject label = element.getAsJsonObject();
                    String labelId = label.has("id") && !label.get("id").isJsonNull()
                            ? label.get("id").getAsString() : "";
                    String labelName = label.has("name") && !label.get("name").isJsonNull()
                            ? label.get("name").getAsString() : "";
                    if (!labelName.isBlank()) {
                        result.put(labelId, labelName);
                    }
                }
            }
        } catch (RuntimeException e) {
            // A settings blob we cannot read must not break discovery for the whole board.
            LOG.warn("Could not parse settings for column '{}'; continuing with no label catalogue", columnId);
        }
        return result;
    }

    /** Case-insensitive lookup of a label; returns the canonical spelling or null. */
    public String canonicalLabel(String candidate) {
        if (candidate == null) {
            return null;
        }
        String needle = candidate.trim();
        for (String label : labels.values()) {
            if (label.equalsIgnoreCase(needle)) {
                return label;
            }
        }
        return null;
    }

    /** The label index for a label text, or null when unknown. */
    public String labelIndex(String labelText) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(labelText)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public List<String> labelTexts() {
        return new ArrayList<>(labels.values());
    }

    public boolean hasLabels() {
        return !labels.isEmpty();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getLowerType() {
        return type.toLowerCase(Locale.ROOT);
    }

    public String getDescription() {
        return description;
    }

    public boolean isArchived() {
        return archived;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    private static String stringOrNull(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : null;
    }

    @Override
    public String toString() {
        return "ColumnDefinition{id=" + id + ", title=" + title + ", type=" + type + '}';
    }
}
