package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.MondayUser;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.errorhandlers.AuthorizationExceptionHandler;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp.ExtensionResponseFactory;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp.TelemetryHelper;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayClientProvider;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayGraphQLClient;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.transform.MondayTransformer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Setup area — connectivity and health.
 *
 * <p>Test Connection makes one real, cheap authenticated call ({@code me}) rather than only
 * checking that a token exists, so a pass genuinely proves the configured credentials reach
 * monday.com.
 */
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
public class SetupArea {

    private static final Logger LOG = LoggerFactory.getLogger(SetupArea.class);
    private static final String PREFIX = "mbl_monday";

    private final MondayClientProvider clientProvider;
    private final TelemetryHelper telemetry;
    private final String invokerId;

    @Inject
    public SetupArea(MondayClientProvider clientProvider, TelemetryHelper telemetry, Invoker invoker) {
        this.clientProvider = clientProvider;
        this.telemetry = telemetry;
        this.invokerId = invoker.getInvokerId();
    }

    @CatalogRequest(
            id = "localDomainRequest_a1f03c82-7b45-4de6-9c18-0e5a7f2d6b91",
            name = "Test Connection",
            description = "Verify that the configured monday.com credentials work and report who they belong to",
            area = "Setup",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "User", type = "Entity(User)", required = false)
    @Field.Boolean(name = "Connected", required = false)
    @Field.Text(name = "Account Name", required = false)
    @Field.Text(name = "API Version", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse testConnection() {
        long start = telemetry.start(PREFIX + ".TestConnection");
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            MondayGraphQLClient client = clientProvider.forOperator();
            MondayUser user = MondayTransformer.toUser(client.me());
            JsonObject version = client.version();

            out.put("User", user.toFields());
            out.put("Connected", true);
            out.put("Account Name", user.accountName);
            out.put("API Version", MondayTransformer.str(version, "value"));
            out.put("Message", "Connected to monday.com as " + user.name + ".");
            telemetry.recordSuccess(PREFIX + ".TestConnection", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            // Operator context here — surface it as a normal result pointing at the Setup tab.
            telemetry.recordError(PREFIX + ".TestConnection", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            LOG.error("Test Connection failed for invoker {} ({}): {}",
                    invokerId, e.getErrorCode(), e.getMessage());
            telemetry.recordError(PREFIX + ".TestConnection", start, e, Map.of());
            out.put("Connected", false);
            out.putAll(e.toResponseFields());
            out.put("Message", e.toDisplayMessage());
            return ExtensionResponseFactory.ok(out);
        } catch (Exception e) {
            LOG.error("Test Connection failed unexpectedly for invoker {}", invokerId, e);
            telemetry.recordError(PREFIX + ".TestConnection", start, e, Map.of());
            out.put("Connected", false);
            out.put("Message", "Could not reach monday.com. Check the appliance's network access "
                    + "to api.monday.com and the credentials on the Setup tab.");
            return ExtensionResponseFactory.ok(out);
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_c47d9ea1-30b8-42f5-8a6c-1d9b57e0f3a4",
            name = "Health Check",
            description = "Report extension health and the monday.com API versions currently available",
            area = "Setup",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Boolean(name = "Healthy", required = false)
    @Field.Text(name = "API Version", required = false)
    @Field.Desc(name = "Available API Versions", type = "[ Text ]", required = false)
    @Field(name = "Response Time Ms", type = "Number", required = false, attributes = {}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse healthCheck() {
        long start = telemetry.start(PREFIX + ".HealthCheck");
        Map<String, Object> out = new LinkedHashMap<>();
        long began = System.currentTimeMillis();
        try {
            MondayGraphQLClient client = clientProvider.forOperator();
            JsonObject version = client.version();
            JsonArray versions = client.versions();

            List<String> available = new ArrayList<>();
            for (JsonElement element : versions) {
                if (element.isJsonObject()) {
                    String value = MondayTransformer.str(element.getAsJsonObject(), "value");
                    if (value != null) {
                        available.add(value);
                    }
                }
            }

            out.put("Healthy", true);
            out.put("API Version", MondayTransformer.str(version, "value"));
            out.put("Available API Versions", available);
            out.put("Response Time Ms", (double) (System.currentTimeMillis() - began));
            out.put("Message", "monday.com is reachable.");
            telemetry.recordSuccess(PREFIX + ".HealthCheck", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".HealthCheck", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            LOG.error("Health Check failed for invoker {} ({})", invokerId, e.getErrorCode());
            telemetry.recordError(PREFIX + ".HealthCheck", start, e, Map.of());
            out.put("Healthy", false);
            out.put("Response Time Ms", (double) (System.currentTimeMillis() - began));
            out.putAll(e.toResponseFields());
            out.put("Message", e.toDisplayMessage());
            return ExtensionResponseFactory.ok(out);
        } catch (Exception e) {
            LOG.error("Health Check failed unexpectedly for invoker {}", invokerId, e);
            telemetry.recordError(PREFIX + ".HealthCheck", start, e, Map.of());
            out.put("Healthy", false);
            out.put("Response Time Ms", (double) (System.currentTimeMillis() - began));
            out.put("Message", "monday.com could not be reached.");
            return ExtensionResponseFactory.ok(out);
        }
    }
}
