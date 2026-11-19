package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed monday.com failure carrying everything a structured error response needs: an error code, a
 * user-facing message, the operation that failed, relevant context and a suggested corrective
 * action.
 *
 * <p>The message is always user-facing and actionable; full technical detail (status codes, URLs,
 * vendor payloads) belongs in the log, never here. No constructor path ever accepts a token.
 */
public class MondayApiException extends RuntimeException {

    /** Failure classes the extension distinguishes, mapped from monday's HTTP + GraphQL errors. */
    public enum Kind {
        AUTHENTICATION,
        PERMISSION,
        NOT_FOUND,
        VALIDATION,
        RATE_LIMIT,
        COMPLEXITY,
        NETWORK,
        GRAPHQL,
        UNSUPPORTED_COLUMN,
        AMBIGUOUS
    }

    private final Kind kind;
    private final String errorCode;
    private final String operation;
    private final Map<String, Object> context;
    private final String suggestedAction;

    public MondayApiException(Kind kind,
                              String errorCode,
                              String message,
                              String operation,
                              Map<String, Object> context,
                              String suggestedAction) {
        super(message);
        this.kind = kind;
        this.errorCode = errorCode;
        this.operation = operation;
        this.context = context == null ? Map.of() : new LinkedHashMap<>(context);
        this.suggestedAction = suggestedAction;
    }

    public MondayApiException(Kind kind,
                              String errorCode,
                              String message,
                              String operation,
                              Map<String, Object> context,
                              String suggestedAction,
                              Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.errorCode = errorCode;
        this.operation = operation;
        this.context = context == null ? Map.of() : new LinkedHashMap<>(context);
        this.suggestedAction = suggestedAction;
    }

    public Kind getKind() {
        return kind;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    /** True when a retry with backoff is worthwhile (rate limit, complexity budget, transport blip). */
    public boolean isRetryable() {
        return kind == Kind.RATE_LIMIT || kind == Kind.COMPLEXITY || kind == Kind.NETWORK;
    }

    /**
     * The structured error payload surfaced on a catalog response: Error Code, Error Message,
     * Operation, Context and Suggested Action.
     */
    public Map<String, Object> toResponseFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Error Code", errorCode);
        fields.put("Error Message", getMessage());
        fields.put("Operation", operation);
        fields.put("Context", contextAsText());
        fields.put("Suggested Action", suggestedAction);
        return fields;
    }

    /** Flattened, human-readable context — safe for display (never contains credentials). */
    public String contextAsText() {
        if (context.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /** A single-line, display-ready rendering used for the Message output field. */
    public String toDisplayMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(errorCode).append("] ").append(getMessage());
        String ctx = contextAsText();
        if (!ctx.isEmpty()) {
            sb.append(" (").append(ctx).append(')');
        }
        if (suggestedAction != null && !suggestedAction.isBlank()) {
            sb.append(' ').append(suggestedAction);
        }
        return sb.toString();
    }
}
