package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnDefinition;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnValueConverter;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Accumulates the column writes for a create or update, resolving every field reference against the
 * target board's real columns.
 *
 * <p>This is where requirement §5's three-state rule is enforced. A field is written only when the
 * caller actually supplied it:
 * <ul>
 *   <li><b>not supplied</b> — never appears in {@code column_values}, so monday leaves it alone;</li>
 *   <li><b>supplied</b> — converted to the column's typed shape and written;</li>
 *   <li><b>explicitly cleared</b> — collected separately and emptied via
 *       {@code change_simple_column_value} with a null value.</li>
 * </ul>
 * Nothing is ever defaulted to null or blank just because an optional input was omitted.
 */
public final class ItemWriteRequest {

    private final BoardSchema schema;
    private final String operation;
    private final ColumnValueConverter.OwnerResolver ownerResolver;

    private final JsonObject columnValues = new JsonObject();
    private final List<String> updatedFields = new ArrayList<>();
    private final Set<String> clearColumnIds = new LinkedHashSet<>();
    private final List<String> clearedFields = new ArrayList<>();
    private final Set<String> touchedReferences = new LinkedHashSet<>();

    private String newName;

    public ItemWriteRequest(BoardSchema schema, String operation,
                            ColumnValueConverter.OwnerResolver ownerResolver) {
        this.schema = schema;
        this.operation = operation;
        this.ownerResolver = ownerResolver;
    }

    /**
     * Adds a field the caller supplied. A blank value is ignored — clearing is explicit, via
     * {@link #clear(String)}.
     *
     * @param reference a friendly name ("Owner"), a column title ("Assigned To") or a column id.
     */
    public ItemWriteRequest set(String reference, String value) {
        if (reference == null || value == null || value.trim().isEmpty()) {
            return this;
        }
        touchedReferences.add(normalise(reference));

        if (BoardSchema.isNameReference(reference)) {
            newName = value.trim();
            updatedFields.add("Name");
            return this;
        }

        ColumnDefinition column = requireColumn(reference);
        JsonElement converted = ColumnValueConverter.convert(column, value, ownerResolver, operation);
        columnValues.add(column.getId(), converted);
        updatedFields.add(column.getTitle() == null ? column.getId() : column.getTitle());
        return this;
    }

    /** Marks a field for explicit clearing. */
    public ItemWriteRequest clear(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return this;
        }
        String normalised = normalise(reference);
        if (touchedReferences.contains(normalised)) {
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_CONTRADICTORY_UPDATE",
                    "'" + reference + "' was given both a new value and a request to clear it.",
                    operation, Map.of("field", reference),
                    "Supply the field either as a value or in Fields To Clear, not both.");
        }
        if (BoardSchema.isNameReference(reference)) {
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_CANNOT_CLEAR_NAME",
                    "An item's name cannot be cleared.",
                    operation, Map.of("field", reference),
                    "Supply a new name instead of clearing it.");
        }
        ColumnDefinition column = requireColumn(reference);
        clearColumnIds.add(column.getId());
        clearedFields.add(column.getTitle() == null ? column.getId() : column.getTitle());
        touchedReferences.add(normalised);
        return this;
    }

    /** Adds the caller's free-form column list, which may use column ids or titles. */
    public ItemWriteRequest setAll(List<Map<String, Object>> additionalColumnValues) {
        if (additionalColumnValues == null) {
            return this;
        }
        for (Map<String, Object> row : additionalColumnValues) {
            if (row == null) {
                continue;
            }
            Object column = firstPresent(row, "Column", "column", "Id", "id", "Key", "key");
            Object value = firstPresent(row, "Value", "value");
            if (column == null || value == null) {
                continue;
            }
            set(String.valueOf(column), String.valueOf(value));
        }
        return this;
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ColumnDefinition requireColumn(String reference) {
        Optional<ColumnDefinition> column = schema.resolve(reference);
        if (column.isEmpty()) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("field", reference);
            context.put("boardId", schema.getBoardId());
            context.put("availableColumns", String.join(", ", schema.columnTitles()));
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_UNKNOWN_COLUMN",
                    "Board '" + schema.getBoardName() + "' has no column matching '" + reference + "'.",
                    operation, context,
                    "Run Get Board Columns for this board and use one of: "
                            + String.join(", ", schema.columnTitles()) + ".");
        }
        return column.get();
    }

    private static String normalise(String reference) {
        return reference.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** True when nothing at all was supplied — the caller asked for no change. */
    public boolean isEmpty() {
        return columnValues.entrySet().isEmpty() && clearColumnIds.isEmpty() && newName == null;
    }

    public boolean hasColumnValues() {
        return !columnValues.entrySet().isEmpty();
    }

    /** The {@code column_values} JSON string monday expects, including a rename when requested. */
    public String toColumnValuesJson(boolean includeName) {
        JsonObject payload = columnValues.deepCopy();
        if (includeName && newName != null) {
            payload.addProperty(ColumnDefinition.NAME_COLUMN_ID, newName);
        }
        return payload.toString();
    }

    public JsonObject getColumnValues() {
        return columnValues;
    }

    public String getNewName() {
        return newName;
    }

    public Set<String> getClearColumnIds() {
        return clearColumnIds;
    }

    /** Every field actually written or cleared, for the Updated Fields output. */
    public List<String> getUpdatedFields() {
        List<String> all = new ArrayList<>(updatedFields);
        for (String cleared : clearedFields) {
            all.add(cleared + " (cleared)");
        }
        return all;
    }
}
