package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A board's discovered shape: its groups and its real columns.
 *
 * <p>This is the object that makes requirement §6 work — friendly field names ("Owner", "Status",
 * "Due Date", "Priority", "Description") are resolved against the board's actual columns rather than
 * assumed, so a board whose date column is {@code date4} and one whose date column is {@code due}
 * both work with no code change.
 */
public final class BoardSchema {

    /** Friendly names the extension understands, mapped to the column type they prefer. */
    public static final String FRIENDLY_OWNER = "owner";
    public static final String FRIENDLY_STATUS = "status";
    public static final String FRIENDLY_DUE_DATE = "due date";
    public static final String FRIENDLY_PRIORITY = "priority";
    public static final String FRIENDLY_DESCRIPTION = "description";
    public static final String FRIENDLY_NAME = "name";

    private final String boardId;
    private final String boardName;
    private final List<ColumnDefinition> columns;
    private final Map<String, String> groups;

    public BoardSchema(String boardId, String boardName, List<ColumnDefinition> columns, Map<String, String> groups) {
        this.boardId = boardId;
        this.boardName = boardName;
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.groups = groups == null ? Map.of() : new LinkedHashMap<>(groups);
    }

    /** Builds a schema from a monday {@code Board} JSON node (as returned by boards / boardColumns). */
    public static BoardSchema fromJson(JsonObject board) {
        String id = stringOrNull(board, "id");
        String name = stringOrNull(board, "name");
        List<ColumnDefinition> columns = new ArrayList<>();
        if (board.has("columns") && board.get("columns").isJsonArray()) {
            for (JsonElement element : board.getAsJsonArray("columns")) {
                if (element.isJsonObject()) {
                    columns.add(ColumnDefinition.fromJson(element.getAsJsonObject()));
                }
            }
        }
        Map<String, String> groups = new LinkedHashMap<>();
        if (board.has("groups") && board.get("groups").isJsonArray()) {
            JsonArray groupArray = board.getAsJsonArray("groups");
            for (JsonElement element : groupArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject group = element.getAsJsonObject();
                String groupId = stringOrNull(group, "id");
                String title = stringOrNull(group, "title");
                if (groupId != null) {
                    groups.put(groupId, title == null ? "" : title);
                }
            }
        }
        return new BoardSchema(id, name, columns, groups);
    }

    /**
     * Builds a schema from the {@code column} nodes embedded in an item's {@code column_values}.
     *
     * <p>Every item query already selects {@code column { id title type settings_str }}, so an
     * item's own payload is enough to work out which column is the owner / status / due-date column
     * on its board — no extra round-trip is needed just to name the fields on a read.
     */
    public static BoardSchema fromColumnValues(JsonArray columnValues, String boardId, String boardName) {
        List<ColumnDefinition> columns = new ArrayList<>();
        if (columnValues != null) {
            for (JsonElement element : columnValues) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject columnValue = element.getAsJsonObject();
                if (columnValue.has("column") && columnValue.get("column").isJsonObject()) {
                    columns.add(ColumnDefinition.fromJson(columnValue.getAsJsonObject("column")));
                } else {
                    // Fall back to the value node itself, which still carries id and type.
                    columns.add(ColumnDefinition.fromJson(columnValue));
                }
            }
        }
        return new BoardSchema(boardId, boardName, columns, Map.of());
    }

    /**
     * Resolves a caller-supplied field reference to a real column.
     *
     * <p>Resolution order, deliberately: an exact column id, then an exact (case-insensitive) column
     * title, then a friendly-name rule based on the column's <em>type</em>. Type-before-title matters
     * — a board may title its people column "Assigned To", and a board may have a text column
     * literally titled "Status" that is not the status column.
     *
     * @return the matching column, or empty when nothing on this board fits.
     */
    public Optional<ColumnDefinition> resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String needle = reference.trim();

        for (ColumnDefinition column : columns) {
            if (needle.equals(column.getId())) {
                return Optional.of(column);
            }
        }
        for (ColumnDefinition column : columns) {
            if (column.getTitle() != null && column.getTitle().equalsIgnoreCase(needle)) {
                return Optional.of(column);
            }
        }
        return resolveFriendly(needle.toLowerCase(Locale.ROOT));
    }

    private Optional<ColumnDefinition> resolveFriendly(String lower) {
        return switch (canonicalFriendlyName(lower)) {
            case FRIENDLY_OWNER -> byType("people", "person")
                    .or(() -> byTitleContains("owner", "assignee", "assigned"));
            case FRIENDLY_STATUS -> byTitleAndType("status", "status", "color")
                    .or(() -> byType("status", "color"));
            case FRIENDLY_DUE_DATE -> byTitleAndType("due", "date")
                    .or(() -> byType("date"))
                    .or(() -> byType("timeline"));
            case FRIENDLY_PRIORITY -> byTitleAndType("priority", "status", "color", "dropdown")
                    .or(() -> byTitleContains("priority"));
            case FRIENDLY_DESCRIPTION -> byTitleAndType("description", "long_text", "text", "long-text")
                    .or(() -> byTitleContains("description", "notes", "details"))
                    .or(() -> byType("long_text", "long-text"));
            default -> Optional.empty();
        };
    }

    /** Normalises the many ways a conversational caller may name the same field. */
    static String canonicalFriendlyName(String lower) {
        return switch (lower) {
            case "owner", "owners", "assignee", "assignees", "assigned to", "assigned",
                 "person", "people" -> FRIENDLY_OWNER;
            case "status", "state" -> FRIENDLY_STATUS;
            case "due date", "due", "duedate", "due on", "deadline", "date" -> FRIENDLY_DUE_DATE;
            case "priority", "urgency" -> FRIENDLY_PRIORITY;
            case "description", "notes", "details", "long text" -> FRIENDLY_DESCRIPTION;
            case "name", "title", "item name", "item title" -> FRIENDLY_NAME;
            default -> lower;
        };
    }

    /** True when the reference names the item's own name rather than a board column. */
    public static boolean isNameReference(String reference) {
        if (reference == null) {
            return false;
        }
        return FRIENDLY_NAME.equals(canonicalFriendlyName(reference.trim().toLowerCase(Locale.ROOT)));
    }

    private Optional<ColumnDefinition> byType(String... types) {
        for (String type : types) {
            for (ColumnDefinition column : columns) {
                if (!column.isArchived() && column.getLowerType().equals(type)) {
                    return Optional.of(column);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ColumnDefinition> byTitleAndType(String titleFragment, String... types) {
        for (String type : types) {
            for (ColumnDefinition column : columns) {
                if (column.isArchived() || !column.getLowerType().equals(type)) {
                    continue;
                }
                String title = column.getTitle();
                if (title != null && title.toLowerCase(Locale.ROOT).contains(titleFragment)) {
                    return Optional.of(column);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ColumnDefinition> byTitleContains(String... fragments) {
        for (String fragment : fragments) {
            for (ColumnDefinition column : columns) {
                if (column.isArchived() || column.getTitle() == null) {
                    continue;
                }
                if (column.getTitle().toLowerCase(Locale.ROOT).contains(fragment)) {
                    return Optional.of(column);
                }
            }
        }
        return Optional.empty();
    }

    /** Every non-archived column title — used to build "did you mean" error context. */
    public List<String> columnTitles() {
        List<String> titles = new ArrayList<>();
        for (ColumnDefinition column : columns) {
            if (!column.isArchived() && column.getTitle() != null) {
                titles.add(column.getTitle());
            }
        }
        return titles;
    }

    public boolean hasGroup(String groupId) {
        return groupId != null && groups.containsKey(groupId);
    }

    public List<String> groupIds() {
        return new ArrayList<>(groups.keySet());
    }

    public String getBoardId() {
        return boardId;
    }

    public String getBoardName() {
        return boardName;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public Map<String, String> getGroups() {
        return groups;
    }

    private static String stringOrNull(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : null;
    }
}
