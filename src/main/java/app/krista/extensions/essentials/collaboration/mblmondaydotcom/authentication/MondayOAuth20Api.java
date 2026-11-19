package app.krista.extensions.essentials.collaboration.mblmondaydotcom.authentication;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import com.github.scribejava.core.builder.api.DefaultApi20;

/**
 * ScribeJava descriptor for monday.com's OAuth 2.0 authorization-code flow.
 *
 * <p>monday is not one of ScribeJava's bundled providers, so its two endpoints are declared here.
 * Both come from the supplied API reference:
 * <ul>
 *   <li>authorize — {@code https://auth.monday.com/oauth2/authorize}</li>
 *   <li>token — {@code https://auth.monday.com/oauth2/token}</li>
 * </ul>
 */
public final class MondayOAuth20Api extends DefaultApi20 {

    private MondayOAuth20Api() {
    }

    private static final class InstanceHolder {
        private static final MondayOAuth20Api INSTANCE = new MondayOAuth20Api();
    }

    public static MondayOAuth20Api instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return MondayAttributes.OAUTH_TOKEN_URL;
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return MondayAttributes.OAUTH_AUTHORIZE_URL;
    }
}
