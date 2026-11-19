package app.krista.extensions.essentials.collaboration.mblmondaydotcom.api;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication.MondayOAuthService;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication.MondayRequestAuthenticator;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayAttributeStore;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayTokenStore;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.PendingAuthRegistry;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.util.RoutingUrls;
import app.krista.extension.executor.Invoker;
import app.krista.ksdk.authentication.AuthorizationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;

/**
 * monday.com OAuth 2.0 callback. Full route: {@code rest/mondayextn/callback}.
 *
 * <p>This exact URL is what must be registered as the app's redirect URL in the monday Developer
 * Center.
 *
 * <p>On callback it exchanges the authorization code, stores the token set in the <b>durable</b>
 * {@link MondayTokenStore} (the same store every catalog request reads from — using a different one
 * here would make the sign-in invisible to real work), and notifies the platform's
 * {@link AuthorizationListener}.
 *
 * <p>The {@code state} parameter carries the Krista user id, the transient attribute key saved
 * before the browser round-trip, and — for an end-user sign-in — the account id, so the token lands
 * in the per-user keyspace rather than the operator's.
 */
@Path("/")
public final class MondayAuthCallbackResource {

    private static final Logger LOG = LoggerFactory.getLogger(MondayAuthCallbackResource.class);

    private final AuthorizationListener authorizationListener;
    private final MondayAttributeStore attributeStore;
    private final MondayTokenStore tokenStore;
    private final PendingAuthRegistry pendingAuthRegistry;
    private final Invoker invoker;

    @Inject
    public MondayAuthCallbackResource(AuthorizationListener authorizationListener,
                                      MondayAttributeStore attributeStore,
                                      MondayTokenStore tokenStore,
                                      PendingAuthRegistry pendingAuthRegistry,
                                      Invoker invoker) {
        this.authorizationListener = authorizationListener;
        this.attributeStore = attributeStore;
        this.tokenStore = tokenStore;
        this.pendingAuthRegistry = pendingAuthRegistry;
        this.invoker = invoker;
    }

    @GET
    @Path("/callback")
    @Produces(MediaType.TEXT_HTML)
    public Response callback(@QueryParam("code") String code,
                             @QueryParam("state") String state,
                             @QueryParam("error") String error,
                             @QueryParam("error_description") String errorDescription) {

        if (error != null && !error.isBlank()) {
            LOG.error("monday.com returned an OAuth error on the callback: {} ({})", error, errorDescription);
            return html(false, "monday.com declined the authorization: " + escape(error)
                    + ". Check that the app's redirect URL and scopes are correct, then try again.");
        }
        if (code == null || code.isBlank()) {
            LOG.error("monday.com callback arrived without an authorization code");
            return html(false, "No authorization code was returned by monday.com. Please start the "
                    + "sign-in again from the Setup tab.");
        }

        String invokerId = invoker.getInvokerId();
        String userId = MondayRequestAuthenticator.userIdFromState(state);
        String authContextId = MondayRequestAuthenticator.authContextIdFromState(state);
        String clientUserId = MondayRequestAuthenticator.clientUserIdFromState(state);

        try {
            // Prefer the candidate attributes saved before the browser round-trip; they exist even
            // when the connection has not been saved yet (a first-time Test Connection).
            MondayAttributes attributes = authContextId == null ? null : attributeStore.load(authContextId);
            if (attributes == null) {
                attributes = attributeStore.load(invokerId);
            }
            if (attributes == null) {
                LOG.error("No monday.com configuration found for the callback (invoker {}, authContextId {})",
                        invokerId, authContextId);
                return html(false, "No saved configuration was found for this connection. "
                        + "Re-open the Setup tab and sign in again.");
            }

            String callbackUrl = buildCallbackUrl();
            MondayTokenStore.StoredToken token =
                    new MondayOAuthService(attributes, callbackUrl).exchangeCode(code);

            String key = (clientUserId != null && !clientUserId.isBlank())
                    ? MondayTokenStore.userKey(clientUserId)
                    : MondayTokenStore.operatorKey(invokerId);
            tokenStore.save(key, token);

            if (clientUserId != null && !clientUserId.isBlank()) {
                pendingAuthRegistry.clearPendingUser(invokerId);
            }

            notifyAuthorized(userId == null ? invokerId : userId);
            LOG.info("monday.com authorization completed for key {} (perUser={})",
                    key, clientUserId != null && !clientUserId.isBlank());
            return html(true, "You are connected to monday.com. You may close this window.");

        } catch (Exception e) {
            // Log the detail; show the user something actionable and credential-free.
            LOG.error("monday.com authorization callback failed for invoker {}", invokerId, e);
            return html(false, "Could not complete the monday.com sign-in. Verify the Client ID, "
                    + "Client Secret and redirect URL on the Setup tab, then try again.");
        } finally {
            if (authContextId != null && !authContextId.isBlank()) {
                attributeStore.remove(authContextId);
            }
        }
    }

    private String buildCallbackUrl() {
        // Rebuild the exact redirect_uri used at authorize time; monday verifies it on exchange.
        String callbackUrl = RoutingUrls.callbackUrl(invoker);
        return callbackUrl == null ? RoutingUrls.CALLBACK_PATH : callbackUrl;
    }

    /**
     * Tells the platform the authorization finished. Invoked reflectively across the plausible
     * listener signatures so a mismatch logs a warning instead of breaking a completed sign-in that
     * has already been persisted.
     */
    private void notifyAuthorized(String userId) {
        if (authorizationListener == null) {
            return;
        }
        Object[][] candidateArgs = {{userId}, {}};
        for (Object[] args : candidateArgs) {
            try {
                Class<?>[] types = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) {
                    types[i] = String.class;
                }
                Method method = authorizationListener.getClass().getMethod("authorized", types);
                method.invoke(authorizationListener, args);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the next shape
            }
        }
        LOG.warn("Could not notify the platform AuthorizationListener; the token was still stored.");
    }

    private static Response html(boolean success, String message) {
        String title = success ? "Connected to monday.com" : "monday.com sign-in failed";
        String colour = success ? "#0073ea" : "#d83a52";
        String body = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>" + title + "</title></head>"
                + "<body style=\"font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
                + "padding:48px;max-width:640px;margin:0 auto;\">"
                + "<h2 style=\"color:" + colour + ";margin-bottom:8px;\">" + title + "</h2>"
                + "<p style=\"color:#333;line-height:1.5;\">" + escape(message) + "</p>"
                + "</body></html>";
        return Response.status(success ? Response.Status.OK : Response.Status.BAD_REQUEST)
                .entity(body)
                .type(MediaType.TEXT_HTML)
                .build();
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
