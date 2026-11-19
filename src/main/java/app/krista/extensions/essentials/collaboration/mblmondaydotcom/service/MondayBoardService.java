package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardColumn;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardDetails;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardGroup;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.MondayUser;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.BoardSchema;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.ColumnDefinition;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayClientProvider;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayGraphQLClient;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.transform.MondayTransformer;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.util.Validation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Board, group, column and user operations — the discovery half of the extension.
 *
 * <p>Everything the item operations need to know about a board's layout comes from here, which is
 * why no column id is ever hardcoded anywhere else.
 */
@Service
public class MondayBoardService {

    private static final Logger LOG = LoggerFactory.getLogger(MondayBoardService.class);

    /** The friendly names reported back on a discovered column. */
    private static final List<String> FRIENDLY_NAMES = List.of(
            BoardSchema.FRIENDLY_OWNER, BoardSchema.FRIENDLY_STATUS, BoardSchema.FRIENDLY_DUE_DATE,
            BoardSchema.FRIENDLY_PRIORITY, BoardSchema.FRIENDLY_DESCRIPTION);

    private final MondayClientProvider clientProvider;

    /** Per-call cache so one catalog request never fetches the same board's layout twice. */
    private final Map<String, BoardSchema> schemaCache = new LinkedHashMap<>();

    @Inject
    public MondayBoardService(MondayClientProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    public MondayClientProvider getClientProvider() {
        return clientProvider;
    }

    // ---------------------------------------------------------------------------------------------
    // Boards
    // ---------------------------------------------------------------------------------------------

    /**
     * Lists accessible boards.
     *
     * <p>monday's {@code boards} query has no name argument, so Name Filter is applied here,
     * case-insensitively, after the fetch.
     */
    public List<BoardDetails> getBoards(String boardId, String nameFilter, String workspaceId,
                                        String state, Integer limit, Integer page) {
        String operation = "GetBoards";
        MondayGraphQLClient client = clientProvider.forRequest();

        JsonArray boardIds = new JsonArray();
        String validatedBoardId = Validation.optionalId(boardId, "Board ID", operation);
        if (validatedBoardId != null) {
            boardIds.add(validatedBoardId);
        }
        JsonArray workspaceIds = new JsonArray();
        String validatedWorkspaceId = Validation.optionalId(workspaceId, "Workspace ID", operation);
        if (validatedWorkspaceId != null) {
            workspaceIds.add(validatedWorkspaceId);
        }

        JsonArray boards = client.boards(boardIds, limit, page, validateState(state, operation), workspaceIds);

        List<BoardDetails> results = new ArrayList<>();
        String needle = Validation.trimToNull(nameFilter);
        for (JsonElement element : boards) {
            if (!element.isJsonObject()) {
                continue;
            }
            BoardDetails details = MondayTransformer.toBoardDetails(element.getAsJsonObject());
            if (needle != null) {
                String name = details.name == null ? "" : details.name;
                if (!name.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            results.add(details);
        }
        return results;
    }

    /** Board ids ordered by recency of use — the fan-out order for a boardless search. */
    public List<String> accessibleBoardIds(int cap) {
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonArray boards = client.boards(new JsonArray(), cap, 1, "active", new JsonArray());
        List<String> ids = new ArrayList<>();
        for (JsonElement element : boards) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject board = element.getAsJsonObject();
            if (board.has("id") && !board.get("id").isJsonNull()) {
                ids.add(board.get("id").getAsString());
            }
        }
        return ids;
    }

    private static String validateState(String state, String operation) {
        String trimmed = Validation.trimToNull(state);
        if (trimmed == null) {
            return "active";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!List.of("all", "active", "archived", "deleted").contains(lower)) {
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_INVALID_STATE",
                    "'" + state + "' is not a valid board state.",
                    operation, Map.of("state", state),
                    "Use one of: all, active, archived, deleted.");
        }
        return lower;
    }

    // ---------------------------------------------------------------------------------------------
    // Columns and groups
    // ---------------------------------------------------------------------------------------------

    /**
     * Loads (and caches for this call) a board's column and group layout.
     *
     * @throws MondayApiException NOT_FOUND when the board does not exist or is not visible.
     */
    public BoardSchema loadSchema(String boardId, String operation) {
        String validated = Validation.requireId(boardId, "Board ID", operation);
        BoardSchema cached = schemaCache.get(validated);
        if (cached != null) {
            return cached;
        }
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonArray ids = new JsonArray();
        ids.add(validated);
        JsonArray boards = client.boards(ids, 1, 1, "active", new JsonArray());
        if (boards.isEmpty()) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_BOARD_NOT_FOUND",
                    "No monday.com board with id " + validated + " is visible to this account.",
                    operation, Map.of("boardId", validated),
                    "Run Get Boards to list the boards this connection can see.");
        }
        BoardSchema schema = BoardSchema.fromJson(boards.get(0).getAsJsonObject());
        schemaCache.put(validated, schema);
        return schema;
    }

    public List<BoardColumn> getBoardColumns(String boardId, boolean includeArchived) {
        String operation = "GetBoardColumns";
        BoardSchema schema = loadSchema(boardId, operation);
        List<BoardColumn> results = new ArrayList<>();
        for (ColumnDefinition column : schema.getColumns()) {
            if (column.isArchived() && !includeArchived) {
                continue;
            }
            results.add(MondayTransformer.toBoardColumn(column, schema.getBoardId(),
                    friendlyNameFor(schema, column)));
        }
        return results;
    }

    /** Reports which conversational field name resolved to this column on this board, if any. */
    static String friendlyNameFor(BoardSchema schema, ColumnDefinition column) {
        for (String friendly : FRIENDLY_NAMES) {
            Optional<ColumnDefinition> resolved = schema.resolve(friendly);
            if (resolved.isPresent() && resolved.get().getId() != null
                    && resolved.get().getId().equals(column.getId())) {
                return capitalise(friendly);
            }
        }
        return null;
    }

    private static String capitalise(String value) {
        StringBuilder sb = new StringBuilder();
        for (String word : value.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    public List<BoardGroup> getBoardGroups(String boardId) {
        String operation = "GetBoardGroups";
        String validated = Validation.requireId(boardId, "Board ID", operation);
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonArray ids = new JsonArray();
        ids.add(validated);
        JsonArray boards = client.boardGroups(ids);
        if (boards.isEmpty()) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_BOARD_NOT_FOUND",
                    "No monday.com board with id " + validated + " is visible to this account.",
                    operation, Map.of("boardId", validated),
                    "Run Get Boards to list the boards this connection can see.");
        }
        JsonObject board = boards.get(0).getAsJsonObject();
        List<BoardGroup> results = new ArrayList<>();
        if (board.has("groups") && board.get("groups").isJsonArray()) {
            for (JsonElement element : board.getAsJsonArray("groups")) {
                if (element.isJsonObject()) {
                    results.add(MondayTransformer.toBoardGroup(element.getAsJsonObject(), validated));
                }
            }
        }
        return results;
    }

    public BoardGroup createGroup(String boardId, String groupName, String groupColor) {
        String operation = "CreateGroup";
        String validatedBoardId = Validation.requireId(boardId, "Board ID", operation);
        String validatedName = Validation.requireText(groupName, "Group Name", operation);
        String colour = Validation.optionalHexColour(groupColor, "Group Color", operation);

        MondayGraphQLClient client = clientProvider.forRequest();
        JsonObject created = client.createGroup(validatedBoardId, validatedName, colour);
        LOG.info("Created a monday.com group on board {}", validatedBoardId);
        return MondayTransformer.toBoardGroup(created, validatedBoardId);
    }

    // ---------------------------------------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------------------------------------

    public List<MondayUser> searchUsers(String name, String email, String userId, Integer limit) {
        String operation = "SearchUsers";
        MondayGraphQLClient client = clientProvider.forRequest();

        JsonArray ids = new JsonArray();
        String validatedUserId = Validation.optionalId(userId, "User ID", operation);
        if (validatedUserId != null) {
            ids.add(validatedUserId);
        }
        JsonArray emails = new JsonArray();
        if (Validation.isNotBlank(email)) {
            emails.add(email.trim());
        }

        JsonArray users = client.users(ids, Validation.trimToNull(name), emails, limit);
        List<MondayUser> results = new ArrayList<>();
        for (JsonElement element : users) {
            if (element.isJsonObject()) {
                results.add(MondayTransformer.toUser(element.getAsJsonObject()));
            }
        }
        return results;
    }

    public MondayUser currentUser() {
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonObject me = client.me();
        return MondayTransformer.toUser(me);
    }

    /**
     * Resolves one or more owner references to monday person ids.
     *
     * <p>Accepts a numeric id directly, otherwise looks the person up by email when the reference
     * looks like an address and by name otherwise. A reference that matches nobody is skipped here
     * and reported by the caller, which knows the column context.
     */
    public List<String> resolvePersonIds(String ownerReferences) {
        List<String> ids = new ArrayList<>();
        for (String reference : splitReferences(ownerReferences)) {
            if (Validation.isNumericId(reference)) {
                ids.add(reference);
                continue;
            }
            List<MondayUser> matches = reference.contains("@")
                    ? searchUsers(null, reference, null, 5)
                    : searchUsers(reference, null, null, 5);
            if (matches.isEmpty()) {
                LOG.warn("No monday.com user matched an owner reference");
                continue;
            }
            if (matches.size() > 1) {
                // Prefer an exact, case-insensitive name match before falling back to the first hit.
                MondayUser exact = null;
                for (MondayUser candidate : matches) {
                    if (candidate.name != null && candidate.name.equalsIgnoreCase(reference)) {
                        exact = candidate;
                        break;
                    }
                }
                ids.add(exact != null ? exact.userId : matches.get(0).userId);
            } else {
                ids.add(matches.get(0).userId);
            }
        }
        return ids;
    }

    private static List<String> splitReferences(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null) {
            return parts;
        }
        for (String part : value.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
