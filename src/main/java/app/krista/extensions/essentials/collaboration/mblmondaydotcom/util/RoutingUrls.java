package app.krista.extensions.essentials.collaboration.mblmondaydotcom.util;

import app.krista.extension.executor.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Resolves the appliance routing URL that the OAuth redirect URI is built from.
 *
 * <p>Read reflectively and defensively on purpose. This value is needed while the extension class
 * is being constructed, and a hard failure there does not just break authentication — it stops the
 * whole extension bean from building, which blanks every custom tab including Documentation. A
 * lookup failure here degrades to {@code null}, which produces a clear "redirect URL not resolved"
 * message instead.
 */
public final class RoutingUrls {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingUrls.class);

    /** The JAX-RS mount id declared on the @Extension annotation. Keep the two in step. */
    public static final String JAXRS_ID = "mondayextn";
    public static final String CALLBACK_PATH = "/rest/" + JAXRS_ID + "/callback";

    private RoutingUrls() {
    }

    /** The appliance base URL, or null when it cannot be resolved. */
    public static String applianceUrl(Invoker invoker) {
        if (invoker == null) {
            return null;
        }
        try {
            Object routingInfo = invoker.getClass().getMethod("getRoutingInfo").invoke(invoker);
            if (routingInfo == null) {
                return null;
            }
            for (Method method : routingInfo.getClass().getMethods()) {
                if (!"getRoutingURL".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Object[] args = argumentsFor(method);
                if (args == null) {
                    continue;
                }
                Object url = method.invoke(routingInfo, args);
                if (url != null) {
                    return String.valueOf(url);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Could not resolve the appliance routing URL; the OAuth redirect URL will be unset");
        }
        return null;
    }

    /** The full OAuth redirect URI to register in the monday Developer Center. */
    public static String callbackUrl(Invoker invoker) {
        String base = applianceUrl(invoker);
        return base == null ? null : trimTrailingSlashes(base) + CALLBACK_PATH;
    }

    public static String trimTrailingSlashes(String value) {
        return value == null ? null : value.replaceAll("/+$", "");
    }

    /** Fills {@code (String protocol, RoutingInfo.Type type)} without importing either type. */
    private static Object[] argumentsFor(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == String.class) {
                args[i] = "http";
            } else if (types[i].isEnum()) {
                Object chosen = null;
                for (Object constant : types[i].getEnumConstants()) {
                    if ("APPLIANCE".equals(String.valueOf(constant))) {
                        chosen = constant;
                        break;
                    }
                }
                Object[] constants = types[i].getEnumConstants();
                args[i] = chosen != null ? chosen : (constants.length > 0 ? constants[0] : null);
            } else {
                return null;
            }
        }
        return args;
    }
}
