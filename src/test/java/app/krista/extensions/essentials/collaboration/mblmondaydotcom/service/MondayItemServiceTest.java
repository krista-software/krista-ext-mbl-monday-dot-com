package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Search messaging and the ambiguity payload — the parts that do not need a live API. */
class MondayItemServiceTest {

    @Test
    void anEmptyResultGetsAClearMessageRatherThanAnError() {
        assertEquals("No monday.com items matched the search criteria.",
                MondayItemService.describe(0, false));
    }

    @Test
    void aSingleMatchReadsNaturally() {
        assertEquals("Found 1 matching item.", MondayItemService.describe(1, false));
    }

    @Test
    void severalMatchesAreCounted() {
        assertEquals("Found 7 matching items.", MondayItemService.describe(7, false));
    }

    @Test
    void aTruncatedFanOutSaysSoAndSuggestsHowToNarrowIt() {
        String message = MondayItemService.describe(25, true);
        assertTrue(message.startsWith("Found 25 matching items."));
        assertTrue(message.contains("board scan cap"));
        assertTrue(message.contains("Board ID"));
        assertTrue(message.contains("Max Boards To Scan"));
    }

    @Test
    void anEmptyResultIsNeverReportedAsTruncated() {
        assertFalse(MondayItemService.describe(0, true).contains("board scan cap"));
    }

    @Test
    void ambiguousCandidatesCarryEnoughToChooseBetweenThem() {
        BoardItem first = new BoardItem();
        first.itemId = "1";
        first.name = "API Integration";
        first.boardName = "Engineering";
        first.groupName = "Sprint 1";
        first.status = "Working on it";
        first.itemUrl = "https://x.monday.com/boards/9/pulses/1";

        BoardItem second = new BoardItem();
        second.itemId = "2";
        second.name = "API Integration Review";
        second.boardName = "Sales";
        second.groupName = "Backlog";
        second.status = "Done";
        second.itemUrl = "https://x.monday.com/boards/8/pulses/2";

        List<Map<String, Object>> rows = MondayItemService.toCandidateRows(List.of(first, second));

        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("Id"));
        assertEquals("API Integration", rows.get(0).get("Name"));
        assertEquals("Engineering", rows.get(0).get("Board"));
        assertEquals("Sprint 1", rows.get(0).get("Group"));
        assertEquals("Working on it", rows.get(0).get("Status"));
        assertTrue(String.valueOf(rows.get(1).get("Url")).endsWith("/pulses/2"));
    }

    @Test
    void candidateRowsHandleAnItemWithSparseFields() {
        BoardItem bare = new BoardItem();
        bare.itemId = "3";
        List<Map<String, Object>> rows = MondayItemService.toCandidateRows(List.of(bare));
        assertEquals(1, rows.size());
        assertEquals("3", rows.get(0).get("Id"));
        assertTrue(rows.get(0).containsKey("Name"));
    }
}
