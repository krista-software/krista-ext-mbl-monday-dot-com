package app.krista.extensions.essentials.collaboration.mblmondaydotcom.transform;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardColumn;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardDetails;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardGroup;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.ItemUpdate;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.MondayUser;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps monday.com GraphQL JSON onto the extension's typed entities.
 *
 * <p>Pure, static and dependency-free so it can be unit tested without the platform or a network.
 */
public final class MondayTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(MondayTransformer.class);

    private MondayTransformer() {
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    /** Maps one monday {@code Item} node, naming its fields from the board's real column layout. */
    public static BoardItem toBoardItem(JsonObject item) {
        BoardItem entity = new BoardItem();
        entity.itemId = str(item, "id");
        entity.name = str(item, "name");
        entity.state = str(item, "state");
        entity.itemUrl = str(item, "url");
        entity.createdAt = toEpochMillis(str(item, "created_at"));
        entity.updatedAt = toEpochMillis(str(item, "updated_at"));

        JsonObject board = obj(item, "board");
        if (board != null) {
            entity.boardId = str(board, "id");
            entity.boardName = str(board, "name");
        }
        JsonObject group = obj(item, "group");
        if (group != null) {
            entity.groupId = str(group, "id");
            entity.groupName = str(group, "title");
        }

        JsonArray columnValues = arr(item, "column_values");
        entity.columnValues = toColumnValueRows(columnValues);

        // Name the well-known fields by resolving this board's actual columns.
        BoardSchema schema = BoardSchema.fromColumnValues(columnValues, entity.boardId, entity.boardName);
        applyNamedFields(entity, columnValues, schema);
        return entity;
    }

    /** Flattens every column value into id / title / type / text / value rows. */
    static List<Map<String, Object>> toColumnValueRows(JsonArray columnValues) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (columnValues == null) {
            return rows;
        }
        for (JsonElement element : columnValues) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject columnValue = element.getAsJsonObject();
            JsonObject column = obj(columnValue, "column");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Id", str(columnValue, "id"));
            row.put("Title", column != null ? str(column, "title") : null);
            row.put("Type", str(columnValue, "type"));
            row.put("Text", str(columnValue, "text"));
            row.put("Value", rawValue(columnValue));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Fills Status / Owners / Due Date / Priority / Description from whichever columns this board
     * actually uses for them.
     */
    static void applyNamedFields(BoardItem entity, JsonArray columnValues, BoardSchema schema) {
        Optional<ColumnDefinition> statusColumn = schema.resolve(BoardSchema.FRIENDLY_STATUS);
        Optional<ColumnDefinition> ownerColumn = schema.resolve(BoardSchema.FRIENDLY_OWNER);
        Optional<ColumnDefinition> dueColumn = schema.resolve(BoardSchema.FRIENDLY_DUE_DATE);
        Optional<ColumnDefinition> priorityColumn = schema.resolve(BoardSchema.FRIENDLY_PRIORITY);
        Optional<ColumnDefinition> descriptionColumn = schema.resolve(BoardSchema.FRIENDLY_DESCRIPTION);

        // A board whose priority column IS its status column would otherwise report the same label
        // twice; prefer the status reading and leave priority empty in that case.
        boolean priorityIsStatus = statusColumn.isPresent() && priorityColumn.isPresent()
                && statusColumn.get().getId() != null
                && statusColumn.get().getId().equals(priorityColumn.get().getId());

        if (columnValues == null) {
            return;
        }
        for (JsonElement element : columnValues) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject columnValue = element.getAsJsonObject();
            String columnId = str(columnValue, "id");
            if (columnId == null) {
                continue;
            }
            if (matches(statusColumn, columnId)) {
                entity.status = firstNonBlank(str(columnValue, "label"), str(columnValue, "text"));
                entity.statusIndex = dbl(columnValue, "index");
                entity.isDone = bool(columnValue, "is_done");
            }
            if (matches(ownerColumn, columnId)) {
                entity.owners = toOwnerRows(columnValue);
                entity.ownerNames = str(columnValue, "text");
            }
            if (matches(dueColumn, columnId)) {
                String date = firstNonBlank(str(columnValue, "date"), str(columnValue, "text"));
                entity.dueDate = toEpochMillis(date);
            }
            if (!priorityIsStatus && matches(priorityColumn, columnId)) {
                entity.priority = firstNonBlank(str(columnValue, "label"), str(columnValue, "text"));
            }
            if (matches(descriptionColumn, columnId)) {
                entity.description = str(columnValue, "text");
            }
        }
    }

    private static boolean matches(Optional<ColumnDefinition> column, String columnId) {
        return column.isPresent() && columnId.equals(column.get().getId());
    }

    /** People column → one row per assigned person or team. */
    static List<Map<String, Object>> toOwnerRows(JsonObject peopleColumnValue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonArray personsAndTeams = arr(peopleColumnValue, "persons_and_teams");
        if (personsAndTeams == null) {
            return rows;
        }
        String[] names = splitDisplayNames(str(peopleColumnValue, "text"));
        int index = 0;
        for (JsonElement element : personsAndTeams) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject person = element.getAsJsonObject();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Id", str(person, "id"));
            row.put("Name", index < names.length ? names[index] : null);
            row.put("Kind", str(person, "kind"));
            rows.add(row);
            index++;
        }
        return rows;
    }

    private static String[] splitDisplayNames(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        String[] parts = text.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    /** Attaches update rows to an item (used when Include Updates is requested). */
    public static void attachUpdates(BoardItem entity, JsonArray updates) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (updates != null) {
            for (JsonElement element : updates) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject update = element.getAsJsonObject();
                JsonObject creator = obj(update, "creator");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Id", str(update, "id"));
                row.put("Body", firstNonBlank(str(update, "text_body"), str(update, "body")));
                row.put("Creator", creator != null ? str(creator, "name") : null);
                row.put("Created", str(update, "created_at"));
                rows.add(row);
            }
        }
        entity.updates = rows;
    }

    // ---------------------------------------------------------------------------------------------
    // Boards, groups, columns
    // ---------------------------------------------------------------------------------------------

    public static BoardDetails toBoardDetails(JsonObject board) {
        BoardDetails entity = new BoardDetails();
        entity.boardId = str(board, "id");
        entity.name = str(board, "name");
        entity.description = str(board, "description");
        entity.state = str(board, "state");
        entity.boardKind = str(board, "board_kind");
        entity.boardUrl = str(board, "url");

        JsonObject workspace = obj(board, "workspace");
        if (workspace != null) {
            entity.workspaceId = str(workspace, "id");
            entity.workspaceName = str(workspace, "name");
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        JsonArray groupArray = arr(board, "groups");
        if (groupArray != null) {
            for (JsonElement element : groupArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject group = element.getAsJsonObject();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Id", str(group, "id"));
                row.put("Title", str(group, "title"));
                groups.add(row);
            }
        }
        entity.groups = groups;

        List<Map<String, Object>> columns = new ArrayList<>();
        JsonArray columnArray = arr(board, "columns");
        if (columnArray != null) {
            for (JsonElement element : columnArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject column = element.getAsJsonObject();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Id", str(column, "id"));
                row.put("Title", str(column, "title"));
                row.put("Type", str(column, "type"));
                columns.add(row);
            }
        }
        entity.columns = columns;
        return entity;
    }

    public static BoardGroup toBoardGroup(JsonObject group, String boardId) {
        BoardGroup entity = new BoardGroup();
        entity.groupId = str(group, "id");
        entity.title = str(group, "title");
        entity.boardId = boardId;
        entity.color = str(group, "color");
        entity.position = str(group, "position");
        entity.archived = bool(group, "archived");
        return entity;
    }

    /** Maps a discovered column, recording which friendly name (if any) resolved to it. */
    public static BoardColumn toBoardColumn(ColumnDefinition column, String boardId, String friendlyName) {
        BoardColumn entity = new BoardColumn();
        entity.columnId = column.getId();
        entity.title = column.getTitle();
        entity.type = column.getType();
        entity.boardId = boardId;
        entity.description = column.getDescription();
        entity.archived = column.isArchived();
        entity.friendlyName = friendlyName;

        List<Map<String, Object>> labels = new ArrayList<>();
        for (Map.Entry<String, String> entry : column.getLabels().entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Index", entry.getKey());
            row.put("Label", entry.getValue());
            labels.add(row);
        }
        entity.labels = labels;
        return entity;
    }

    // ---------------------------------------------------------------------------------------------
    // Updates and users
    // ---------------------------------------------------------------------------------------------

    public static ItemUpdate toItemUpdate(JsonObject update, String itemId) {
        ItemUpdate entity = new ItemUpdate();
        entity.updateId = str(update, "id");
        entity.itemId = itemId;
        entity.body = str(update, "body");
        entity.textBody = str(update, "text_body");
        entity.createdAt = toEpochMillis(str(update, "created_at"));
        entity.updatedAt = toEpochMillis(str(update, "updated_at"));
        JsonObject creator = obj(update, "creator");
        if (creator != null) {
            entity.creatorId = str(creator, "id");
            entity.creatorName = str(creator, "name");
        }
        return entity;
    }

    public static MondayUser toUser(JsonObject user) {
        MondayUser entity = new MondayUser();
        entity.userId = str(user, "id");
        entity.name = str(user, "name");
        entity.email = str(user, "email");
        entity.enabled = bool(user, "enabled");
        entity.isAdmin = bool(user, "is_admin");
        entity.isGuest = bool(user, "is_guest");
        JsonObject account = obj(user, "account");
        if (account != null) {
            entity.accountName = str(account, "name");
        }
        return entity;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Parses the date shapes monday returns — {@code YYYY-MM-DD} from a DateValue and an ISO-8601
     * timestamp from {@code created_at} / {@code updated_at} — into epoch milliseconds.
     *
     * @return null for a blank or unparseable value; a date is never worth failing a read over.
     */
    public static Long toEpochMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            if (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(value).toInstant().toEpochMilli();
            }
            if (value.contains("T")) {
                return OffsetDateTime.parse(value + "Z").toInstant().toEpochMilli();
            }
            if (value.matches("-?\\d{10,}")) {
                return Instant.ofEpochMilli(Long.parseLong(value)).toEpochMilli();
            }
        } catch (DateTimeParseException | NumberFormatException e) {
            LOG.debug("Could not parse '{}' as a date; leaving it unset", value);
        }
        return null;
    }

    static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return (second != null && !second.isBlank()) ? second : null;
    }

    public static String str(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(field);
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    static String rawValue(JsonObject object) {
        if (object == null || !object.has("value") || object.get("value").isJsonNull()) {
            return null;
        }
        JsonElement element = object.get("value");
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    static Double dbl(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return object.get(field).getAsDouble();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Boolean bool(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return object.get(field).getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static JsonObject obj(JsonObject object, String field) {
        return object != null && object.has(field) && object.get(field).isJsonObject()
                ? object.getAsJsonObject(field) : null;
    }

    static JsonArray arr(JsonObject object, String field) {
        return object != null && object.has(field) && object.get(field).isJsonArray()
                ? object.getAsJsonArray(field) : null;
    }
}
