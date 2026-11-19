package app.krista.extensions.essentials.collaboration.mblmondaydotcom.transform;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardColumn;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardDetails;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.ItemUpdate;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.MondayUser;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mapping monday.com GraphQL payloads onto the typed entities. */
class MondayTransformerTest {

    private static JsonObject itemJson() {
        return JsonParser.parseString("""
                {
                  "id": "13590022086",
                  "name": "Customer Onboarding",
                  "state": "active",
                  "url": "https://mblpartners.monday.com/boards/18440687408/pulses/13590022086",
                  "created_at": "2026-11-01T09:15:00Z",
                  "updated_at": "2026-11-19T12:30:45Z",
                  "board": {"id": "18440687408", "name": "Test Board"},
                  "group": {"id": "topics", "title": "Group Title"},
                  "column_values": [
                    {"id": "person", "type": "people", "text": "Amit Shah",
                     "value": "{\\"personsAndTeams\\":[{\\"id\\":122060799,\\"kind\\":\\"person\\"}]}",
                     "column": {"id": "person", "title": "Person", "type": "people"},
                     "persons_and_teams": [{"id": "122060799", "kind": "person"}]},
                    {"id": "status", "type": "status", "text": "Done", "label": "Done",
                     "index": 1, "is_done": true,
                     "column": {"id": "status", "title": "Status", "type": "status"}},
                    {"id": "date4", "type": "date", "text": "2026-11-27", "date": "2026-11-27",
                     "column": {"id": "date4", "title": "Date", "type": "date"}}
                  ]
                }
                """).getAsJsonObject();
    }

    @Test
    void mapsTheCoreItemFields() {
        BoardItem item = MondayTransformer.toBoardItem(itemJson());
        assertEquals("13590022086", item.itemId);
        assertEquals("Customer Onboarding", item.name);
        assertEquals("18440687408", item.boardId);
        assertEquals("Test Board", item.boardName);
        assertEquals("topics", item.groupId);
        assertEquals("Group Title", item.groupName);
        assertEquals("active", item.state);
        assertTrue(item.itemUrl.endsWith("/pulses/13590022086"));
        assertNotNull(item.createdAt);
        assertNotNull(item.updatedAt);
    }

    @Test
    void namesTheStatusOwnerAndDueDateFieldsFromTheBoardsOwnColumns() {
        BoardItem item = MondayTransformer.toBoardItem(itemJson());
        assertEquals("Done", item.status);
        assertEquals(1.0d, item.statusIndex.doubleValue());
        assertEquals(Boolean.TRUE, item.isDone);
        assertEquals("Amit Shah", item.ownerNames);
        assertEquals(1, item.owners.size());
        assertEquals("122060799", item.owners.get(0).get("Id"));
        assertEquals("Amit Shah", item.owners.get(0).get("Name"));
        assertEquals("person", item.owners.get(0).get("Kind"));
        assertEquals(MondayTransformer.toEpochMillis("2026-11-27"), item.dueDate);
    }

    @Test
    void everyColumnIsAlsoPresentInColumnValuesSoCustomColumnsSurvive() {
        BoardItem item = MondayTransformer.toBoardItem(itemJson());
        assertEquals(3, item.columnValues.size());
        Map<String, Object> statusRow = item.columnValues.stream()
                .filter(row -> "status".equals(row.get("Id"))).findFirst().orElseThrow();
        assertEquals("Status", statusRow.get("Title"));
        assertEquals("status", statusRow.get("Type"));
        assertEquals("Done", statusRow.get("Text"));
    }

    @Test
    void anItemWithNoColumnValuesStillMaps() {
        JsonObject bare = JsonParser.parseString(
                "{\"id\":\"1\",\"name\":\"Bare\",\"board\":{\"id\":\"9\"}}").getAsJsonObject();
        BoardItem item = MondayTransformer.toBoardItem(bare);
        assertEquals("1", item.itemId);
        assertNull(item.status);
        assertTrue(item.columnValues.isEmpty());
    }

    @Test
    void parsesBothTheDateAndTimestampShapesMondayReturns() {
        assertNotNull(MondayTransformer.toEpochMillis("2026-11-27"));
        assertNotNull(MondayTransformer.toEpochMillis("2026-11-19T12:30:45Z"));
        assertNotNull(MondayTransformer.toEpochMillis("2026-11-19T12:30:45+02:00"));
        assertEquals(1795564800000L, MondayTransformer.toEpochMillis("2026-11-25").longValue());
    }

    @Test
    void anUnparseableDateYieldsNullRatherThanFailingTheWholeRead() {
        assertNull(MondayTransformer.toEpochMillis("not a date"));
        assertNull(MondayTransformer.toEpochMillis(""));
        assertNull(MondayTransformer.toEpochMillis(null));
    }

    @Test
    void mapsBoardDetailsIncludingGroupsAndColumns() {
        JsonObject board = JsonParser.parseString("""
                {
                  "id": "18440687408", "name": "Test Board", "state": "active",
                  "board_kind": "public", "url": "https://x.monday.com/boards/18440687408",
                  "workspace": {"id": "7", "name": "Main"},
                  "groups": [{"id": "topics", "title": "Group Title"}],
                  "columns": [{"id": "status", "title": "Status", "type": "status"}]
                }
                """).getAsJsonObject();
        BoardDetails details = MondayTransformer.toBoardDetails(board);
        assertEquals("18440687408", details.boardId);
        assertEquals("Main", details.workspaceName);
        assertEquals(1, details.groups.size());
        assertEquals("Group Title", details.groups.get(0).get("Title"));
        assertEquals("status", details.columns.get(0).get("Id"));
    }

    @Test
    void mapsAColumnDefinitionWithItsLabelCatalogueAndFriendlyName() {
        ColumnDefinition column = new ColumnDefinition("status", "Status", "status", null, false,
                new LinkedHashMap<>(Map.of("0", "Working on it", "1", "Done")));
        BoardColumn entity = MondayTransformer.toBoardColumn(column, "18440687408", "Status");
        assertEquals("status", entity.columnId);
        assertEquals("Status", entity.friendlyName);
        assertEquals(2, entity.labels.size());
    }

    @Test
    void mapsAnItemUpdateWithItsCreator() {
        JsonObject update = JsonParser.parseString("""
                {"id": "55", "body": "<p>Done</p>", "text_body": "Done",
                 "created_at": "2026-11-19T12:30:45Z",
                 "creator": {"id": "122060799", "name": "Amit Shah"}}
                """).getAsJsonObject();
        ItemUpdate entity = MondayTransformer.toItemUpdate(update, "13590022086");
        assertEquals("55", entity.updateId);
        assertEquals("13590022086", entity.itemId);
        assertEquals("<p>Done</p>", entity.body);
        assertEquals("Done", entity.textBody);
        assertEquals("Amit Shah", entity.creatorName);
        assertNotNull(entity.createdAt);
    }

    @Test
    void mapsAUserIncludingTheAccountName() {
        JsonObject user = JsonParser.parseString("""
                {"id": "122060799", "name": "Amit Shah", "email": "amit@example.com",
                 "is_admin": false, "is_guest": false, "enabled": true,
                 "account": {"id": "1", "name": "mblpartners"}}
                """).getAsJsonObject();
        MondayUser entity = MondayTransformer.toUser(user);
        assertEquals("122060799", entity.userId);
        assertEquals("amit@example.com", entity.email);
        assertEquals("mblpartners", entity.accountName);
    }

    @Test
    void attachesUpdateRowsToAnItem() {
        BoardItem item = MondayTransformer.toBoardItem(itemJson());
        JsonArray updates = JsonParser.parseString("""
                [{"id": "55", "text_body": "Looks good", "created_at": "2026-11-19T12:30:45Z",
                  "creator": {"id": "1", "name": "Amit Shah"}}]
                """).getAsJsonArray();
        MondayTransformer.attachUpdates(item, updates);
        assertEquals(1, item.updates.size());
        assertEquals("Looks good", item.updates.get(0).get("Body"));
        assertEquals("Amit Shah", item.updates.get(0).get("Creator"));
    }

    @Test
    void toFieldsUsesTheDisplayNamesTheEntityDeclares() {
        Map<String, Object> fields = MondayTransformer.toBoardItem(itemJson()).toFields();
        assertTrue(fields.containsKey("Item Id"));
        assertTrue(fields.containsKey("Owner Names"));
        assertTrue(fields.containsKey("Column Values"));
        assertEquals("Customer Onboarding", fields.get("Name"));
    }

    @Test
    void ownerRowsHandleAPeopleColumnWithNoAssignees() {
        JsonObject empty = JsonParser.parseString(
                "{\"id\":\"person\",\"type\":\"people\",\"text\":\"\",\"persons_and_teams\":[]}")
                .getAsJsonObject();
        List<Map<String, Object>> rows = MondayTransformer.toOwnerRows(empty);
        assertTrue(rows.isEmpty());
    }
}
