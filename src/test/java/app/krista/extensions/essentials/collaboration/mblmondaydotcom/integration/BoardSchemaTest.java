package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dynamic board/column discovery — the behaviour requirement §6 turns on. */
class BoardSchemaTest {

    /** A board laid out like the reference's test board: person / status / date4. */
    private static BoardSchema testBoard() {
        JsonObject board = JsonParser.parseString("""
                {
                  "id": "18440687408",
                  "name": "Test Board",
                  "groups": [{"id": "topics", "title": "Group Title"}],
                  "columns": [
                    {"id": "name", "title": "Name", "type": "name", "archived": false},
                    {"id": "person", "title": "Person", "type": "people", "archived": false},
                    {"id": "status", "title": "Status", "type": "status", "archived": false,
                     "settings_str": "{\\"labels\\":{\\"0\\":\\"Working on it\\",\\"1\\":\\"Done\\",\\"2\\":\\"Stuck\\"}}"},
                    {"id": "date4", "title": "Date", "type": "date", "archived": false}
                  ]
                }
                """).getAsJsonObject();
        return BoardSchema.fromJson(board);
    }

    /** A deliberately differently-shaped board — nothing may be hardcoded to the one above. */
    private static BoardSchema engineeringBoard() {
        JsonObject board = JsonParser.parseString("""
                {
                  "id": "99",
                  "name": "Engineering",
                  "groups": [{"id": "sprint1", "title": "Sprint 1"}],
                  "columns": [
                    {"id": "people9", "title": "Assigned To", "type": "people", "archived": false},
                    {"id": "status_1", "title": "Workflow Status", "type": "status", "archived": false,
                     "settings_str": "{\\"labels\\":{\\"0\\":\\"Open\\",\\"5\\":\\"Shipped\\"}}"},
                    {"id": "due", "title": "Due On", "type": "date", "archived": false},
                    {"id": "prio", "title": "Priority", "type": "status", "archived": false,
                     "settings_str": "{\\"labels\\":{\\"0\\":\\"Low\\",\\"1\\":\\"High\\"}}"},
                    {"id": "notes", "title": "Description", "type": "long_text", "archived": false}
                  ]
                }
                """).getAsJsonObject();
        return BoardSchema.fromJson(board);
    }

    @Test
    void resolvesOwnerByColumnTypeNotTitle() {
        assertEquals("person", testBoard().resolve("Owner").orElseThrow().getId());
        // Different board, different id and a title that says nothing about "owner".
        assertEquals("people9", engineeringBoard().resolve("Owner").orElseThrow().getId());
    }

    @Test
    void resolvesStatusDueDatePriorityAndDescriptionOnADifferentlyShapedBoard() {
        BoardSchema schema = engineeringBoard();
        assertEquals("status_1", schema.resolve("Status").orElseThrow().getId());
        assertEquals("due", schema.resolve("Due Date").orElseThrow().getId());
        assertEquals("prio", schema.resolve("Priority").orElseThrow().getId());
        assertEquals("notes", schema.resolve("Description").orElseThrow().getId());
    }

    @Test
    void resolvesDueDateOnABoardWhoseDateColumnIsDate4() {
        assertEquals("date4", testBoard().resolve("Due Date").orElseThrow().getId());
    }

    @Test
    void anExactColumnIdWinsOverEverythingElse() {
        assertEquals("date4", testBoard().resolve("date4").orElseThrow().getId());
    }

    @Test
    void anExactColumnTitleIsMatchedCaseInsensitively() {
        assertEquals("people9", engineeringBoard().resolve("assigned to").orElseThrow().getId());
    }

    @Test
    void acceptsConversationalSynonymsForTheSameField() {
        BoardSchema schema = engineeringBoard();
        for (String synonym : new String[]{"owner", "owners", "assignee", "assigned to", "person"}) {
            assertEquals("people9", schema.resolve(synonym).orElseThrow().getId(),
                    "synonym '" + synonym + "' should resolve to the people column");
        }
        for (String synonym : new String[]{"due date", "due", "deadline"}) {
            assertEquals("due", schema.resolve(synonym).orElseThrow().getId(),
                    "synonym '" + synonym + "' should resolve to the date column");
        }
    }

    @Test
    void returnsEmptyForAColumnTheBoardDoesNotHave() {
        Optional<ColumnDefinition> resolved = testBoard().resolve("Sprint Points");
        assertTrue(resolved.isEmpty());
    }

    @Test
    void priorityFallsBackToTheStatusColumnOnlyWhenNoPriorityColumnExists() {
        // The test board has no priority column at all, so Priority must not silently resolve to
        // "Status" and overwrite it.
        Optional<ColumnDefinition> priority = testBoard().resolve("Priority");
        assertTrue(priority.isEmpty(), "Priority should not resolve on a board with no priority column");
    }

    @Test
    void recognisesTheItemNameAsSomethingOtherThanABoardColumn() {
        assertTrue(BoardSchema.isNameReference("Item Name"));
        assertTrue(BoardSchema.isNameReference("title"));
        assertFalse(BoardSchema.isNameReference("Status"));
    }

    @Test
    void exposesGroupsForValidation() {
        BoardSchema schema = testBoard();
        assertTrue(schema.hasGroup("topics"));
        assertFalse(schema.hasGroup("no_such_group"));
    }

    @Test
    void listsColumnTitlesForDidYouMeanMessages() {
        assertTrue(engineeringBoard().columnTitles().contains("Workflow Status"));
    }

    @Test
    void buildsASchemaFromAnItemsEmbeddedColumnNodes() {
        // Item queries already select column { id title type }, so no extra round-trip is needed.
        com.google.gson.JsonArray columnValues = JsonParser.parseString("""
                [
                  {"id": "status", "type": "status",
                   "column": {"id": "status", "title": "Status", "type": "status"}},
                  {"id": "person", "type": "people",
                   "column": {"id": "person", "title": "Person", "type": "people"}}
                ]
                """).getAsJsonArray();
        BoardSchema schema = BoardSchema.fromColumnValues(columnValues, "1", "Board");
        assertEquals("status", schema.resolve("Status").orElseThrow().getId());
        assertEquals("person", schema.resolve("Owner").orElseThrow().getId());
    }
}
