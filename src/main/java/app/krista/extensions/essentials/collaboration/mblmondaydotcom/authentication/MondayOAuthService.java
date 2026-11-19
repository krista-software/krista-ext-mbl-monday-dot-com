package app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayTokenStore;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin wrapper over ScribeJava for monday.com's authorization-code flow.
 *
 * <p>Deliberately stateless and not an HK2 service: it is constructed with plain {@code new} from
 * already-resolved attributes and a callback URL, because HK2 in this container cannot bind a bare
 * {@code String} in any constructor.
 */
public final class MondayOAuthService {

    private static final Logger LOG = LoggerFactory.getLogger(MondayOAuthService.class);

    private final OAuth20Service delegate;

    public MondayOAuthService(MondayAttributes attributes, String callbackUrl) {
        this.delegate = new ServiceBuilder(attributes.getClientId())
                .apiSecret(attributes.getClientSecret())
                .defaultScope(attributes.getScopes())
                .callback(callbackUrl)
                .build(MondayOAuth20Api.instance());
    }

    /** The URL the user's browser is sent to. {@code state} carries the Krista identity back. */
    public String getAuthorizationUrl(String state) {
        return delegate.getAuthorizationUrl(state);
    }

    /** Exchanges the authorization code for a token set. */
    public MondayTokenStore.StoredToken exchangeCode(String code) {
        try {
            return toStoredToken(delegate.getAccessToken(code));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw authFailure("The monday.com token exchange was interrupted.", e);
        } catch (Exception e) {
            LOG.error("monday.com authorization-code exchange failed", e);
            throw authFailure("monday.com rejected the authorization code.", e);
        }
    }

    /**
     * Refreshes an access token.
     *
     * <p>The supplied reference flags it as unverified whether monday issues refresh tokens at all
     * (its tokens are believed not to expire until revoked). This path therefore exists but is only
     * taken when a refresh token was actually returned at sign-in; otherwise the caller re-drives
     * authorization instead.
     */
    public MondayTokenStore.StoredToken refresh(String refreshToken) {
        try {
            return toStoredToken(delegate.refreshAccessToken(refreshToken));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw authFailure("The monday.com token refresh was interrupted.", e);
        } catch (Exception e) {
            LOG.error("monday.com token refresh failed", e);
            throw authFailure("monday.com refused to refresh the access token.", e);
        }
    }

    private static MondayTokenStore.StoredToken toStoredToken(OAuth2AccessToken token) {
        Long expiresAt = null;
        if (token.getExpiresIn() != null) {
            expiresAt = System.currentTimeMillis() + (token.getExpiresIn() * 1000L);
        }
        return new MondayTokenStore.StoredToken(
                token.getAccessToken(), token.getRefreshToken(), expiresAt, token.getScope());
    }

    private static MondayApiException authFailure(String message, Throwable cause) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("provider", "monday.com");
        // The cause's message can echo an OAuth error body; keep it out of the user-facing text.
        return new MondayApiException(MondayApiException.Kind.AUTHENTICATION,
                "MONDAY_OAUTH_EXCHANGE_FAILED",
                message,
                "oauth", context,
                "Confirm the Client ID, Client Secret and redirect URL registered in the monday.com "
                        + "Developer Center match this extension's Setup tab, then sign in again.",
                cause);
    }
}
