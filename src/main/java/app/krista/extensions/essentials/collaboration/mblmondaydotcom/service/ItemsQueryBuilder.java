package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnDefinition;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnValueConverter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * Builds monday's {@code ItemsQuery} rule set from the Get Items inputs.
 *
 * <p>Every rule targets a column discovered on the board being searched, so the same inputs work
 * against boards with different layouts.
 *
 * <p><b>Known uncertainty, carried from the API reference:</b> the reference states that
 * compare-value shapes for status and people columns (index / id rather than label text) are
 * <em>unverified</em>. This builder therefore prefers the numeric status index when the board's
 * label catalogue resolves the caller's text, and falls back to the raw text when it cannot — so a
 * board that does accept label text still works.
 */
public final class ItemsQueryBuilder {

    public static final String OPERATOR_CONTAINS_TEXT = "contains_text";
    public static final String OPERATOR_ANY_OF = "any_of";
    public static final String OPERATOR_BETWEEN = "between";

    private final JsonArray rules = new JsonArray();

    /** True when no rule was added — monday then returns the board's items unfiltered. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int ruleCount() {
        return rules.size();
    }

    /** Partial, case-insensitive match on the item's name. */
    public ItemsQueryBuilder withNameContains(String text) {
        if (isBlank(text)) {
            return this;
        }
        addRule(ColumnDefinition.NAME_COLUMN_ID, List.of(text.trim()), OPERATOR_CONTAINS_TEXT);
        return this;
    }

    /** Filters on the board's status column, by label index when the label is known. */
    public ItemsQueryBuilder withStatus(BoardSchema schema, String status) {
        if (isBlank(status)) {
            return this;
        }
        Optional<ColumnDefinition> column = schema.resolve(BoardSchema.FRIENDLY_STATUS);
        if (column.isEmpty()) {
            return this;
        }
        ColumnDefinition statusColumn = column.get();
        String canonical = statusColumn.canonicalLabel(status);
        String index = canonical == null ? null : statusColumn.labelIndex(canonical);
        String compareValue = index != null ? index : status.trim();
        addRule(statusColumn.getId(), List.of(compareValue), OPERATOR_ANY_OF);
        return this;
    }

    /** Filters on the board's people column by resolved person id. */
    public ItemsQueryBuilder withOwnerIds(BoardSchema schema, List<String> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return this;
        }
        Optional<ColumnDefinition> column = schema.resolve(BoardSchema.FRIENDLY_OWNER);
        if (column.isEmpty()) {
            return this;
        }
        addRule(column.get().getId(), personIds, OPERATOR_ANY_OF);
        return this;
    }

    /** Exact due date. */
    public ItemsQueryBuilder withDueDate(BoardSchema schema, String isoDate) {
        if (isBlank(isoDate)) {
            return this;
        }
        Optional<ColumnDefinition> column = schema.resolve(BoardSchema.FRIENDLY_DUE_DATE);
        if (column.isEmpty()) {
            return this;
        }
        addRule(column.get().getId(), List.of(isoDate), OPERATOR_ANY_OF);
        return this;
    }

    /** Inclusive due-date range. */
    public ItemsQueryBuilder withDueDateRange(BoardSchema schema, String fromIsoDate, String toIsoDate) {
        if (isBlank(fromIsoDate) && isBlank(toIsoDate)) {
            return this;
        }
        Optional<ColumnDefinition> column = schema.resolve(BoardSchema.FRIENDLY_DUE_DATE);
        if (column.isEmpty()) {
            return this;
        }
        if (!isBlank(fromIsoDate) && !isBlank(toIsoDate)) {
            addRule(column.get().getId(), List.of(fromIsoDate, toIsoDate), OPERATOR_BETWEEN);
        } else if (!isBlank(fromIsoDate)) {
            addRule(column.get().getId(), List.of(fromIsoDate), "greater_than_or_equals");
        } else {
            addRule(column.get().getId(), List.of(toIsoDate), "lower_than_or_equal");
        }
        return this;
    }

    /** Free-text search. monday exposes no all-column text search here, so this targets the name. */
    public ItemsQueryBuilder withSearchText(String text) {
        return withNameContains(text);
    }

    private void addRule(String columnId, List<String> compareValues, String operator) {
        if (columnId == null) {
            return;
        }
        JsonObject rule = new JsonObject();
        rule.addProperty("column_id", columnId);
        JsonArray values = new JsonArray();
        for (String value : compareValues) {
            values.add(value);
        }
        rule.add("compare_value", values);
        rule.addProperty("operator", operator);
        rules.add(rule);
    }

    /** The {@code ItemsQuery} object, or an empty object when no rule was added. */
    public JsonObject build() {
        JsonObject queryParams = new JsonObject();
        if (rules.isEmpty()) {
            return queryParams;
        }
        queryParams.add("rules", rules);
        queryParams.addProperty("operator", "and");
        return queryParams;
    }

    /** Converts a Krista epoch-millis date input to monday's {@code YYYY-MM-DD}. */
    public static String toIsoDate(Long epochMillis) {
        return epochMillis == null ? null : ColumnValueConverter.formatEpochMillis(epochMillis);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
