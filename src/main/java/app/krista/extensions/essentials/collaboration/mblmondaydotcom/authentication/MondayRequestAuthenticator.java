package app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication;

import app.krista.extension.authorization.MustAuthenticateException;
import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.ProtoResponse;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayAttributeStore;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.PendingAuthRegistry;
import app.krista.model.field.NamedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request authenticator covering both modes this extension supports.
 *
 * <p>In {@code ApiKey} mode the invoker itself is the authenticated account and there is no browser
 * flow. In {@code OAuth} mode this builds the monday.com authorize URL when the platform reports
 * that a caller must authorise.
 *
 * <p><b>Not</b> an HK2 {@code @Service}: it is constructed with plain {@code new} from the extension
 * class, which legitimately injects {@link app.krista.extension.executor.Invoker}. HK2 in this
 * container cannot bind a bare {@code String}, and a failed authenticator construction would blank
 * every custom tab, not just authentication.
 */
public class MondayRequestAuthenticator implements RequestAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(MondayRequestAuthenticator.class);

    static final String CALLBACK_PATH = "/callback";

    private final MondayAttributes attributes;
    private final MondayAttributeStore attributeStore;
    private final PendingAuthRegistry pendingAuthRegistry;
    private final String invokerId;
    private final String routingUrl;

    public MondayRequestAuthenticator(MondayAttributes attributes,
                                      MondayAttributeStore attributeStore,
                                      PendingAuthRegistry pendingAuthRegistry,
                                      String invokerId,
                                      String routingUrl) {
        this.attributes = attributes;
        this.attributeStore = attributeStore;
        this.pendingAuthRegistry = pendingAuthRegistry;
        this.invokerId = invokerId;
        this.routingUrl = routingUrl;
    }

    /** The OAuth redirect URI — must match the one registered in the monday Developer Center. */
    public String callbackUrl() {
        String base = routingUrl == null ? "" : routingUrl.replaceAll("/+$", "");
        return base + "/rest/mondayextn" + CALLBACK_PATH;
    }

    @Override
    public String getScheme() {
        return attributes.isApiKeyMode() ? "PAT" : "OAuth2";
    }

    @Override
    public Set<String> getSupportedProtocols() {
        return Set.of("https");
    }

    @Override
    public String getAuthenticatedAccountId(ProtoRequest protoRequest) {
        if (attributes.isApiKeyMode()) {
            // Token mode: the invoker is the authenticated account.
            return invokerId;
        }
        String state = readStateParameter(protoRequest);
        if (state == null || state.isBlank()) {
            return invokerId;
        }
        return userIdFromState(state);
    }

    @Override
    public boolean setServiceAuthorization(String authorization) {
        return false;
    }

    @Override
    public Map<String, NamedField> getAttributeFields() {
        return Collections.emptyMap();
    }

    @Override
    public ProtoResponse getMustAuthenticateResponse(MustAuthenticateException exception, ProtoRequest request) {
        return null;
    }

    @Override
    public AuthorizationResponse getMustAuthenticateResponse(MustAuthenticateException exception) {
        return new AuthorizationResponse(null, List.of());
    }

    @Override
    public ProtoResponse getMustAuthorizeResponse(MustAuthorizeException exception, ProtoRequest request) {
        return null;
    }

    @Override
    public AuthorizationResponse getMustAuthorizeResponse(MustAuthorizeException exception) {
        if (attributes.isApiKeyMode()) {
            // No browser flow exists in token mode — there is nothing to redirect to.
            LOG.warn("MustAuthorize raised while in ApiKey mode; the API Token on the Setup tab is "
                    + "missing or rejected.");
            return new AuthorizationResponse(null, List.of());
        }
        if (attributes.getClientId().isBlank() || attributes.getClientSecret().isBlank()) {
            LOG.error("Cannot build a monday.com authorize URL: Client ID / Client Secret are not "
                    + "configured on the Setup tab for invoker {}", invokerId);
            return new AuthorizationResponse(null, List.of());
        }

        Map<String, String> details = readDetails(exception);
        String userId = details.getOrDefault("userId", invokerId);
        String authContextId = details.get("authContextId");
        if (authContextId == null || authContextId.isBlank()) {
            // Carry the current configuration across the browser round-trip so the callback can
            // read it even before the connection has been saved.
            authContextId = attributeStore.save(attributes);
        }
        // The per-user account id reaches us one of two ways: in the exception details when the
        // platform supplies them, or via the registry the client provider wrote just before it
        // raised MustAuthorizeException for an invoke-as-user call.
        String clientUserId = details.get("clientUserId");
        if ((clientUserId == null || clientUserId.isBlank()) && pendingAuthRegistry != null) {
            clientUserId = pendingAuthRegistry.pendingUser(invokerId);
        }

        String state = buildState(userId, authContextId, clientUserId);
        String authorizeUrl = new MondayOAuthService(attributes, callbackUrl()).getAuthorizationUrl(state);
        LOG.info("Returning monday.com authorize URL for userId {} (perUser={})",
                userId, clientUserId != null && !clientUserId.isBlank());
        return new AuthorizationResponse(authorizeUrl, List.of());
    }

    // ---------------------------------------------------------------------------------------------
    // state encoding: "<userId>#authContextId_<uuid>[#wsContact_<accountId>]"
    // ---------------------------------------------------------------------------------------------

    static String buildState(String userId, String authContextId, String clientUserId) {
        StringBuilder state = new StringBuilder(userId == null ? "" : userId);
        if (authContextId != null && !authContextId.isBlank()) {
            state.append('#').append(authContextId);
        }
        if (clientUserId != null && !clientUserId.isBlank()) {
            state.append("#wsContact_").append(clientUserId);
        }
        return state.toString();
    }

    public static String userIdFromState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        int hash = state.indexOf('#');
        return hash < 0 ? state : state.substring(0, hash);
    }

    /** The transient attribute key folded into state at sign-in time, or null. */
    public static String authContextIdFromState(String state) {
        return segmentWithPrefix(state, "authContextId_");
    }

    /** The per-user account id folded into state for an invoke-as-user sign-in, or null. */
    public static String clientUserIdFromState(String state) {
        String segment = segmentWithPrefix(state, "wsContact_");
        return segment == null ? null : segment.substring("wsContact_".length());
    }

    private static String segmentWithPrefix(String state, String prefix) {
        if (state == null) {
            return null;
        }
        for (String segment : state.split("#")) {
            if (segment.startsWith(prefix)) {
                return segment;
            }
        }
        return null;
    }

    /**
     * Reads {@code userId} / {@code authContextId} / {@code clientUserId} out of the exception's
     * details.
     *
     * <p>Read reflectively on purpose: the detail carrier's concrete accessor names are a platform
     * implementation detail, and an authenticator that throws while building a redirect would leave
     * the user with no way to sign in at all. A read failure degrades to "use the invoker id", which
     * is the correct operator-context answer.
     */
    private static Map<String, String> readDetails(MustAuthorizeException exception) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Object rawDetails = exception.getDetails();
            if (!(rawDetails instanceof Collection<?> collection)) {
                return result;
            }
            for (Object detail : collection) {
                if (detail == null) {
                    continue;
                }
                String name = invokeFirst(detail, "getName", "name");
                String value = invokeFirst(detail, "getValue", "value");
                if (name != null && value != null) {
                    result.put(name, value);
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not read MustAuthorizeException details; defaulting to the invoker id");
        }
        return result;
    }

    private static String invokeFirst(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the next candidate accessor
            }
        }
        return null;
    }

    /** Best-effort read of the {@code state} query parameter from an inbound callback request. */
    private static String readStateParameter(ProtoRequest protoRequest) {
        if (protoRequest == null) {
            return null;
        }
        try {
            Method method = protoRequest.getClass().getMethod("getUri");
            Object uri = method.invoke(protoRequest);
            if (uri == null) {
                return null;
            }
            String text = String.valueOf(uri);
            int stateIndex = text.indexOf("state=");
            if (stateIndex < 0) {
                return null;
            }
            String tail = text.substring(stateIndex + "state=".length());
            int amp = tail.indexOf('&');
            return amp < 0 ? tail : tail.substring(0, amp);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
