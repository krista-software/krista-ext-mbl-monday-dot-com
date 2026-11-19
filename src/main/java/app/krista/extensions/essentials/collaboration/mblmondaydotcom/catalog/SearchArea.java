package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponse.Error.ExceptionType;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardColumn;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardDetails;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardGroup;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.ItemUpdate;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.MondayUser;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.errorhandlers.AuthorizationExceptionHandler;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp.ExtensionResponseFactory;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp.TelemetryHelper;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayApiException;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration.MondayClientProvider;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.service.MondayBoardService;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.service.MondayItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search area — every read operation against monday.com.
 *
 * <p>All requests are {@code tool = true} so a Krista AI agent can call them directly in a
 * conversation.
 */
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
public class SearchArea {

    private static final Logger LOG = LoggerFactory.getLogger(SearchArea.class);
    private static final String PREFIX = "mbl_monday";

    private final MondayItemService itemService;
    private final MondayBoardService boardService;
    private final MondayClientProvider clientProvider;
    private final TelemetryHelper telemetry;
    private final String invokerId;

    @Inject
    public SearchArea(MondayItemService itemService,
                      MondayBoardService boardService,
                      MondayClientProvider clientProvider,
                      TelemetryHelper telemetry,
                      Invoker invoker) {
        this.itemService = itemService;
        this.boardService = boardService;
        this.clientProvider = clientProvider;
        this.telemetry = telemetry;
        this.invokerId = invoker.getInvokerId();
    }

    // ---------------------------------------------------------------------------------------------
    // Get Items
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_5e8b1072-94cd-4a36-bf71-2c6093ad8e15",
            name = "Get Items",
            description = "Find monday.com items by id, name, owner, status, due date, group or free text",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Items", type = "[ Entity(Board Item) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Next Cursor", required = false)
    @Field.Boolean(name = "Search Truncated", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getItems(
            @Field.Text(name = "Board ID", required = false,
                    description = "Restrict the search to one board. Omit to search accessible boards.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Text(name = "Item ID", required = false,
                    description = "Return exactly this item. Takes precedence over every other filter.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field.Text(name = "Item Name", required = false,
                    description = "Full or partial item name.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String itemName,
            @Field.Text(name = "Owner", required = false,
                    description = "Filter by assigned person: a name, an email or a monday user id.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String owner,
            @Field.Text(name = "Status", required = false,
                    description = "Filter by the board's status label, for example Done.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String status,
            @Field.Date(name = "Due Date", required = false, includeTimeOfDay = false,
                    allowPast = true, allowToday = true, allowFuture = true,
                    description = "Filter by an exact due date.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Long dueDate,
            @Field.Date(name = "Due Date From", required = false, includeTimeOfDay = false,
                    allowPast = true, allowToday = true, allowFuture = true,
                    description = "Start of a due-date range.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Long dueDateFrom,
            @Field.Date(name = "Due Date To", required = false, includeTimeOfDay = false,
                    allowPast = true, allowToday = true, allowFuture = true,
                    description = "End of a due-date range.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Long dueDateTo,
            @Field.Text(name = "Group ID", required = false,
                    description = "Restrict the search to one group on the board.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String groupId,
            @Field.Text(name = "Search Text", required = false,
                    description = "Free-text search across item names.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String searchText,
            @Field(name = "Limit", type = "Number", required = false,
                    description = "Maximum items to return. Defaults to 25, capped at monday's 500.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double limit,
            @Field.Boolean(name = "Include Column Values", required = false,
                    description = "Return every column value for each item. Defaults to true.") Boolean includeColumnValues,
            @Field.Boolean(name = "Include Updates", required = false,
                    description = "Also return recent updates for each item. Defaults to false.") Boolean includeUpdates,
            @Field(name = "Max Boards To Scan", type = "Number", required = false,
                    description = "How many boards a search with no Board ID may scan. Defaults to 25.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double maxBoardsToScan,
            @Field.Text(name = "Cursor", required = false,
                    description = "Pass the Next Cursor from a previous call to fetch the following page.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String cursor) {

        long start = telemetry.start(PREFIX + ".GetItems");
        try {
            MondayItemService.SearchCriteria criteria = new MondayItemService.SearchCriteria();
            criteria.boardId = boardId;
            criteria.itemId = itemId;
            criteria.itemName = itemName;
            criteria.owner = owner;
            criteria.status = status;
            criteria.dueDate = dueDate;
            criteria.dueDateFrom = dueDateFrom;
            criteria.dueDateTo = dueDateTo;
            criteria.groupId = groupId;
            criteria.searchText = searchText;
            criteria.limit = limit;
            criteria.includeColumnValues = includeColumnValues;
            criteria.includeUpdates = includeUpdates;
            criteria.maxBoardsToScan = maxBoardsToScan;
            criteria.cursor = cursor;

            MondayItemService.SearchResult result = itemService.searchItems(criteria);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (BoardItem item : result.items) {
                rows.add(item.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Items", rows);
            out.put("Count", (double) result.count());
            out.put("Next Cursor", result.nextCursor);
            out.put("Search Truncated", result.truncated);
            out.put("Message", result.message);
            telemetry.recordSuccess(PREFIX + ".GetItems", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetItems", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetItems", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetItems", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Item Details
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_f0a6d4c3-1b58-4e92-87da-36c0bf5192e7",
            name = "Get Item Details",
            description = "Retrieve one monday.com item by id, with every column value",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Item", type = "Entity(Board Item)", required = false)
    @Field.Boolean(name = "Found", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getItemDetails(
            @Field.Text(name = "Item ID", required = true,
                    description = "The monday.com item id.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field.Boolean(name = "Include Updates", required = false,
                    description = "Also return the item's recent updates. Defaults to false.") Boolean includeUpdates) {

        long start = telemetry.start(PREFIX + ".GetItemDetails");
        try {
            BoardItem item = itemService.getItemDetails(itemId,
                    includeUpdates != null && includeUpdates);
            Map<String, Object> out = new LinkedHashMap<>();
            if (item == null) {
                out.put("Found", false);
                out.put("Message", "No monday.com item with id " + itemId
                        + " is visible to this account. Check the id, or find it by name with Get Items.");
            } else {
                out.put("Item", item.toFields());
                out.put("Found", true);
                out.put("Message", "Retrieved item " + item.itemId + ".");
            }
            telemetry.recordSuccess(PREFIX + ".GetItemDetails", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetItemDetails", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetItemDetails", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetItemDetails", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Boards
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_8d2e95b4-6f07-41ca-93be-5a7148cd0e26",
            name = "Get Boards",
            description = "List the monday.com boards this connection can access",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Boards", type = "[ Entity(Board Details) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getBoards(
            @Field.Text(name = "Board ID", required = false,
                    description = "Return only this board.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Text(name = "Name Filter", required = false,
                    description = "Keep only boards whose name contains this text.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String nameFilter,
            @Field.Text(name = "Workspace ID", required = false,
                    description = "Restrict to one workspace.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String workspaceId,
            @Field.Text(name = "State", required = false,
                    description = "all, active, archived or deleted. Defaults to active.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String state,
            @Field(name = "Limit", type = "Number", required = false,
                    description = "Maximum boards to return. Defaults to 25.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double limit,
            @Field(name = "Page", type = "Number", required = false,
                    description = "1-based page number.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double page) {

        long start = telemetry.start(PREFIX + ".GetBoards");
        try {
            List<BoardDetails> boards = boardService.getBoards(boardId, nameFilter, workspaceId, state,
                    limit == null ? null : (int) Math.round(limit),
                    page == null ? null : (int) Math.round(page));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (BoardDetails board : boards) {
                rows.add(board.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Boards", rows);
            out.put("Count", (double) rows.size());
            out.put("Message", rows.isEmpty()
                    ? "No monday.com boards matched."
                    : "Found " + rows.size() + " board(s).");
            telemetry.recordSuccess(PREFIX + ".GetBoards", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetBoards", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetBoards", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetBoards", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Board Columns
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_3b7c0e18-5da9-4f63-8102-9ec4a7d5b03f",
            name = "Get Board Columns",
            description = "Discover a board's columns, their types and the values a status column accepts",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Columns", type = "[ Entity(Board Column) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getBoardColumns(
            @Field.Text(name = "Board ID", required = true,
                    description = "The board whose columns should be returned.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Boolean(name = "Include Archived", required = false,
                    description = "Include archived columns. Defaults to false.") Boolean includeArchived) {

        long start = telemetry.start(PREFIX + ".GetBoardColumns");
        try {
            List<BoardColumn> columns = boardService.getBoardColumns(boardId,
                    includeArchived != null && includeArchived);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (BoardColumn column : columns) {
                rows.add(column.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Columns", rows);
            out.put("Count", (double) rows.size());
            out.put("Message", "Board " + boardId + " has " + rows.size() + " column(s).");
            telemetry.recordSuccess(PREFIX + ".GetBoardColumns", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetBoardColumns", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetBoardColumns", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetBoardColumns", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Board Groups
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_67e1ab30-c982-4d57-ae04-8f3b25c91d7a",
            name = "Get Board Groups",
            description = "List the groups defined on a monday.com board",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Groups", type = "[ Entity(Board Group) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getBoardGroups(
            @Field.Text(name = "Board ID", required = true,
                    description = "The board whose groups should be returned.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId) {

        long start = telemetry.start(PREFIX + ".GetBoardGroups");
        try {
            List<BoardGroup> groups = boardService.getBoardGroups(boardId);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (BoardGroup group : groups) {
                rows.add(group.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Groups", rows);
            out.put("Count", (double) rows.size());
            out.put("Message", "Board " + boardId + " has " + rows.size() + " group(s).");
            telemetry.recordSuccess(PREFIX + ".GetBoardGroups", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetBoardGroups", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetBoardGroups", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetBoardGroups", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Item Updates
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_94f5c7d2-0a31-48be-b6e9-72d08f13ac65",
            name = "Get Item Updates",
            description = "Retrieve the updates and comments posted on a monday.com item",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Updates", type = "[ Entity(Item Update) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Next Cursor", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getItemUpdates(
            @Field.Text(name = "Item ID", required = true,
                    description = "The item whose updates should be returned.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field(name = "Limit", type = "Number", required = false,
                    description = "Maximum updates to return. Defaults to 25.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double limit,
            @Field.Text(name = "Cursor", required = false,
                    description = "Pass the Next Cursor from a previous call to fetch the following page.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String cursor) {

        long start = telemetry.start(PREFIX + ".GetItemUpdates");
        try {
            MondayItemService.UpdatesResult result = itemService.getItemUpdates(itemId, limit, cursor);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ItemUpdate update : result.updates) {
                rows.add(update.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Updates", rows);
            out.put("Count", (double) rows.size());
            out.put("Next Cursor", result.nextCursor);
            out.put("Message", rows.isEmpty()
                    ? "Item " + itemId + " has no updates."
                    : "Found " + rows.size() + " update(s) on item " + itemId + ".");
            telemetry.recordSuccess(PREFIX + ".GetItemUpdates", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetItemUpdates", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetItemUpdates", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetItemUpdates", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Search Users
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_2c08e6f9-4b1d-473a-95c7-e0a6318bd24c",
            name = "Search Users",
            description = "Find monday.com users by name or email so they can be assigned as owners",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "Users", type = "[ Entity(User) ]", required = false)
    @Field(name = "Count", type = "Number", required = false,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse searchUsers(
            @Field.Text(name = "Name", required = false,
                    description = "Full or partial user name.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String name,
            @Field.Text(name = "Email", required = false,
                    description = "Exact email address.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String email,
            @Field.Text(name = "User ID", required = false,
                    description = "Exact monday.com user id.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String userId,
            @Field(name = "Limit", type = "Number", required = false,
                    description = "Maximum users to return.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Double limit) {

        long start = telemetry.start(PREFIX + ".SearchUsers");
        try {
            List<MondayUser> users = boardService.searchUsers(name, email, userId,
                    limit == null ? null : (int) Math.round(limit));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (MondayUser user : users) {
                rows.add(user.toFields());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Users", rows);
            out.put("Count", (double) rows.size());
            out.put("Message", rows.isEmpty()
                    ? "No monday.com users matched."
                    : "Found " + rows.size() + " user(s).");
            telemetry.recordSuccess(PREFIX + ".SearchUsers", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".SearchUsers", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("SearchUsers", start, e);
        } catch (Exception e) {
            return unexpectedFailure("SearchUsers", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Get Current User
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_db34a71e-8c62-4095-bf18-3e7a0c6d521b",
            name = "Get Current User",
            description = "Report which monday.com account the current caller is acting as",
            area = "Search",
            type = CatalogRequest.Type.QUERY_SYSTEM,
            tool = true)
    @Field.Desc(name = "User", type = "Entity(User)", required = false)
    @Field.Text(name = "Account Name", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse getCurrentUser() {
        long start = telemetry.start(PREFIX + ".GetCurrentUser");
        try {
            MondayUser user = boardService.currentUser();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("User", user.toFields());
            out.put("Account Name", user.accountName);
            out.put("Message", "Acting as " + user.name + " on monday.com.");
            telemetry.recordSuccess(PREFIX + ".GetCurrentUser", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".GetCurrentUser", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return queryFailure("GetCurrentUser", start, e);
        } catch (Exception e) {
            return unexpectedFailure("GetCurrentUser", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared failure handling
    // ---------------------------------------------------------------------------------------------

    private ExtensionResponse queryFailure(String operation, long start, MondayApiException e) {
        LOG.error("{} failed for invoker {} ({}): {}", operation, invokerId, e.getErrorCode(), e.getMessage());
        telemetry.recordError(PREFIX + "." + operation, start, e, Map.of());
        return ExtensionResponseFactory.failure(e.toDisplayMessage(), ExtensionResponseFactory.classify(e));
    }

    private ExtensionResponse unexpectedFailure(String operation, long start, Exception e) {
        LOG.error("{} failed unexpectedly for invoker {}", operation, invokerId, e);
        telemetry.recordError(PREFIX + "." + operation, start, e, Map.of());
        return ExtensionResponseFactory.failure(
                "monday.com request '" + operation + "' failed unexpectedly. Check the extension logs.",
                ExceptionType.SYSTEM_ERROR);
    }
}
