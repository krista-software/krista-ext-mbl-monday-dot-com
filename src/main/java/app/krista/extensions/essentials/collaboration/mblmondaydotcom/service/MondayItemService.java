package app.krista.extensions.essentials.collaboration.mblmondaydotcom.service;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.ItemUpdate;
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
 * Item search, retrieval, creation and update — the business heart of the extension.
 *
 * <p>Three behaviours here are worth knowing about:
 * <ul>
 *   <li><b>Boardless search fans out.</b> monday has no account-wide item search, so when no board
 *       is named the service walks the most recently used boards up to a cap and reports whether
 *       that cap truncated the result.</li>
 *   <li><b>Updates never guess.</b> If the caller identifies an item by name and more than one
 *       matches, nothing is written and the candidates are returned instead.</li>
 *   <li><b>Updates are surgical.</b> Only fields the caller actually supplied are sent; see
 *       {@link ItemWriteRequest}.</li>
 * </ul>
 */
@Service
public class MondayItemService {

    private static final Logger LOG = LoggerFactory.getLogger(MondayItemService.class);

    /** Upper bound on how many items get an extra updates round-trip in one call. */
    private static final int MAX_ITEMS_TO_ENRICH_WITH_UPDATES = 25;
    private static final int UPDATES_PER_ITEM = 10;

    private final MondayClientProvider clientProvider;
    private final MondayBoardService boardService;

    @Inject
    public MondayItemService(MondayClientProvider clientProvider, MondayBoardService boardService) {
        this.clientProvider = clientProvider;
        this.boardService = boardService;
    }

    // ---------------------------------------------------------------------------------------------
    // Inputs and results
    // ---------------------------------------------------------------------------------------------

    /** Everything Get Items accepts. Every field is optional. */
    public static final class SearchCriteria {
        public String boardId;
        public String itemId;
        public String itemName;
        public String owner;
        public String status;
        public Long dueDate;
        public Long dueDateFrom;
        public Long dueDateTo;
        public String groupId;
        public String searchText;
        public Double limit;
        public Boolean includeColumnValues;
        public Boolean includeUpdates;
        public Double maxBoardsToScan;
        public String cursor;
    }

    public static final class SearchResult {
        public final List<BoardItem> items;
        public final String nextCursor;
        public final boolean truncated;
        public final String message;

        SearchResult(List<BoardItem> items, String nextCursor, boolean truncated, String message) {
            this.items = items;
            this.nextCursor = nextCursor;
            this.truncated = truncated;
            this.message = message;
        }

        public int count() {
            return items.size();
        }
    }

    /** Everything Create Items accepts. */
    public static final class CreateCommand {
        public String boardId;
        public String itemName;
        public String groupId;
        public String owners;
        public String status;
        public Long dueDate;
        public String priority;
        public String description;
        public List<Map<String, Object>> additionalColumnValues;
        public Boolean createLabelsIfMissing;
    }

    /** Everything Update Item accepts, including the three-state fields. */
    public static final class UpdateCommand {
        public String itemId;
        public String boardId;
        public String itemName;
        public String searchText;
        public String newItemName;
        public String owners;
        public String status;
        public Long dueDate;
        public String priority;
        public String description;
        public String groupId;
        public List<Map<String, Object>> additionalColumnValues;
        public List<String> fieldsToClear;
        public Boolean createLabelsIfMissing;
    }

    public static final class UpdateResult {
        public final BoardItem item;
        public final List<String> updatedFields;
        public final boolean ambiguous;
        public final List<Map<String, Object>> candidates;
        public final String message;
        public final boolean success;

        UpdateResult(BoardItem item, List<String> updatedFields, boolean ambiguous,
                     List<Map<String, Object>> candidates, String message, boolean success) {
            this.item = item;
            this.updatedFields = updatedFields;
            this.ambiguous = ambiguous;
            this.candidates = candidates;
            this.message = message;
            this.success = success;
        }
    }

    public static final class UpdatesResult {
        public final List<ItemUpdate> updates;
        public final String nextCursor;

        UpdatesResult(List<ItemUpdate> updates, String nextCursor) {
            this.updates = updates;
            this.nextCursor = nextCursor;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Items
    // ---------------------------------------------------------------------------------------------

    public SearchResult searchItems(SearchCriteria criteria) {
        String operation = "GetItems";
        MondayGraphQLClient client = clientProvider.forRequest();

        int limit = Validation.pageSize(criteria.limit);
        boolean includeColumnValues = Validation.booleanOrDefault(criteria.includeColumnValues, true);
        boolean includeUpdates = Validation.booleanOrDefault(criteria.includeUpdates, false);

        // 1. Continuing an earlier page always wins — the cursor already encodes the query.
        if (Validation.isNotBlank(criteria.cursor)) {
            JsonObject page = client.nextItemsPage(criteria.cursor.trim(), limit);
            List<BoardItem> items = mapItems(page);
            enrichWithUpdates(client, items, includeUpdates);
            return new SearchResult(items, cursorOf(page), false, describe(items.size(), false));
        }

        // 2. An exact item id short-circuits the whole search path.
        String itemId = Validation.optionalId(criteria.itemId, "Item ID", operation);
        if (itemId != null) {
            JsonArray ids = new JsonArray();
            ids.add(itemId);
            JsonArray found = client.items(ids, includeColumnValues);
            List<BoardItem> items = mapItemArray(found);
            enrichWithUpdates(client, items, includeUpdates);
            String message = items.isEmpty()
                    ? "No monday.com item with id " + itemId + " is visible to this account."
                    : describe(items.size(), false);
            return new SearchResult(items, null, false, message);
        }

        // 3. Decide which boards to search.
        String boardId = Validation.optionalId(criteria.boardId, "Board ID", operation);
        List<String> boardIds;
        boolean truncated = false;
        if (boardId != null) {
            boardIds = List.of(boardId);
        } else {
            int cap = Validation.boardScanCap(criteria.maxBoardsToScan,
                    clientProvider.attributes().getMaxBoardsToScan());
            // Ask for one more than the cap so we can tell "exactly full" from "there were more".
            List<String> discovered = boardService.accessibleBoardIds(cap + 1);
            truncated = discovered.size() > cap;
            boardIds = truncated ? discovered.subList(0, cap) : discovered;
            LOG.info("No Board ID supplied — fanning out across {} board(s), truncated={}",
                    boardIds.size(), truncated);
        }

        if (boardIds.isEmpty()) {
            return new SearchResult(List.of(), null, false,
                    "This monday.com account has no boards visible to the signed-in user.");
        }

        // 4. Search each board, stopping once the caller's limit is satisfied.
        List<BoardItem> collected = new ArrayList<>();
        String nextCursor = null;
        for (String currentBoardId : boardIds) {
            if (collected.size() >= limit) {
                truncated = true;
                break;
            }
            int remaining = limit - collected.size();
            try {
                JsonObject page = searchOneBoard(client, currentBoardId, criteria, remaining,
                        includeColumnValues, operation);
                collected.addAll(mapItems(page));
                // A cursor is only meaningful when exactly one board was searched.
                if (boardIds.size() == 1) {
                    nextCursor = cursorOf(page);
                }
            } catch (MondayApiException e) {
                if (boardIds.size() == 1) {
                    throw e;
                }
                // One unreadable board must not sink a whole account-wide search.
                LOG.warn("Skipping board {} during fan-out: {}", currentBoardId, e.getErrorCode());
            }
        }

        enrichWithUpdates(client, collected, includeUpdates);
        return new SearchResult(collected, nextCursor, truncated, describe(collected.size(), truncated));
    }

    private JsonObject searchOneBoard(MondayGraphQLClient client, String boardId, SearchCriteria criteria,
                                      int limit, boolean includeColumnValues, String operation) {
        BoardSchema schema = boardService.loadSchema(boardId, operation);

        ItemsQueryBuilder builder = new ItemsQueryBuilder()
                .withNameContains(criteria.itemName)
                .withStatus(schema, criteria.status)
                .withDueDate(schema, ItemsQueryBuilder.toIsoDate(criteria.dueDate))
                .withDueDateRange(schema, ItemsQueryBuilder.toIsoDate(criteria.dueDateFrom),
                        ItemsQueryBuilder.toIsoDate(criteria.dueDateTo));

        if (Validation.isNotBlank(criteria.owner)) {
            builder.withOwnerIds(schema, boardService.resolvePersonIds(criteria.owner));
        }
        if (Validation.isNotBlank(criteria.searchText) && Validation.isBlank(criteria.itemName)) {
            builder.withSearchText(criteria.searchText);
        }

        JsonObject queryParams = builder.build();
        String groupId = Validation.trimToNull(criteria.groupId);
        if (groupId != null) {
            if (!schema.hasGroup(groupId)) {
                throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                        "MONDAY_GROUP_NOT_FOUND",
                        "Board '" + schema.getBoardName() + "' has no group with id '" + groupId + "'.",
                        operation, Map.of("boardId", boardId, "groupId", groupId),
                        "Run Get Board Groups for this board and use one of: "
                                + String.join(", ", schema.groupIds()) + ".");
            }
            return client.groupItemsPage(boardId, groupId, limit, null, queryParams, includeColumnValues);
        }
        return client.boardItemsPage(boardId, limit, null, queryParams, includeColumnValues);
    }

    public BoardItem getItemDetails(String itemId, boolean includeUpdates) {
        String operation = "GetItemDetails";
        String validated = Validation.requireId(itemId, "Item ID", operation);
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonArray ids = new JsonArray();
        ids.add(validated);
        JsonArray found = client.items(ids, true);
        if (found.isEmpty()) {
            return null;
        }
        BoardItem item = MondayTransformer.toBoardItem(found.get(0).getAsJsonObject());
        if (includeUpdates) {
            enrichWithUpdates(client, List.of(item), true);
        }
        return item;
    }

    public UpdatesResult getItemUpdates(String itemId, Double limit, String cursor) {
        String operation = "GetItemUpdates";
        String validated = Validation.requireId(itemId, "Item ID", operation);
        MondayGraphQLClient client = clientProvider.forRequest();
        JsonObject page = client.itemUpdatesPage(validated, Validation.pageSize(limit),
                Validation.trimToNull(cursor));

        List<ItemUpdate> updates = new ArrayList<>();
        if (page.has("updates") && page.get("updates").isJsonArray()) {
            for (JsonElement element : page.getAsJsonArray("updates")) {
                if (element.isJsonObject()) {
                    updates.add(MondayTransformer.toItemUpdate(element.getAsJsonObject(), validated));
                }
            }
        }
        return new UpdatesResult(updates, cursorOf(page));
    }

    // ---------------------------------------------------------------------------------------------
    // Create Items
    // ---------------------------------------------------------------------------------------------

    public BoardItem createItem(CreateCommand command) {
        String operation = "CreateItems";
        String boardId = Validation.requireId(command.boardId, "Board ID", operation);
        String itemName = Validation.requireText(command.itemName, "Item Name", operation);

        BoardSchema schema = boardService.loadSchema(boardId, operation);

        String groupId = Validation.trimToNull(command.groupId);
        if (groupId != null && !schema.hasGroup(groupId)) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_GROUP_NOT_FOUND",
                    "Board '" + schema.getBoardName() + "' has no group with id '" + groupId + "'.",
                    operation, Map.of("boardId", boardId, "groupId", groupId),
                    "Run Get Board Groups for this board and use one of: "
                            + String.join(", ", schema.groupIds()) + ".");
        }

        ItemWriteRequest write = new ItemWriteRequest(schema, operation, boardService::resolvePersonIds)
                .set(BoardSchema.FRIENDLY_OWNER, command.owners)
                .set(BoardSchema.FRIENDLY_STATUS, command.status)
                .set(BoardSchema.FRIENDLY_DUE_DATE, ItemsQueryBuilder.toIsoDate(command.dueDate))
                .set(BoardSchema.FRIENDLY_PRIORITY, command.priority)
                .set(BoardSchema.FRIENDLY_DESCRIPTION, command.description)
                .setAll(command.additionalColumnValues);

        // Default false: creating a label silently changes board structure for everyone.
        boolean createLabels = Validation.booleanOrDefault(command.createLabelsIfMissing, false);

        MondayGraphQLClient client = clientProvider.forRequest();
        JsonObject created = client.createItem(boardId, itemName, groupId,
                write.hasColumnValues() ? write.toColumnValuesJson(false) : null, createLabels);
        LOG.info("Created a monday.com item on board {}", boardId);
        return MondayTransformer.toBoardItem(created);
    }

    // ---------------------------------------------------------------------------------------------
    // Update Item
    // ---------------------------------------------------------------------------------------------

    public UpdateResult updateItem(UpdateCommand command) {
        String operation = "UpdateItem";
        MondayGraphQLClient client = clientProvider.forRequest();

        // 1. Identify the target. An Item ID always wins over any search attribute.
        BoardItem target;
        String explicitItemId = Validation.optionalId(command.itemId, "Item ID", operation);
        if (explicitItemId != null) {
            target = loadItem(client, explicitItemId, operation);
        } else {
            List<BoardItem> matches = findCandidates(command, operation);
            if (matches.isEmpty()) {
                return new UpdateResult(null, List.of(), false, List.of(),
                        "No monday.com item matched the information supplied, so nothing was changed. "
                                + "Try an Item ID, or widen the name or search text.", false);
            }
            if (matches.size() > 1) {
                // Never pick one arbitrarily — hand the choice back to the caller.
                LOG.info("Update matched {} items; returning candidates without writing", matches.size());
                return new UpdateResult(null, List.of(), true, toCandidateRows(matches),
                        matches.size() + " items matched. Nothing was changed — re-run Update Item "
                                + "with the Item ID of the one you mean.", false);
            }
            target = matches.get(0);
        }

        if (target.boardId == null) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_ITEM_BOARD_UNKNOWN",
                    "Could not determine which board item " + target.itemId + " belongs to.",
                    operation, Map.of("itemId", String.valueOf(target.itemId)),
                    "Confirm the item still exists and the signed-in user can see its board.");
        }

        // 2. Build only the changes the caller actually asked for.
        BoardSchema schema = boardService.loadSchema(target.boardId, operation);
        ItemWriteRequest write = new ItemWriteRequest(schema, operation, boardService::resolvePersonIds)
                .set(BoardSchema.FRIENDLY_NAME, command.newItemName)
                .set(BoardSchema.FRIENDLY_OWNER, command.owners)
                .set(BoardSchema.FRIENDLY_STATUS, command.status)
                .set(BoardSchema.FRIENDLY_DUE_DATE, ItemsQueryBuilder.toIsoDate(command.dueDate))
                .set(BoardSchema.FRIENDLY_PRIORITY, command.priority)
                .set(BoardSchema.FRIENDLY_DESCRIPTION, command.description)
                .setAll(command.additionalColumnValues);

        if (command.fieldsToClear != null) {
            for (String field : command.fieldsToClear) {
                write.clear(field);
            }
        }

        String targetGroupId = Validation.trimToNull(command.groupId);
        boolean movingGroup = targetGroupId != null && !targetGroupId.equals(target.groupId);

        if (write.isEmpty() && !movingGroup) {
            return new UpdateResult(target, List.of(), false, List.of(),
                    "No changes were supplied, so item " + target.itemId + " was left untouched.", true);
        }

        boolean createLabels = Validation.booleanOrDefault(command.createLabelsIfMissing, false);
        List<String> updatedFields = new ArrayList<>(write.getUpdatedFields());

        // 3. Write. One multi-column call covers every supplied value, including a rename.
        JsonObject updated = null;
        if (write.hasColumnValues() || write.getNewName() != null) {
            updated = client.changeMultipleColumnValues(target.boardId, target.itemId,
                    write.toColumnValuesJson(true), createLabels);
        }

        // 4. Explicit clears go through the simple-value mutation with a null value.
        for (String columnId : write.getClearColumnIds()) {
            updated = client.clearColumnValue(target.boardId, target.itemId, columnId);
        }

        // 5. A group change is a separate mutation; it takes no board id, so the board was already
        //    confirmed above.
        if (movingGroup) {
            if (!schema.hasGroup(targetGroupId)) {
                throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                        "MONDAY_GROUP_NOT_FOUND",
                        "Board '" + schema.getBoardName() + "' has no group with id '" + targetGroupId + "'.",
                        operation, Map.of("boardId", target.boardId, "groupId", targetGroupId),
                        "Run Get Board Groups for this board and use one of: "
                                + String.join(", ", schema.groupIds()) + ".");
            }
            updated = client.moveItemToGroup(target.itemId, targetGroupId);
            updatedFields.add("Group");
        }

        // 6. Re-read so the response reflects every change, including ones made by monday itself.
        BoardItem refreshed = loadItem(client, target.itemId, operation);
        BoardItem result = refreshed != null ? refreshed
                : (updated != null ? MondayTransformer.toBoardItem(updated) : target);

        LOG.info("Updated monday.com item {} ({} field(s))", target.itemId, updatedFields.size());
        return new UpdateResult(result, updatedFields, false, List.of(),
                "Updated " + String.join(", ", updatedFields) + " on item " + target.itemId + ".", true);
    }

    /** Finds the items that match the caller's identifying attributes. */
    private List<BoardItem> findCandidates(UpdateCommand command, String operation) {
        String nameOrText = Validation.isNotBlank(command.itemName)
                ? command.itemName : command.searchText;
        if (Validation.isBlank(nameOrText)) {
            throw new MondayApiException(MondayApiException.Kind.VALIDATION,
                    "MONDAY_NO_ITEM_IDENTIFIER",
                    "No way to identify the item was supplied.",
                    operation, Map.of(),
                    "Supply an Item ID, or an Item Name / Search Text to look one up.");
        }

        SearchCriteria criteria = new SearchCriteria();
        criteria.boardId = command.boardId;
        criteria.itemName = command.itemName;
        criteria.searchText = command.searchText;
        criteria.limit = 50.0;
        criteria.includeColumnValues = true;
        return searchItems(criteria).items;
    }

    private BoardItem loadItem(MondayGraphQLClient client, String itemId, String operation) {
        JsonArray ids = new JsonArray();
        ids.add(itemId);
        JsonArray found = client.items(ids, true);
        if (found.isEmpty()) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_ITEM_NOT_FOUND",
                    "No monday.com item with id " + itemId + " is visible to this account.",
                    operation, Map.of("itemId", itemId),
                    "Check the id, or use Get Items to find the item by name first.");
        }
        return MondayTransformer.toBoardItem(found.get(0).getAsJsonObject());
    }

    static List<Map<String, Object>> toCandidateRows(List<BoardItem> matches) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BoardItem item : matches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Id", item.itemId);
            row.put("Name", item.name);
            row.put("Board", item.boardName);
            row.put("Group", item.groupName);
            row.put("Status", item.status);
            row.put("Url", item.itemUrl);
            rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------------------------------------
    // Item-only mutations (these take no board id, so the item's board is confirmed first)
    // ---------------------------------------------------------------------------------------------

    public BoardItem moveItemToGroup(String itemId, String groupId) {
        String operation = "MoveItemToGroup";
        String validatedItemId = Validation.requireId(itemId, "Item ID", operation);
        String validatedGroupId = Validation.requireText(groupId, "Group ID", operation);

        MondayGraphQLClient client = clientProvider.forRequest();
        BoardItem current = loadItem(client, validatedItemId, operation);

        BoardSchema schema = boardService.loadSchema(current.boardId, operation);
        if (!schema.hasGroup(validatedGroupId)) {
            throw new MondayApiException(MondayApiException.Kind.NOT_FOUND,
                    "MONDAY_GROUP_NOT_FOUND",
                    "Board '" + schema.getBoardName() + "' has no group with id '" + validatedGroupId + "'.",
                    operation, Map.of("boardId", current.boardId, "groupId", validatedGroupId),
                    "Run Get Board Groups for this board and use one of: "
                            + String.join(", ", schema.groupIds()) + ".");
        }

        JsonObject moved = client.moveItemToGroup(validatedItemId, validatedGroupId);
        LOG.info("Moved monday.com item {} to group {}", validatedItemId, validatedGroupId);
        return MondayTransformer.toBoardItem(moved);
    }

    public BoardItem archiveItem(String itemId) {
        String operation = "ArchiveItem";
        String validated = Validation.requireId(itemId, "Item ID", operation);
        MondayGraphQLClient client = clientProvider.forRequest();

        BoardItem current = loadItem(client, validated, operation);
        if ("archived".equalsIgnoreCase(String.valueOf(current.state))) {
            LOG.info("monday.com item {} is already archived", validated);
            return current;
        }
        JsonObject archived = client.archiveItem(validated);
        LOG.info("Archived monday.com item {}", validated);
        return MondayTransformer.toBoardItem(archived);
    }

    public ItemUpdate addItemUpdate(String itemId, String body, String parentUpdateId,
                                    List<Map<String, Object>> mentions) {
        String operation = "AddItemUpdate";
        String validatedItemId = Validation.requireId(itemId, "Item ID", operation);
        String validatedBody = Validation.requireText(body, "Body", operation);

        JsonArray mentionsList = new JsonArray();
        if (mentions != null) {
            for (Map<String, Object> mention : mentions) {
                if (mention == null) {
                    continue;
                }
                Object id = mention.get("Id") != null ? mention.get("Id") : mention.get("id");
                Object type = mention.get("Type") != null ? mention.get("Type") : mention.get("type");
                if (id == null) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", String.valueOf(id));
                entry.addProperty("type", type == null ? "User" : String.valueOf(type));
                mentionsList.add(entry);
            }
        }

        MondayGraphQLClient client = clientProvider.forRequest();
        // Confirms the item exists (and is visible) before posting — create_update takes no board id.
        loadItem(client, validatedItemId, operation);

        JsonObject created = client.createUpdate(validatedItemId, validatedBody,
                Validation.trimToNull(parentUpdateId), mentionsList);
        LOG.info("Posted an update on monday.com item {}", validatedItemId);
        return MondayTransformer.toItemUpdate(created, validatedItemId);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void enrichWithUpdates(MondayGraphQLClient client, List<BoardItem> items, boolean includeUpdates) {
        if (!includeUpdates || items.isEmpty()) {
            return;
        }
        int enriched = 0;
        for (BoardItem item : items) {
            if (enriched >= MAX_ITEMS_TO_ENRICH_WITH_UPDATES) {
                break;
            }
            try {
                JsonObject page = client.itemUpdatesPage(item.itemId, UPDATES_PER_ITEM, null);
                MondayTransformer.attachUpdates(item,
                        page.has("updates") && page.get("updates").isJsonArray()
                                ? page.getAsJsonArray("updates") : null);
            } catch (MondayApiException e) {
                // Updates are supplementary; a failure here must not lose the items themselves.
                LOG.warn("Could not load updates for item {}: {}", item.itemId, e.getErrorCode());
            }
            enriched++;
        }
    }

    private static List<BoardItem> mapItems(JsonObject itemsPage) {
        if (itemsPage == null || !itemsPage.has("items") || !itemsPage.get("items").isJsonArray()) {
            return new ArrayList<>();
        }
        return mapItemArray(itemsPage.getAsJsonArray("items"));
    }

    private static List<BoardItem> mapItemArray(JsonArray items) {
        List<BoardItem> results = new ArrayList<>();
        for (JsonElement element : items) {
            if (element.isJsonObject()) {
                results.add(MondayTransformer.toBoardItem(element.getAsJsonObject()));
            }
        }
        return results;
    }

    private static String cursorOf(JsonObject page) {
        if (page == null || !page.has("cursor") || page.get("cursor").isJsonNull()) {
            return null;
        }
        String cursor = page.get("cursor").getAsString();
        return cursor.isBlank() ? null : cursor;
    }

    /** The human-readable summary every search returns, including the empty case. */
    static String describe(int count, boolean truncated) {
        if (count == 0) {
            return "No monday.com items matched the search criteria.";
        }
        String base = count == 1 ? "Found 1 matching item." : "Found " + count + " matching items.";
        return truncated
                ? base + " The search stopped at the board scan cap, so there may be more — "
                        + "narrow it with a Board ID or raise Max Boards To Scan."
                : base;
    }

    /** Exposed for the areas' friendly-name reporting. */
    public Optional<ColumnDefinition> resolveColumn(String boardId, String reference, String operation) {
        return boardService.loadSchema(boardId, operation).resolve(reference);
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
