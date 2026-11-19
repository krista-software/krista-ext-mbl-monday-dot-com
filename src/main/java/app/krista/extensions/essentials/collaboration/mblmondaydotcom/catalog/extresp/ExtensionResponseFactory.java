package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.executor.RemediationActions;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds {@link ExtensionResponse} values consistently across every area. */
public final class ExtensionResponseFactory {

    private ExtensionResponseFactory() {
    }

    public static ExtensionResponse ok(Map<String, Object> values) {
        return new ExtensionResponseBuilder().success(values).build();
    }

    /** Failure envelope with an ExceptionType classification. */
    public static ExtensionResponse failure(String message, ExtensionResponse.Error.ExceptionType type) {
        ExtensionResponse.Error error =
                new ExtensionResponse.Error(message, System.currentTimeMillis(), type, "");
        return new ExtensionResponse(ExtensionResponse.Result.FAILURE, null, error,
                new RemediationActions(List.of(), null), Map.of());
    }

    /**
     * The CHANGE_SYSTEM convention: a SUCCESS envelope carrying {@code Success=false} plus the
     * structured error fields, so a workflow or agent can branch on the Success field and still see
     * the error code, context and suggested action.
     */
    public static ExtensionResponse changeFailure(MondayApiException exception, Map<String, Object> extraFields) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Success", false);
        if (extraFields != null) {
            values.putAll(extraFields);
        }
        values.putAll(exception.toResponseFields());
        values.put("Message", exception.toDisplayMessage());
        return new ExtensionResponseBuilder().success(values).build();
    }

    /** Maps the extension's failure kinds onto the platform's error classification. */
    public static ExtensionResponse.Error.ExceptionType classify(MondayApiException exception) {
        return switch (exception.getKind()) {
            case AUTHENTICATION, PERMISSION -> ExtensionResponse.Error.ExceptionType.AUTHENTICATION_ERROR;
            case VALIDATION, NOT_FOUND, UNSUPPORTED_COLUMN, AMBIGUOUS ->
                    ExtensionResponse.Error.ExceptionType.INPUT_ERROR;
            default -> ExtensionResponse.Error.ExceptionType.SYSTEM_ERROR;
        };
    }
}
