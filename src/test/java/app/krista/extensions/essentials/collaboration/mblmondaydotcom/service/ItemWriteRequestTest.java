package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnValueConverter;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The partial-update contract from requirement §5: a field is written only when the caller actually
 * supplied it, and clearing is always explicit.
 */
class ItemWriteRequestTest {

    private static final String OP = "UpdateItem";

    private static final ColumnValueConverter.OwnerResolver RESOLVER = reference -> List.of("122060799");

    private static BoardSchema schema() {
        JsonObject board = JsonParser.parseString("""
                {
                  "id": "18440687408",
                  "name": "Test Board",
                  "groups": [{"id": "topics", "title": "Group Title"}],
                  "columns": [
                    {"id": "person", "title": "Person", "type": "people", "archived": false},
                    {"id": "status", "title": "Status", "type": "status", "archived": false,
                     "settings_str": "{\\"labels\\":{\\"0\\":\\"Working on it\\",\\"1\\":\\"Done\\"}}"},
                    {"id": "date4", "title": "Date", "type": "date", "archived": false},
                    {"id": "notes", "title": "Description", "type": "long_text", "archived": false}
                  ]
                }
                """).getAsJsonObject();
        return BoardSchema.fromJson(board);
    }

    private static ItemWriteRequest write() {
        return new ItemWriteRequest(schema(), OP, RESOLVER);
    }

    /** "Change the status of item 12345 to Done" must touch the status column and nothing else. */
    @Test
    void onlyTheSuppliedFieldIsWritten() {
        ItemWriteRequest request = write()
                .set(BoardSchema.FRIENDLY_STATUS, "Done")
                .set(BoardSchema.FRIENDLY_OWNER, null)
                .set(BoardSchema.FRIENDLY_DUE_DATE, null)
                .set(BoardSchema.FRIENDLY_DESCRIPTION, null);

        JsonObject payload = JsonParser.parseString(request.toColumnValuesJson(true)).getAsJsonObject();
        assertEquals(1, payload.entrySet().size());
        assertTrue(payload.has("status"));
        assertFalse(payload.has("person"), "an omitted owner must not appear in the payload at all");
        assertFalse(payload.has("date4"));
        assertFalse(payload.has("name"));
    }

    @Test
    void anOmittedFieldIsNeverSentAsNullOrBlank() {
        ItemWriteRequest request = write()
                .set(BoardSchema.FRIENDLY_STATUS, "Done")
                .set(BoardSchema.FRIENDLY_DESCRIPTION, "   ");
        JsonObject payload = JsonParser.parseString(request.toColumnValuesJson(false)).getAsJsonObject();
        assertFalse(payload.has("notes"), "a blank value is an omission, not a clear");
    }

    @Test
    void severalFieldsBecomeOneMultiColumnPayload() {
        ItemWriteRequest request = write()
                .set(BoardSchema.FRIENDLY_STATUS, "Done")
                .set(BoardSchema.FRIENDLY_DUE_DATE, "2026-11-27")
                .set(BoardSchema.FRIENDLY_OWNER, "Amit");

        JsonObject payload = JsonParser.parseString(request.toColumnValuesJson(true)).getAsJsonObject();
        assertEquals(3, payload.entrySet().size());
        assertEquals("{\"label\":\"Done\"}", payload.get("status").toString());
        assertEquals("{\"date\":\"2026-11-27\"}", payload.get("date4").toString());
        assertEquals("{\"personsAndTeams\":[{\"id\":122060799,\"kind\":\"person\"}]}",
                payload.get("person").toString());
    }

    @Test
    void renamingGoesThroughTheNameColumnAndLeavesOtherColumnsAlone() {
        ItemWriteRequest request = write().set(BoardSchema.FRIENDLY_NAME, "Release Testing");
        JsonObject payload = JsonParser.parseString(request.toColumnValuesJson(true)).getAsJsonObject();
        assertEquals(1, payload.entrySet().size());
        assertEquals("\"Release Testing\"", payload.get("name").toString());
        assertEquals("Release Testing", request.getNewName());
    }

    @Test
    void aClearedFieldIsCollectedSeparatelyFromTheWrittenOnes() {
        ItemWriteRequest request = write()
                .set(BoardSchema.FRIENDLY_STATUS, "Done")
                .clear(BoardSchema.FRIENDLY_DUE_DATE);

        JsonObject payload = JsonParser.parseString(request.toColumnValuesJson(true)).getAsJsonObject();
        assertFalse(payload.has("date4"), "a clear must not ride along in column_values");
        assertEquals(List.of("date4"), List.copyOf(request.getClearColumnIds()));
        assertTrue(request.getUpdatedFields().contains("Date (cleared)"));
    }

    @Test
    void supplyingAFieldAsBothAValueAndAClearIsRejected() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                write().set(BoardSchema.FRIENDLY_STATUS, "Done").clear("Status"));
        assertEquals("MONDAY_CONTRADICTORY_UPDATE", e.getErrorCode());
    }

    @Test
    void theItemNameCannotBeCleared() {
        MondayApiException e = assertThrows(MondayApiException.class, () -> write().clear("Item Name"));
        assertEquals("MONDAY_CANNOT_CLEAR_NAME", e.getErrorCode());
    }

    @Test
    void anUnknownColumnIsRejectedWithTheAvailableTitlesListed() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                write().set("Sprint Points", "13"));
        assertEquals("MONDAY_UNKNOWN_COLUMN", e.getErrorCode());
        assertTrue(e.getSuggestedAction().contains("Status"));
        assertTrue(e.getSuggestedAction().contains("Get Board Columns"));
    }

    @Test
    void additionalColumnValuesAcceptEitherAColumnTitleOrAColumnId() {
        ItemWriteRequest byTitle = write().setAll(List.of(Map.of("Column", "Description", "Value", "Ship it")));
        assertTrue(JsonParser.parseString(byTitle.toColumnValuesJson(false))
                .getAsJsonObject().has("notes"));

        ItemWriteRequest byId = write().setAll(List.of(Map.of("Column", "notes", "Value", "Ship it")));
        assertTrue(JsonParser.parseString(byId.toColumnValuesJson(false))
                .getAsJsonObject().has("notes"));
    }

    @Test
    void anEmptyRequestIsRecognisedSoNoPointlessWriteHappens() {
        assertTrue(write().isEmpty());
        assertFalse(write().set(BoardSchema.FRIENDLY_STATUS, "Done").isEmpty());
    }

    @Test
    void updatedFieldsReportsTheHumanReadableColumnTitles() {
        ItemWriteRequest request = write()
                .set(BoardSchema.FRIENDLY_STATUS, "Done")
                .set(BoardSchema.FRIENDLY_OWNER, "Amit");
        assertEquals(List.of("Status", "Person"), request.getUpdatedFields());
    }
}
