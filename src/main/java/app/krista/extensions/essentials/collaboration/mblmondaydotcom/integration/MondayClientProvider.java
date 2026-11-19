package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.Invoker;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication.MondayOAuthService;
import app.krista.ksdk.context.AuthorizationContext;
import app.krista.ksdk.context.RequestContext;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link MondayGraphQLClient} with the right credentials for whoever is calling.
 *
 * <p>Three call contexts exist and they must not be confused (the same split Pattern D documents,
 * which applies to any interactive flow):
 * <ol>
 *   <li><b>operator</b> — the connection configured once on the Setup tab. Used by Test Connection
 *       and Health Check, and by every catalog request when no end-user identity is in play.</li>
 *   <li><b>a real end user</b> — identified by {@code RequestContext#invokeAsUser()}.</li>
 *   <li><b>an MCP agent acting for a specific end user</b> — identified identically.</li>
 * </ol>
 * Cases 2 and 3 read and write a separate credential keyspace, so an end-user's request can never
 * silently run as the operator's monday.com account.
 */
@Service
public class MondayClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MondayClientProvider.class);

    private final MondayAttributes injectedAttributes;
    private final MondayAttributeStore attributeStore;
    private final MondayTokenStore tokenStore;
    private final PendingAuthRegistry pendingAuthRegistry;
    private final RequestContext requestContext;
    private final AuthorizationContext authorizationContext;
    private final String invokerId;

    @Inject
    public MondayClientProvider(MondayAttributes injectedAttributes,
                                MondayAttributeStore attributeStore,
                                MondayTokenStore tokenStore,
                                PendingAuthRegistry pendingAuthRegistry,
                                RequestContext requestContext,
                                AuthorizationContext authorizationContext,
                                Invoker invoker) {
        this.injectedAttributes = injectedAttributes;
        this.attributeStore = attributeStore;
        this.tokenStore = tokenStore;
        this.pendingAuthRegistry = pendingAuthRegistry;
        this.requestContext = requestContext;
        this.authorizationContext = authorizationContext;
        this.invokerId = invoker.getInvokerId();
    }

    public String getInvokerId() {
        return invokerId;
    }

    /** True when the current call is attributed to a specific end user (or an MCP agent for one). */
    public boolean isInvokeAsUser() {
        try {
            return requestContext != null && requestContext.invokeAsUser();
        } catch (RuntimeException e) {
            // Never let a context probe break a catalog request; operator is the safe reading.
            LOG.debug("Could not read invokeAsUser(); treating this call as operator context");
            return false;
        }
    }

    /** The saved connection configuration, falling back to the live invoker attributes. */
    public MondayAttributes attributes() {
        MondayAttributes stored = attributeStore.load(invokerId);
        return stored != null ? stored : injectedAttributes;
    }

    /**
     * The client for a real catalog request — dispatches on {@code invokeAsUser()}.
     *
     * @throws MustAuthorizeException when an end-user has not yet connected their monday.com
     *                                account. Never catch this here: the platform turns it into a
     *                                sign-in prompt for that user.
     */
    public MondayGraphQLClient forRequest() {
        return isInvokeAsUser() ? forCurrentUser() : forOperator();
    }

    /** The operator connection configured on the Setup tab. */
    public MondayGraphQLClient forOperator() {
        MondayAttributes attrs = attributes();
        String token = operatorToken(attrs);
        return new MondayGraphQLClient(attrs, token);
    }

    private MondayGraphQLClient forCurrentUser() {
        MondayAttributes attrs = attributes();
        if (attrs.isApiKeyMode()) {
            // One shared token is the whole point of ApiKey mode — there is no per-user identity.
            return new MondayGraphQLClient(attrs, operatorToken(attrs));
        }
        String accountId = currentAccountId();
        if (accountId == null || accountId.isBlank()) {
            LOG.warn("invokeAsUser() is true but no authorized account id is available; "
                    + "falling back to the operator connection for invoker {}", invokerId);
            return new MondayGraphQLClient(attrs, operatorToken(attrs));
        }

        String key = MondayTokenStore.userKey(accountId);
        MondayTokenStore.StoredToken token = tokenStore.load(key);
        if (token != null) {
            return new MondayGraphQLClient(attrs, usableAccessToken(attrs, key, token));
        }

        // No per-user credentials yet — ask the platform to prompt this user to sign in. Record who
        // the pending sign-in belongs to so the authenticator and callback store the resulting
        // token under the per-user key rather than the operator's.
        if (attrs.getClientId().isBlank() || attrs.getClientSecret().isBlank()) {
            throw new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                    "MONDAY_OAUTH_NOT_CONFIGURED",
                    "Sign-in on behalf of this user is not available: no monday.com OAuth app is "
                            + "configured for this connection.",
                    "authorize", Map.of(),
                    "Add the Client ID and Client Secret on the Setup tab, or switch Auth Type to ApiKey.");
        }
        pendingAuthRegistry.recordPendingUser(invokerId, accountId);
        LOG.info("No monday.com token for account {} on invoker {} — requesting authorization",
                accountId, invokerId);
        throw new MustAuthorizeException(
                "Connect your monday.com account to continue.", List.of());
    }

    /** Resolves the token for the operator connection, refreshing it when that is possible. */
    private String operatorToken(MondayAttributes attrs) {
        if (attrs.isApiKeyMode()) {
            if (attrs.getApiToken().isBlank()) {
                throw new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                        "MONDAY_NO_API_TOKEN",
                        "No monday.com API token is configured.",
                        "authenticate", Map.of("authType", attrs.getAuthType()),
                        "Paste a monday.com API token into the API Token field on the Setup tab.");
            }
            return attrs.getApiToken();
        }

        String key = MondayTokenStore.operatorKey(invokerId);
        MondayTokenStore.StoredToken token = tokenStore.load(key);
        if (token == null) {
            if (attrs.getClientId().isBlank() || attrs.getClientSecret().isBlank()) {
                throw new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                        "MONDAY_OAUTH_NOT_CONFIGURED",
                        "This connection is set to OAuth but has no Client ID or Client Secret.",
                        "authenticate", Map.of(),
                        "Enter the monday.com app's Client ID and Client Secret on the Setup tab, "
                                + "then run Test Connection to sign in.");
            }
            LOG.info("No stored monday.com token for invoker {} — requesting authorization", invokerId);
            throw new MustAuthorizeException(
                    "Sign in to monday.com to connect this extension.", List.of());
        }
        return usableAccessToken(attrs, key, token);
    }

    /**
     * Returns a currently-valid access token, refreshing first when monday gave us both an expiry
     * and a refresh token.
     *
     * <p>The supplied reference flags monday's token lifetime as unverified — tokens are believed
     * not to expire until revoked, and a refresh token may never be issued. This handles both
     * worlds: refresh when we can, otherwise re-drive authorization.
     */
    private String usableAccessToken(MondayAttributes attrs, String key, MondayTokenStore.StoredToken token) {
        if (!token.isExpired()) {
            return token.accessToken;
        }
        if (!token.hasRefreshToken()) {
            LOG.info("Stored monday.com token for key {} expired with no refresh token — "
                    + "requesting re-authorization", key);
            tokenStore.remove(key);
            throw new MustAuthorizeException(
                    "Your monday.com session expired. Sign in again to continue.", List.of());
        }
        LOG.info("Refreshing the expired monday.com token for key {}", key);
        MondayTokenStore.StoredToken refreshed =
                new MondayOAuthService(attrs, callbackUrlForRefresh()).refresh(token.refreshToken);
        if (refreshed.refreshToken == null || refreshed.refreshToken.isBlank()) {
            // monday may not rotate refresh tokens — keep the one we already hold.
            refreshed.refreshToken = token.refreshToken;
        }
        tokenStore.save(key, refreshed);
        return refreshed.accessToken;
    }

    /**
     * A refresh does not redirect a browser, but ScribeJava still wants a callback configured.
     * The value is unused by the token endpoint for {@code grant_type=refresh_token}.
     */
    private String callbackUrlForRefresh() {
        return "https://localhost/rest/mondayextn/callback";
    }

    /** The authorized end user's account id, or null when there is none. */
    public String currentAccountId() {
        try {
            if (authorizationContext == null || authorizationContext.getAuthorizedAccount() == null) {
                return null;
            }
            return authorizationContext.getAuthorizedAccount().getAccountId();
        } catch (RuntimeException e) {
            LOG.debug("No authorized account available on this call");
            return null;
        }
    }

    /** Context shown alongside an authentication failure — never includes credentials. */
    public Map<String, Object> authContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("invokerId", invokerId);
        context.put("authType", attributes().getAuthType());
        context.put("invokeAsUser", isInvokeAsUser());
        return context;
    }
}
