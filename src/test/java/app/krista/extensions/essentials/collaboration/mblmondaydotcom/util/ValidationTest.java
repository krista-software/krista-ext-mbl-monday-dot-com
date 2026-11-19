package app.krista.extensions.essentials.collaboration.mblmondaydotcom.util;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fail-fast input validation. */
class ValidationTest {

    private static final String OP = "GetItems";

    @Test
    void requiresAMandatoryTextField() {
        MondayApiException e = assertThrows(MondayApiException.class,
                () -> Validation.requireText("  ", "Item Name", OP));
        assertEquals("MONDAY_MISSING_REQUIRED_INPUT", e.getErrorCode());
        assertTrue(e.getMessage().contains("Item Name"));
    }

    @Test
    void acceptsAndTrimsANumericId() {
        assertEquals("18440687408", Validation.requireId(" 18440687408 ", "Board ID", OP));
    }

    @Test
    void rejectsANonNumericIdBeforeTheNetworkCall() {
        MondayApiException e = assertThrows(MondayApiException.class,
                () -> Validation.requireId("Engineering", "Board ID", OP));
        assertEquals("MONDAY_INVALID_ID", e.getErrorCode());
    }

    @Test
    void anOptionalIdIsNullWhenBlankButStillValidatedWhenPresent() {
        assertNull(Validation.optionalId("  ", "Board ID", OP));
        assertEquals("12", Validation.optionalId("12", "Board ID", OP));
        assertThrows(MondayApiException.class, () -> Validation.optionalId("abc", "Board ID", OP));
    }

    @Test
    void pageSizeDefaultsAndIsClampedToMondaysMaximum() {
        assertEquals(Validation.DEFAULT_PAGE_SIZE, Validation.pageSize(null));
        assertEquals(Validation.DEFAULT_PAGE_SIZE, Validation.pageSize(0.0));
        assertEquals(Validation.DEFAULT_PAGE_SIZE, Validation.pageSize(-5.0));
        assertEquals(50, Validation.pageSize(50.0));
        assertEquals(Validation.MAX_PAGE_SIZE, Validation.pageSize(9000.0),
                "a limit above monday's 500 maximum is clamped, not rejected");
    }

    @Test
    void boardScanCapFallsBackToTheConfiguredDefault() {
        assertEquals(25, Validation.boardScanCap(null, 25));
        assertEquals(10, Validation.boardScanCap(10.0, 25));
        assertEquals(25, Validation.boardScanCap(0.0, 25));
        assertEquals(Validation.MAX_BOARDS_HARD_CAP, Validation.boardScanCap(5000.0, 25));
    }

    @Test
    void booleanDefaultsHandleTheNullKristaInput() {
        assertTrue(Validation.booleanOrDefault(null, true));
        assertFalse(Validation.booleanOrDefault(null, false));
        assertFalse(Validation.booleanOrDefault(Boolean.FALSE, true));
    }

    @Test
    void hexColoursAreNormalisedAndBadOnesRejected() {
        assertEquals("#037f4c", Validation.optionalHexColour("037f4c", "Group Color", "CreateGroup"));
        assertEquals("#037F4C", Validation.optionalHexColour("#037F4C", "Group Color", "CreateGroup"));
        assertNull(Validation.optionalHexColour(null, "Group Color", "CreateGroup"));
        MondayApiException e = assertThrows(MondayApiException.class,
                () -> Validation.optionalHexColour("green", "Group Color", "CreateGroup"));
        assertEquals("MONDAY_INVALID_COLOUR", e.getErrorCode());
    }

    @Test
    void blankHelpersBehaveAsExpected() {
        assertTrue(Validation.isBlank(null));
        assertTrue(Validation.isBlank("   "));
        assertFalse(Validation.isBlank(" x "));
        assertEquals("x", Validation.trimToNull(" x "));
        assertNull(Validation.trimToNull("   "));
    }
}
