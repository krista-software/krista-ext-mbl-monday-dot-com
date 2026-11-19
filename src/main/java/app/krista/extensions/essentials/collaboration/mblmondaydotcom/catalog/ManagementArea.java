package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog;

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardGroup;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.BoardItem;
import app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities.ItemUpdate;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Management area — every write operation against monday.com.
 *
 * <p>These requests follow the CHANGE_SYSTEM convention: a business failure comes back inside a
 * successful envelope carrying {@code Success = false} plus the structured error fields, so a
 * workflow can branch on Success and an agent can read the suggested corrective action.
 */
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
public class ManagementArea {

    private static final Logger LOG = LoggerFactory.getLogger(ManagementArea.class);
    private static final String PREFIX = "mbl_monday";

    private final MondayItemService itemService;
    private final MondayBoardService boardService;
    private final MondayClientProvider clientProvider;
    private final TelemetryHelper telemetry;
    private final String invokerId;

    @Inject
    public ManagementArea(MondayItemService itemService,
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
    // Create Items
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_0bd7f412-ae35-4c8a-91e6-5d20c7418f93",
            name = "Create Items",
            description = "Create a monday.com item, setting owner, status, due date and any board column",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Item", type = "Entity(Board Item)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Text(name = "Item Id", required = false)
    @Field.Text(name = "Item URL", required = false)
    @Field.Date(name = "Created At", required = false, includeTimeOfDay = true,
            allowPast = true, allowToday = true, allowFuture = true,
            attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse createItems(
            @Field.Text(name = "Board ID", required = true,
                    description = "The board to create the item on.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Text(name = "Item Name", required = true,
                    description = "The new item's name or title.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String itemName,
            @Field.Text(name = "Group ID", required = false,
                    description = "Place the item in this group. Defaults to the board's first group.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String groupId,
            @Field.Text(name = "Owners", required = false,
                    description = "One or more owners as names, emails or user ids, comma separated.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String owners,
            @Field.Text(name = "Status", required = false,
                    description = "A label from the board's status column, for example Working on it.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String status,
            @Field.Date(name = "Due Date", required = false, includeTimeOfDay = false,
                    allowPast = true, allowToday = true, allowFuture = true,
                    description = "Value for the board's date column.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Long dueDate,
            @Field.Text(name = "Priority", required = false,
                    description = "A label from the board's priority column.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String priority,
            @Field(name = "Description", type = "Paragraph", required = false,
                    description = "Value for the board's description or long-text column.",
                    attributes = {}, options = {}) String description,
            @Field.Desc(name = "Additional Column Values", type = "[ { Column: Text, Value: Text } ]",
                    required = false,
                    description = "Any other column, named by its title or id, with the value to set.")
            List<Map<String, Object>> additionalColumnValues,
            @Field.Boolean(name = "Create Labels If Missing", required = false,
                    description = "Let monday add an unknown status label. Defaults to false.")
            Boolean createLabelsIfMissing) {

        long start = telemetry.start(PREFIX + ".CreateItems");
        try {
            MondayItemService.CreateCommand command = new MondayItemService.CreateCommand();
            command.boardId = boardId;
            command.itemName = itemName;
            command.groupId = groupId;
            command.owners = owners;
            command.status = status;
            command.dueDate = dueDate;
            command.priority = priority;
            command.description = description;
            command.additionalColumnValues = additionalColumnValues;
            command.createLabelsIfMissing = createLabelsIfMissing;

            BoardItem created = itemService.createItem(command);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Item", created.toFields());
            out.put("Success", true);
            out.put("Item Id", created.itemId);
            out.put("Item URL", created.itemUrl);
            out.put("Created At", created.createdAt);
            out.put("Message", "Created item '" + created.name + "' (" + created.itemId
                    + ") on board " + created.boardName + ".");
            telemetry.recordSuccess(PREFIX + ".CreateItems", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".CreateItems", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("CreateItems", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("CreateItems", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Update Item
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_7a9e2c50-d184-4b37-86f0-c35e1a97b2d8",
            name = "Update Item",
            description = "Update only the fields supplied on an existing monday.com item, leaving the rest untouched",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Item", type = "Entity(Board Item)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Desc(name = "Updated Fields", type = "[ Text ]", required = false)
    @Field.Boolean(name = "Ambiguous", required = false)
    @Field.Desc(name = "Candidates",
            type = "[ { Id: Text, Name: Text, Board: Text, Group: Text, Status: Text, Url: Text } ]",
            required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse updateItem(
            @Field.Text(name = "Item ID", required = false,
                    description = "The item to update. Used in preference to every other identifier.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field.Text(name = "Board ID", required = false,
                    description = "Narrows a name-based lookup to one board.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Text(name = "Item Name", required = false,
                    description = "Identify the item by name when no Item ID is known.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String itemName,
            @Field.Text(name = "Search Text", required = false,
                    description = "Identify the item by free text when no Item ID is known.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String searchText,
            @Field.Text(name = "New Item Name", required = false,
                    description = "Rename the item to this.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String newItemName,
            @Field.Text(name = "Owners", required = false,
                    description = "Replace the owners with these names, emails or user ids.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String owners,
            @Field.Text(name = "Status", required = false,
                    description = "Set the board's status column to this label.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String status,
            @Field.Date(name = "Due Date", required = false, includeTimeOfDay = false,
                    allowPast = true, allowToday = true, allowFuture = true,
                    description = "Set the board's date column to this date.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Long dueDate,
            @Field.Text(name = "Priority", required = false,
                    description = "Set the board's priority column to this label.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String priority,
            @Field(name = "Description", type = "Paragraph", required = false,
                    description = "Set the board's description or long-text column.",
                    attributes = {}, options = {}) String description,
            @Field.Text(name = "Group ID", required = false,
                    description = "Move the item into this group.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String groupId,
            @Field.Desc(name = "Additional Column Values", type = "[ { Column: Text, Value: Text } ]",
                    required = false,
                    description = "Any other column, named by its title or id, with the value to set.")
            List<Map<String, Object>> additionalColumnValues,
            @Field.Desc(name = "Fields To Clear", type = "[ Text ]", required = false,
                    description = "Fields to empty. Only these are cleared; omitted fields are untouched.")
            List<String> fieldsToClear,
            @Field.Boolean(name = "Create Labels If Missing", required = false,
                    description = "Let monday add an unknown status label. Defaults to false.")
            Boolean createLabelsIfMissing) {

        long start = telemetry.start(PREFIX + ".UpdateItem");
        try {
            MondayItemService.UpdateCommand command = new MondayItemService.UpdateCommand();
            command.itemId = itemId;
            command.boardId = boardId;
            command.itemName = itemName;
            command.searchText = searchText;
            command.newItemName = newItemName;
            command.owners = owners;
            command.status = status;
            command.dueDate = dueDate;
            command.priority = priority;
            command.description = description;
            command.groupId = groupId;
            command.additionalColumnValues = additionalColumnValues;
            command.fieldsToClear = fieldsToClear;
            command.createLabelsIfMissing = createLabelsIfMissing;

            MondayItemService.UpdateResult result = itemService.updateItem(command);

            Map<String, Object> out = new LinkedHashMap<>();
            if (result.item != null) {
                out.put("Item", result.item.toFields());
            }
            out.put("Success", result.success);
            out.put("Updated Fields", result.updatedFields);
            out.put("Ambiguous", result.ambiguous);
            out.put("Candidates", result.candidates);
            out.put("Message", result.message);
            telemetry.recordSuccess(PREFIX + ".UpdateItem", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".UpdateItem", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("UpdateItem", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("UpdateItem", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Move Item To Group
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_e51ca836-29b7-40df-a4c3-68b90f2e7d14",
            name = "Move Item To Group",
            description = "Move a monday.com item into a different group on its board",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Item", type = "Entity(Board Item)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse moveItemToGroup(
            @Field.Text(name = "Item ID", required = true,
                    description = "The item to move.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field.Text(name = "Group ID", required = true,
                    description = "The destination group on the item's own board.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String groupId) {

        long start = telemetry.start(PREFIX + ".MoveItemToGroup");
        try {
            BoardItem moved = itemService.moveItemToGroup(itemId, groupId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Item", moved.toFields());
            out.put("Success", true);
            out.put("Message", "Moved item " + moved.itemId + " to group " + moved.groupName + ".");
            telemetry.recordSuccess(PREFIX + ".MoveItemToGroup", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".MoveItemToGroup", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("MoveItemToGroup", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("MoveItemToGroup", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Archive Item
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_bf208d57-6e91-4a3c-b05d-7c419ae6f382",
            name = "Archive Item",
            description = "Archive a monday.com item so it leaves the active board view",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Item", type = "Entity(Board Item)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse archiveItem(
            @Field.Text(name = "Item ID", required = true,
                    description = "The item to archive.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId) {

        long start = telemetry.start(PREFIX + ".ArchiveItem");
        try {
            BoardItem archived = itemService.archiveItem(itemId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Item", archived.toFields());
            out.put("Success", true);
            out.put("Message", "archived".equalsIgnoreCase(String.valueOf(archived.state))
                    ? "Item " + archived.itemId + " is archived."
                    : "Archive requested for item " + archived.itemId + ".");
            telemetry.recordSuccess(PREFIX + ".ArchiveItem", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".ArchiveItem", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("ArchiveItem", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("ArchiveItem", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Add Item Update
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_36c8b190-fa42-4d7e-9b58-01e7d5a46c23",
            name = "Add Item Update",
            description = "Post an update or comment on a monday.com item",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Update", type = "Entity(Item Update)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse addItemUpdate(
            @Field.Text(name = "Item ID", required = true,
                    description = "The item to post the update on.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String itemId,
            @Field(name = "Body", type = "Paragraph", required = true,
                    description = "The update text. Mention people with the Mentions input, not an @ sign.",
                    attributes = {}, options = {}) String body,
            @Field.Text(name = "Parent Update ID", required = false,
                    description = "Reply to this update instead of posting a new one.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String parentUpdateId,
            @Field.Desc(name = "Mentions", type = "[ { Id: Text, Type: Text } ]", required = false,
                    description = "People, teams or boards to mention. Type defaults to User.")
            List<Map<String, Object>> mentions) {

        long start = telemetry.start(PREFIX + ".AddItemUpdate");
        try {
            ItemUpdate created = itemService.addItemUpdate(itemId, body, parentUpdateId, mentions);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Update", created.toFields());
            out.put("Success", true);
            out.put("Message", "Posted an update on item " + itemId + ".");
            telemetry.recordSuccess(PREFIX + ".AddItemUpdate", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".AddItemUpdate", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("AddItemUpdate", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("AddItemUpdate", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Create Group
    // ---------------------------------------------------------------------------------------------

    @CatalogRequest(
            id = "localDomainRequest_9142fe7b-5c03-48a6-8d91-4ba0e637c5df",
            name = "Create Group",
            description = "Create a new group on a monday.com board",
            area = "Management",
            type = CatalogRequest.Type.CHANGE_SYSTEM,
            tool = true)
    @Field.Desc(name = "Group", type = "Entity(Board Group)", required = false)
    @Field.Boolean(name = "Success", required = false)
    @Field.Text(name = "Message", required = false)
    public ExtensionResponse createGroup(
            @Field.Text(name = "Board ID", required = true,
                    description = "The board to add the group to.",
                    attributes = {@Attribute(name = "visualWidth", value = "M")}, options = {}) String boardId,
            @Field.Text(name = "Group Name", required = true,
                    description = "Title for the new group.",
                    attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String groupName,
            @Field.Text(name = "Group Color", required = false,
                    description = "Optional six-digit hex colour, for example #037f4c.",
                    attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String groupColor) {

        long start = telemetry.start(PREFIX + ".CreateGroup");
        try {
            BoardGroup created = boardService.createGroup(boardId, groupName, groupColor);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Group", created.toFields());
            out.put("Success", true);
            out.put("Message", "Created group '" + created.title + "' on board " + boardId + ".");
            telemetry.recordSuccess(PREFIX + ".CreateGroup", start, Map.of());
            return ExtensionResponseFactory.ok(out);

        } catch (MustAuthorizeException e) {
            telemetry.recordError(PREFIX + ".CreateGroup", start, e, Map.of());
            return AuthorizationExceptionHandler.handleAuthorizationException(
                    e, clientProvider.isInvokeAsUser());
        } catch (MondayApiException e) {
            return changeFailure("CreateGroup", start, e);
        } catch (Exception e) {
            return unexpectedChangeFailure("CreateGroup", start, e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared failure handling — CHANGE_SYSTEM convention
    // ---------------------------------------------------------------------------------------------

    private ExtensionResponse changeFailure(String operation, long start, MondayApiException e) {
        LOG.error("{} failed for invoker {} ({}): {}", operation, invokerId, e.getErrorCode(), e.getMessage());
        telemetry.recordError(PREFIX + "." + operation, start, e, Map.of());
        return ExtensionResponseFactory.changeFailure(e, Map.of());
    }

    private ExtensionResponse unexpectedChangeFailure(String operation, long start, Exception e) {
        LOG.error("{} failed unexpectedly for invoker {}", operation, invokerId, e);
        telemetry.recordError(PREFIX + "." + operation, start, e, Map.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("Success", false);
        out.put("Error Code", "MONDAY_UNEXPECTED_ERROR");
        out.put("Error Message", "The request failed unexpectedly.");
        out.put("Operation", operation);
        out.put("Context", "");
        out.put("Suggested Action", "Check the extension logs for the full error, then retry.");
        out.put("Message", "monday.com request '" + operation + "' failed unexpectedly.");
        return ExtensionResponseFactory.ok(out);
    }
}
