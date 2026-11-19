package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The structured error payload every failure surfaces. */
class MondayApiExceptionTest {

    private static MondayApiException sample() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("boardId", "18440687408");
        context.put("itemId", "13590022086");
        return new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                "MONDAY_ITEM_NOT_FOUND",
                "No monday.com item with id 13590022086 is visible to this account.",
                "UpdateItem", context,
                "Check the id, or use Get Items to find the item by name first.");
    }

    @Test
    void carriesEveryFieldAStructuredErrorResponseNeeds() {
        Map<String, Object> fields = sample().toResponseFields();
        assertEquals("MONDAY_ITEM_NOT_FOUND", fields.get("Error Code"));
        assertEquals("UpdateItem", fields.get("Operation"));
        assertTrue(String.valueOf(fields.get("Error Message")).contains("13590022086"));
        assertTrue(String.valueOf(fields.get("Context")).contains("boardId=18440687408"));
        assertTrue(String.valueOf(fields.get("Suggested Action")).contains("Get Items"));
    }

    @Test
    void theDisplayMessageReadsAsOneActionableSentence() {
        String message = sample().toDisplayMessage();
        assertTrue(message.startsWith("[MONDAY_ITEM_NOT_FOUND]"));
        assertTrue(message.contains("itemId=13590022086"));
        assertTrue(message.contains("Check the id"));
    }

    @Test
    void anEmptyContextRendersCleanlyRatherThanAsEmptyBrackets() {
        MondayApiException e = new MondayApiException(MondayApiException.Kind.VALIDATION,
                "CODE", "Message.", "Op", Map.of(), "Do this.");
        assertEquals("", e.contextAsText());
        assertEquals("[CODE] Message. Do this.", e.toDisplayMessage());
    }

    @Test
    void onlyThrottlingAndTransportFailuresAreRetryable() {
        assertTrue(kind(MondayApiException.Kind.RATE_LIMIT).isRetryable());
        assertTrue(kind(MondayApiException.Kind.COMPLEXITY).isRetryable());
        assertTrue(kind(MondayApiException.Kind.NETWORK).isRetryable());
        assertFalse(kind(MondayApiException.Kind.AUTHENTICATION).isRetryable());
        assertFalse(kind(MondayApiException.Kind.VALIDATION).isRetryable());
        assertFalse(kind(MondayApiException.Kind.NOT_FOUND).isRetryable());
        assertFalse(kind(MondayApiException.Kind.PERMISSION).isRetryable());
    }

    @Test
    void theContextIsDefensivelyCopied() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("a", "1");
        MondayApiException e = new MondayApiException(MondayApiException.Kind.VALIDATION,
                "CODE", "Message.", "Op", context, "Do this.");
        context.put("b", "2");
        assertEquals(1, e.getContext().size());
    }

    private static MondayApiException kind(MondayApiException.Kind kind) {
        return new MondayApiException(kind, "CODE", "Message.", "Op", Map.of(), "Action.");
    }
}
