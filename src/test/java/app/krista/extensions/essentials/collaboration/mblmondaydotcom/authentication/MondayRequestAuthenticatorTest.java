package app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The OAuth {@code state} round-trip.
 *
 * <p>state carries three things from the authorize call back to the callback: who is signing in,
 * which transient configuration to read, and — for an end-user sign-in — whose per-user keyspace
 * the resulting token belongs in. Getting this wrong would store an end-user's token as the
 * operator's.
 */
class MondayRequestAuthenticatorTest {

    @Test
    void operatorStateCarriesTheUserIdAndAuthContextId() {
        String state = MondayRequestAuthenticator.buildState("invoker-1", "authContextId_abc", null);
        assertEquals("invoker-1#authContextId_abc", state);
        assertEquals("invoker-1", MondayRequestAuthenticator.userIdFromState(state));
        assertEquals("authContextId_abc", MondayRequestAuthenticator.authContextIdFromState(state));
        assertNull(MondayRequestAuthenticator.clientUserIdFromState(state));
    }

    @Test
    void perUserStateAlsoCarriesTheAccountId() {
        String state = MondayRequestAuthenticator.buildState("invoker-1", "authContextId_abc", "acct-9");
        assertEquals("invoker-1#authContextId_abc#wsContact_acct-9", state);
        assertEquals("invoker-1", MondayRequestAuthenticator.userIdFromState(state));
        assertEquals("authContextId_abc", MondayRequestAuthenticator.authContextIdFromState(state));
        assertEquals("acct-9", MondayRequestAuthenticator.clientUserIdFromState(state));
    }

    @Test
    void stateWithNoExtraSegmentsStillYieldsTheUserId() {
        assertEquals("invoker-1", MondayRequestAuthenticator.userIdFromState("invoker-1"));
        assertNull(MondayRequestAuthenticator.authContextIdFromState("invoker-1"));
        assertNull(MondayRequestAuthenticator.clientUserIdFromState("invoker-1"));
    }

    @Test
    void aMissingStateIsHandledRatherThanThrowing() {
        assertNull(MondayRequestAuthenticator.userIdFromState(null));
        assertNull(MondayRequestAuthenticator.userIdFromState(""));
        assertNull(MondayRequestAuthenticator.authContextIdFromState(null));
        assertNull(MondayRequestAuthenticator.clientUserIdFromState(null));
    }

    @Test
    void buildStateOmitsSegmentsItWasNotGiven() {
        assertEquals("invoker-1", MondayRequestAuthenticator.buildState("invoker-1", null, null));
        assertEquals("invoker-1", MondayRequestAuthenticator.buildState("invoker-1", "  ", ""));
    }
}
