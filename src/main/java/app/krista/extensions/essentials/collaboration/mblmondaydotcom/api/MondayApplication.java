package app.krista.extensions.essentials.collaboration.mblmondaydotcom.api;

import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.annotations.Service;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.Set;

/**
 * JAX-RS application hosting the monday.com OAuth callback.
 *
 * <p>Mounted at {@code rest/mondayextn/...} — {@code mondayextn} is the {@code jaxrsId} declared on
 * the {@code @Extension} class. Changing one without the other silently 404s the callback, so the
 * value is referenced consistently here, in the authenticator and in the documentation.
 */
@Service
@ApplicationPath("/")
@ContractsProvided(Application.class)
public final class MondayApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(MondayAuthCallbackResource.class);
    }
}
