package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Converting user-supplied values into monday's per-column-type JSON shapes. */
class ColumnValueConverterTest {

    private static final String OP = "CreateItems";

    private static final ColumnValueConverter.OwnerResolver RESOLVER =
            reference -> reference.contains("Amit") ? List.of("122060799") : List.of();

    private static ColumnDefinition column(String id, String title, String type, Map<String, String> labels) {
        return new ColumnDefinition(id, title, type, null, false,
                labels == null ? Map.of() : new LinkedHashMap<>(labels));
    }

    @Test
    void peopleColumnBecomesPersonsAndTeams() {
        JsonElement value = ColumnValueConverter.convert(
                column("person", "Person", "people", null), "Amit", RESOLVER, OP);
        assertEquals("{\"personsAndTeams\":[{\"id\":122060799,\"kind\":\"person\"}]}", value.toString());
    }

    @Test
    void multipleOwnersAllAppearInPersonsAndTeams() {
        ColumnValueConverter.OwnerResolver two = reference -> List.of("1", "2");
        JsonElement value = ColumnValueConverter.convert(
                column("person", "Person", "people", null), "Amit, John", two, OP);
        assertEquals("{\"personsAndTeams\":[{\"id\":1,\"kind\":\"person\"},{\"id\":2,\"kind\":\"person\"}]}",
                value.toString());
    }

    @Test
    void anUnresolvableOwnerIsRejectedBeforeTheApiCall() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                ColumnValueConverter.convert(column("person", "Person", "people", null),
                        "Nobody At All", RESOLVER, OP));
        assertEquals("MONDAY_UNKNOWN_OWNER", e.getErrorCode());
        assertTrue(e.getSuggestedAction().contains("Search Users"));
    }

    @Test
    void statusColumnBecomesALabelObjectUsingTheBoardsOwnSpelling() {
        JsonElement value = ColumnValueConverter.convert(
                column("status", "Status", "status", Map.of("0", "Working on it", "1", "Done")),
                "done", RESOLVER, OP);
        assertEquals("{\"label\":\"Done\"}", value.toString());
    }

    @Test
    void anUnknownStatusLabelIsRejectedAndTheValidOnesAreListed() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                ColumnValueConverter.convert(
                        column("status", "Status", "status", Map.of("0", "Working on it", "1", "Done")),
                        "Finished", RESOLVER, OP));
        assertEquals("MONDAY_INVALID_LABEL", e.getErrorCode());
        assertTrue(e.getSuggestedAction().contains("Done"));
        assertEquals(MondayApiException.Kind.VALIDATION, e.getKind());
    }

    @Test
    void aStatusColumnWithNoDiscoverableLabelsStillAcceptsTheCallersText() {
        JsonElement value = ColumnValueConverter.convert(
                column("status", "Status", "status", null), "Anything", RESOLVER, OP);
        assertEquals("{\"label\":\"Anything\"}", value.toString());
    }

    @Test
    void dateColumnBecomesAnIsoDateObject() {
        JsonElement value = ColumnValueConverter.convert(
                column("date4", "Date", "date", null), "2026-11-25", RESOLVER, OP);
        assertEquals("{\"date\":\"2026-11-25\"}", value.toString());
    }

    @Test
    void dateColumnAcceptsAnIsoTimestampAndKeepsOnlyTheDate() {
        JsonElement value = ColumnValueConverter.convert(
                column("date4", "Date", "date", null), "2026-11-25T13:45:00Z", RESOLVER, OP);
        assertEquals("{\"date\":\"2026-11-25\"}", value.toString());
    }

    @Test
    void anUnreadableDateIsRejected() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                ColumnValueConverter.convert(column("date4", "Date", "date", null),
                        "next tuesday", RESOLVER, OP));
        assertEquals("MONDAY_INVALID_DATE", e.getErrorCode());
    }

    @Test
    void epochMillisAreFormattedAsAnIsoDate() {
        // 1795564800000 == 2026-11-25T00:00:00Z
        assertEquals("2026-11-25", ColumnValueConverter.formatEpochMillis(1795564800000L));
    }

    @Test
    void dropdownColumnBecomesALabelsArray() {
        JsonElement value = ColumnValueConverter.convert(
                column("dropdown", "Tags", "dropdown", Map.of("1", "Alpha", "2", "Beta")),
                "Alpha, Beta", RESOLVER, OP);
        assertEquals("{\"labels\":[\"Alpha\",\"Beta\"]}", value.toString());
    }

    @Test
    void numberColumnRejectsNonNumericText() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                ColumnValueConverter.convert(column("numbers", "Points", "numbers", null),
                        "a lot", RESOLVER, OP));
        assertEquals("MONDAY_INVALID_NUMBER", e.getErrorCode());
    }

    @Test
    void textColumnPassesThroughVerbatim() {
        JsonElement value = ColumnValueConverter.convert(
                column("notes", "Description", "long_text", null), "Ship it", RESOLVER, OP);
        assertEquals("\"Ship it\"", value.toString());
    }

    @Test
    void checkboxColumnBecomesACheckedFlag() {
        assertEquals("{\"checked\":\"true\"}", ColumnValueConverter.convert(
                column("cb", "Done?", "checkbox", null), "yes", RESOLVER, OP).toString());
        assertEquals("{\"checked\":\"false\"}", ColumnValueConverter.convert(
                column("cb", "Done?", "checkbox", null), "no", RESOLVER, OP).toString());
    }

    @Test
    void anUnsupportedColumnTypeFailsClearlyRatherThanGuessingAShape() {
        MondayApiException e = assertThrows(MondayApiException.class, () ->
                ColumnValueConverter.convert(column("mirror1", "Mirror", "mirror", null),
                        "something", RESOLVER, OP));
        assertEquals("MONDAY_UNSUPPORTED_COLUMN_TYPE", e.getErrorCode());
        assertEquals(MondayApiException.Kind.UNSUPPORTED_COLUMN, e.getKind());
    }

    @Test
    void anUnsupportedColumnTypeStillAcceptsRawJsonFromTheCaller() {
        JsonElement value = ColumnValueConverter.convert(column("mirror1", "Mirror", "mirror", null),
                "{\"item_ids\":[1,2]}", RESOLVER, OP);
        assertEquals("{\"item_ids\":[1,2]}", value.toString());
    }

    @Test
    void splitListTrimsAndDropsBlanks() {
        assertEquals(List.of("a", "b", "c"), ColumnValueConverter.splitList(" a , b ;c ,, "));
        assertTrue(ColumnValueConverter.splitList(null).isEmpty());
    }
}
