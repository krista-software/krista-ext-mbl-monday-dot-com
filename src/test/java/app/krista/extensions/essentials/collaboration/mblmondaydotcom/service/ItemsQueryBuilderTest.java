package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Building monday's ItemsQuery rule set from the Get Items inputs. */
class ItemsQueryBuilderTest {

    private static BoardSchema schema() {
        JsonObject board = JsonParser.parseString("""
                {
                  "id": "18440687408", "name": "Test Board",
                  "columns": [
                    {"id": "person", "title": "Person", "type": "people", "archived": false},
                    {"id": "status", "title": "Status", "type": "status", "archived": false,
                     "settings_str": "{\\"labels\\":{\\"0\\":\\"Working on it\\",\\"1\\":\\"Done\\"}}"},
                    {"id": "date4", "title": "Date", "type": "date", "archived": false}
                  ]
                }
                """).getAsJsonObject();
        return BoardSchema.fromJson(board);
    }

    private static JsonArray rulesOf(JsonObject queryParams) {
        return queryParams.getAsJsonArray("rules");
    }

    @Test
    void noInputsProduceNoRulesSoTheBoardIsListedUnfiltered() {
        ItemsQueryBuilder builder = new ItemsQueryBuilder();
        assertTrue(builder.isEmpty());
        assertTrue(builder.build().entrySet().isEmpty());
    }

    @Test
    void anItemNameBecomesAContainsTextRuleOnTheNameColumn() {
        JsonObject params = new ItemsQueryBuilder().withNameContains("Customer Onboarding").build();
        JsonArray rules = rulesOf(params);
        assertEquals(1, rules.size());
        JsonObject rule = rules.get(0).getAsJsonObject();
        assertEquals("name", rule.get("column_id").getAsString());
        assertEquals("contains_text", rule.get("operator").getAsString());
        assertEquals("Customer Onboarding", rule.getAsJsonArray("compare_value").get(0).getAsString());
    }

    @Test
    void aKnownStatusLabelIsComparedByItsNumericIndex() {
        JsonObject params = new ItemsQueryBuilder().withStatus(schema(), "done").build();
        JsonObject rule = rulesOf(params).get(0).getAsJsonObject();
        assertEquals("status", rule.get("column_id").getAsString());
        assertEquals("any_of", rule.get("operator").getAsString());
        // "Done" is index 1 on this board.
        assertEquals("1", rule.getAsJsonArray("compare_value").get(0).getAsString());
    }

    @Test
    void anUnknownStatusLabelFallsBackToComparingTheRawText() {
        JsonObject params = new ItemsQueryBuilder().withStatus(schema(), "Shipped").build();
        JsonObject rule = rulesOf(params).get(0).getAsJsonObject();
        assertEquals("Shipped", rule.getAsJsonArray("compare_value").get(0).getAsString());
    }

    @Test
    void ownersAreComparedByResolvedPersonId() {
        JsonObject params = new ItemsQueryBuilder()
                .withOwnerIds(schema(), List.of("122060799")).build();
        JsonObject rule = rulesOf(params).get(0).getAsJsonObject();
        assertEquals("person", rule.get("column_id").getAsString());
        assertEquals("122060799", rule.getAsJsonArray("compare_value").get(0).getAsString());
    }

    @Test
    void anExactDueDateBecomesAnAnyOfRule() {
        JsonObject params = new ItemsQueryBuilder().withDueDate(schema(), "2026-11-27").build();
        JsonObject rule = rulesOf(params).get(0).getAsJsonObject();
        assertEquals("date4", rule.get("column_id").getAsString());
        assertEquals("any_of", rule.get("operator").getAsString());
    }

    @Test
    void aFullDateRangeBecomesABetweenRule() {
        JsonObject params = new ItemsQueryBuilder()
                .withDueDateRange(schema(), "2026-11-01", "2026-11-30").build();
        JsonObject rule = rulesOf(params).get(0).getAsJsonObject();
        assertEquals("between", rule.get("operator").getAsString());
        assertEquals(2, rule.getAsJsonArray("compare_value").size());
    }

    @Test
    void anOpenEndedRangeBecomesAComparisonRule() {
        JsonObject fromOnly = new ItemsQueryBuilder()
                .withDueDateRange(schema(), "2026-11-01", null).build();
        assertEquals("greater_than_or_equals",
                rulesOf(fromOnly).get(0).getAsJsonObject().get("operator").getAsString());

        JsonObject toOnly = new ItemsQueryBuilder()
                .withDueDateRange(schema(), null, "2026-11-30").build();
        assertEquals("lower_than_or_equal",
                rulesOf(toOnly).get(0).getAsJsonObject().get("operator").getAsString());
    }

    @Test
    void severalFiltersAreCombinedWithAnd() {
        JsonObject params = new ItemsQueryBuilder()
                .withNameContains("Release")
                .withStatus(schema(), "Done")
                .withDueDate(schema(), "2026-11-27")
                .build();
        assertEquals(3, rulesOf(params).size());
        assertEquals("and", params.get("operator").getAsString());
    }

    @Test
    void aFilterForAColumnTheBoardDoesNotHaveIsSkippedRatherThanFailing() {
        // A board with no people column simply cannot filter by owner.
        JsonObject board = JsonParser.parseString(
                "{\"id\":\"1\",\"name\":\"Minimal\",\"columns\":[]}").getAsJsonObject();
        ItemsQueryBuilder builder = new ItemsQueryBuilder()
                .withOwnerIds(BoardSchema.fromJson(board), List.of("1"))
                .withStatus(BoardSchema.fromJson(board), "Done");
        assertTrue(builder.isEmpty());
    }

    @Test
    void blankInputsAddNoRules() {
        ItemsQueryBuilder builder = new ItemsQueryBuilder()
                .withNameContains(null)
                .withNameContains("  ")
                .withStatus(schema(), null)
                .withDueDate(schema(), null);
        assertTrue(builder.isEmpty());
        assertFalse(builder.ruleCount() > 0);
    }

    @Test
    void convertsKristaEpochMillisInputsToIsoDates() {
        assertEquals("2026-11-25", ItemsQueryBuilder.toIsoDate(1795564800000L));
        assertEquals(null, ItemsQueryBuilder.toIsoDate(null));
    }
}
