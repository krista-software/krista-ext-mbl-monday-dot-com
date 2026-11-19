package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.errorhandlers;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes a {@link MustAuthorizeException} from any catalog request based on whether the call is
 * attributed to an end user.
 *
 * <p>The distinction matters: in a per-user or MCP context the platform needs the real exception so
 * it can generate a sign-in prompt for that person. Converting it into an ordinary failure response
 * there would silently break per-user authentication — the end user would just see "it failed" with
 * no way to connect their account. In operator context there is nobody to redirect, so a normal
 * failure pointing at the Setup tab is the right answer.
 */
public final class AuthorizationExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationExceptionHandler.class);

    private AuthorizationExceptionHandler() {
    }

    public static ExtensionResponse handleAuthorizationException(MustAuthorizeException exception,
                                                                 boolean invokeAsUser)
            throws MustAuthorizeException {
        LOG.info("Handling MustAuthorizeException, invokeAsUser={}, message={}",
                invokeAsUser, exception.getMessage());
        if (invokeAsUser) {
            throw exception;
        }
        return new ExtensionResponseBuilder()
                .failure("Authentication required: " + exception.getMessage()
                        + " Open the extension's Setup tab and sign in to monday.com.")
                .build();
    }
}
