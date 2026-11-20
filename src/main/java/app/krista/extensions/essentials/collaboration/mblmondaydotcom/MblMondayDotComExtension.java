package app.krista.extensions.essentials.collaboration.mblmondaydotcom;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.ChangeLog;
import app.krista.extension.impl.anno.Extension;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.InvokerRequest;
import app.krista.extension.impl.anno.Java;
import app.krista.extension.impl.anno.StaticResource;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication.MondayRequestAuthenticator;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayAttributeStore;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayGraphQLClient;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayTokenStore;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.PendingAuthRegistry;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.util.RoutingUrls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

/**
 * MBL Monday Dot Com — Krista extension entry point.
 *
 * <p>Two authentication modes are offered, chosen by the Auth Type field on the Setup tab:
 * <ul>
 *   <li><b>OAuth</b> — interactive authorization-code sign-in, so each Krista user or tenant
 *       connects their own monday.com account (Krista auth Pattern C).</li>
 *   <li><b>ApiKey</b> — one pasted monday.com API token shared by everyone using this connection
 *       (Krista auth Pattern A).</li>
 * </ul>
 *
 * <p>The OAuth redirect URL is {@code <appliance>/rest/mondayextn/callback}; {@code mondayextn} is
 * the {@code jaxrsId} below, and the two must always agree or the callback silently 404s.
 */
@Field.PickOne(name = MondayAttributes.AUTH_TYPE,
        values = {MondayAttributes.AUTH_TYPE_OAUTH, MondayAttributes.AUTH_TYPE_API_KEY},
        required = true,
        description = "OAuth signs each user in to their own monday.com account. ApiKey uses one pasted token for everyone.")
@Field.Text(value = MondayAttributes.API_TOKEN, isSecured = true, required = false,
        description = "ApiKey mode only. A monday.com API token from Admin then API.")
@Field.Text(value = MondayAttributes.CLIENT_ID, isSecured = true, required = false,
        description = "OAuth mode only. The Client ID of your monday.com Developer Center app.")
@Field.Text(value = MondayAttributes.CLIENT_SECRET, isSecured = true, required = false,
        description = "OAuth mode only. The Client Secret of your monday.com Developer Center app.")
@Field.Text(value = MondayAttributes.SCOPES, required = false,
        description = "OAuth scopes to request. Defaults to me:read users:read boards:read boards:write updates:read updates:write")
@Field.Text(value = MondayAttributes.API_VERSION, required = false,
        description = "monday.com API version header. Defaults to 2026-10, the current stable version.")
@Field.Text(value = MondayAttributes.TIMEOUT_SECONDS, required = false,
        description = "HTTP timeout in seconds. Defaults to 60.")
@Field.Text(value = MondayAttributes.MAX_BOARDS_TO_SCAN, required = false,
        description = "How many boards Get Items may scan when no Board ID is given. Defaults to 25.")
@Extension(name = "MBL Monday Dot Com", version = "1.0.1", jaxrsId = "mondayextn",
        description = "Read, search, create and update monday.com board items from Krista agents and workflows.")
@StaticResource(path = "docs", file = "docs")
@ChangeLog(file = "docs/pages/ReleaseNotes.md")
@Java(version = Java.Version.JAVA_21)
public final class MblMondayDotComExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(MblMondayDotComExtension.class);

    private final Invoker invoker;
    private final MondayAttributes attributes;
    private final MondayAttributeStore attributeStore;
    private final MondayTokenStore tokenStore;
    private final MondayRequestAuthenticator requestAuthenticator;

    @Inject
    public MblMondayDotComExtension(Invoker invoker,
                                    MondayAttributes attributes,
                                    MondayAttributeStore attributeStore,
                                    MondayTokenStore tokenStore,
                                    PendingAuthRegistry pendingAuthRegistry) {
        this.invoker = invoker;
        this.attributes = attributes;
        this.attributeStore = attributeStore;
        this.tokenStore = tokenStore;
        // Built with plain `new`, never @Inject: HK2 in this container cannot bind a bare String
        // (invokerId / routingUrl), and a RequestAuthenticator that fails to construct takes this
        // whole class down with it — which blanks every custom tab, Documentation included.
        this.requestAuthenticator = new MondayRequestAuthenticator(
                attributes,
                attributeStore,
                pendingAuthRegistry,
                invoker.getInvokerId(),
                RoutingUrls.applianceUrl(invoker));
    }

    /** Setup-tab tabs. */
    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of("Documentation", "static/docs");
    }

    /** Registers the authenticator the platform uses to drive authorization for real requests. */
    @InvokerRequest(InvokerRequest.Type.AUTHENTICATOR)
    public RequestAuthenticator getRequestAuthenticator() {
        return requestAuthenticator;
    }

    /**
     * Validates the configuration before it is saved. Rejects a combination that cannot work, so
     * the operator finds out here rather than on the first failed catalog request.
     */
    @InvokerRequest(InvokerRequest.Type.VALIDATE_ATTRIBUTES)
    public void validateAttributes(Map<String, Object> connectionAttributes) {
        MondayAttributes candidate = MondayAttributes.create(invoker, connectionAttributes);

        if (candidate.isApiKeyMode()) {
            if (candidate.getApiToken().isBlank()) {
                throw new IllegalArgumentException(
                        "Auth Type is ApiKey, so an API Token is required. Generate one in monday.com "
                                + "under Admin then API, and paste it into the API Token field.");
            }
        } else {
            if (candidate.getClientId().isBlank() || candidate.getClientSecret().isBlank()) {
                throw new IllegalArgumentException(
                        "Auth Type is OAuth, so Client ID and Client Secret are both required. Create "
                                + "an app in the monday.com Developer Center and copy its credentials here.");
            }
            String callbackUrl = RoutingUrls.callbackUrl(invoker);
            if (callbackUrl != null) {
                LOGGER.info("monday.com OAuth redirect URL for this appliance: {}", callbackUrl);
            }
        }

        if (!candidate.getApiVersion().matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(
                    "API Version must look like 2026-10. Leave it blank to use the default.");
        }
    }

    /**
     * The Setup tab's Test Connection button. Checks the already-saved configuration by making one
     * real, cheap authenticated call, and logs the full failure before returning a clear message.
     */
    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() {
        MondayAttributes saved = attributeStore.load(invoker.getInvokerId());
        MondayAttributes effective = saved != null ? saved : attributes;

        // Check for missing credentials up front, so the operator gets a pointed message instead of
        // a cryptic downstream failure.
        if (effective.isApiKeyMode() && effective.getApiToken().isBlank()) {
            throw new IllegalStateException(
                    "No API Token is configured. Paste a monday.com API token on the Setup tab.");
        }
        if (effective.isOAuthMode()
                && (effective.getClientId().isBlank() || effective.getClientSecret().isBlank())) {
            throw new IllegalStateException(
                    "No monday.com OAuth app is configured. Enter the Client ID and Client Secret "
                            + "on the Setup tab.");
        }

        String token;
        if (effective.isApiKeyMode()) {
            token = effective.getApiToken();
        } else {
            MondayTokenStore.StoredToken stored =
                    tokenStore.load(MondayTokenStore.operatorKey(invoker.getInvokerId()));
            if (stored == null) {
                throw new IllegalStateException(
                        "This connection has not been signed in to monday.com yet. Save the Setup tab "
                                + "and complete the monday.com sign-in, then test again.");
            }
            token = stored.accessToken;
        }

        try {
            // A real call against the exact surface the catalog requests use — proving a token
            // exists is not the same as proving it works.
            MondayGraphQLClient client = new MondayGraphQLClient(effective, token);
            client.me();
            LOGGER.info("monday.com test connection succeeded for invoker {} (authType={})",
                    invoker.getInvokerId(), effective.getAuthType());
        } catch (MustAuthorizeException e) {
            LOGGER.error("monday.com test connection needs authorization for invoker {} (authType={})",
                    invoker.getInvokerId(), effective.getAuthType(), e);
            throw new IllegalStateException(
                    "monday.com requires sign-in for this connection. Complete the sign-in and test again.");
        } catch (MondayApiException e) {
            LOGGER.error("monday.com test connection failed for invoker {} (authType={}, code={})",
                    invoker.getInvokerId(), effective.getAuthType(), e.getErrorCode(), e);
            throw new IllegalStateException(e.toDisplayMessage());
        } catch (Exception e) {
            LOGGER.error("monday.com test connection failed unexpectedly for invoker {} (authType={})",
                    invoker.getInvokerId(), effective.getAuthType(), e);
            throw new IllegalStateException(
                    "Could not reach monday.com. Check the appliance's network access to api.monday.com.");
        }
    }

    /** Keeps the in-memory attributes and the durable copy in step after a Setup-tab save. */
    @InvokerRequest(InvokerRequest.Type.INVOKER_UPDATED)
    public void attributesUpdated(Map<String, Object> oldAttributes, Map<String, Object> newAttributes) {
        attributes.update(newAttributes);
        attributeStore.save(attributes, invoker.getInvokerId());
        LOGGER.info("monday.com configuration updated for invoker {}", invoker.getInvokerId());
    }
}
