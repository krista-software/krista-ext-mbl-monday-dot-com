package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing a column's label catalogue out of settings_str. */
class ColumnDefinitionTest {

    @Test
    void parsesStatusLabelsKeyedByIndex() {
        Map<String, String> labels = ColumnDefinition.parseLabels(
                "{\"labels\":{\"0\":\"Working on it\",\"1\":\"Done\",\"2\":\"Stuck\"}}", "status");
        assertEquals(3, labels.size());
        assertEquals("Done", labels.get("1"));
    }

    @Test
    void parsesDropdownLabelsFromAnArray() {
        Map<String, String> labels = ColumnDefinition.parseLabels(
                "{\"labels\":[{\"id\":1,\"name\":\"Alpha\"},{\"id\":2,\"name\":\"Beta\"}]}", "dropdown");
        assertEquals(2, labels.size());
        assertTrue(labels.containsValue("Alpha"));
        assertTrue(labels.containsValue("Beta"));
    }

    @Test
    void aColumnWithNoLabelCatalogueYieldsAnEmptyMapRatherThanFailing() {
        assertTrue(ColumnDefinition.parseLabels(null, "date4").isEmpty());
        assertTrue(ColumnDefinition.parseLabels("", "date4").isEmpty());
        assertTrue(ColumnDefinition.parseLabels("{\"width\":120}", "text").isEmpty());
    }

    @Test
    void malformedSettingsDoNotBreakDiscoveryForTheWholeBoard() {
        assertTrue(ColumnDefinition.parseLabels("not json at all", "status").isEmpty());
        assertTrue(ColumnDefinition.parseLabels("[1,2,3]", "status").isEmpty());
    }

    @Test
    void blankLabelTextsAreSkipped() {
        Map<String, String> labels = ColumnDefinition.parseLabels(
                "{\"labels\":{\"0\":\"\",\"1\":\"Done\"}}", "status");
        assertEquals(1, labels.size());
        assertEquals("Done", labels.get("1"));
    }

    @Test
    void canonicalisesALabelCaseInsensitively() {
        ColumnDefinition column = statusColumn();
        assertEquals("Done", column.canonicalLabel("done"));
        assertEquals("Working on it", column.canonicalLabel("  WORKING ON IT "));
        assertNull(column.canonicalLabel("Finished"));
    }

    @Test
    void mapsALabelBackToItsIndex() {
        assertEquals("1", statusColumn().labelIndex("Done"));
        assertNull(statusColumn().labelIndex("Nope"));
    }

    @Test
    void reportsWhetherItHasALabelCatalogue() {
        assertTrue(statusColumn().hasLabels());
        assertFalse(new ColumnDefinition("date4", "Date", "date", null, false, Map.of()).hasLabels());
    }

    private static ColumnDefinition statusColumn() {
        return new ColumnDefinition("status", "Status", "status", null, false,
                new java.util.LinkedHashMap<>(Map.of("0", "Working on it", "1", "Done", "2", "Stuck")));
    }
}
