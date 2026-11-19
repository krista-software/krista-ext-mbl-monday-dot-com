package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A work item on a monday.com board.
 *
 * <p>The named fields (Status, Owners, Due Date, Priority, Description) are populated by resolving
 * the board's real columns at run time, so they are filled on any board layout. Every column the
 * board actually has — named or not — is additionally present in Column Values, which is what makes
 * custom columns usable downstream without a code change.
 */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "Board Item",
        id = "localDomainEntity_7c1d2e84-5a93-4f0b-9c67-2b8e41d0a5f3",
        primaryKey = "Item Id",
        supportStore = false,
        description = "A work item on a monday.com board, with its named fields and every column value.")
public class BoardItem {

    @Searchable
    @Field.Text(name = "Item Id", required = false, description = "The item's unique monday.com id.")
    public String itemId;

    @Searchable
    @ToString
    @Field.Text(name = "Name", required = false, description = "The item's name or title.")
    public String name;

    @Searchable
    @Field.Text(name = "Board Id", required = false, description = "Id of the board the item belongs to.")
    public String boardId;

    @Searchable
    @Field.Text(name = "Board Name", required = false, description = "Name of the board the item belongs to.")
    public String boardName;

    @Field.Text(name = "Group Id", required = false, description = "Id of the group the item sits in.")
    public String groupId;

    @Searchable
    @Field.Text(name = "Group Name", required = false, description = "Title of the group the item sits in.")
    public String groupName;

    @Field.Desc(name = "Owners", type = "[ { Id: Text, Name: Text, Kind: Text } ]", required = false,
            description = "People and teams assigned to the item, from the board's people column.")
    public List<Map<String, Object>> owners;

    @Searchable
    @Field.Text(name = "Owner Names", required = false,
            description = "Assigned owners as a readable comma-separated list.")
    public String ownerNames;

    @Searchable
    @Field.Text(name = "Status", required = false, description = "Label of the board's status column.")
    public String status;

    @Field(name = "Status Index", type = "Number", required = false,
            description = "Numeric index of the status label on the board.", attributes = {}, options = {})
    public Double statusIndex;

    @Field.Boolean(name = "Is Done", required = false,
            description = "True when monday marks the current status as a done state.")
    public Boolean isDone;

    @Searchable
    @Field.Date(name = "Due Date", required = false, includeTimeOfDay = false,
            allowPast = true, allowToday = true, allowFuture = true,
            description = "Value of the board's date column.",
            attributes = {@Attribute(name = "visualWidth", value = "S"),
                          @Attribute(name = "dateFormat", value = "'MMM DD, yyyy'")}, options = {})
    public Long dueDate;

    @Searchable
    @Field.Text(name = "Priority", required = false, description = "Label of the board's priority column.")
    public String priority;

    @Field(name = "Description", type = "Paragraph", required = false,
            description = "Value of the board's description or long-text column.",
            attributes = {}, options = {})
    public String description;

    @Field.Text(name = "State", required = false, description = "active, archived or deleted.")
    public String state;

    @Field.Text(name = "Item URL", required = false, description = "Direct link to the item in monday.com.")
    public String itemUrl;

    @Field.Date(name = "Created At", required = false, includeTimeOfDay = true,
            allowPast = true, allowToday = true, allowFuture = true,
            description = "When the item was created.",
            attributes = {@Attribute(name = "visualWidth", value = "S"),
                          @Attribute(name = "dateFormat", value = "'MMM DD, yyyy h:mm:ss a'")}, options = {})
    public Long createdAt;

    @Field.Date(name = "Updated At", required = false, includeTimeOfDay = true,
            allowPast = true, allowToday = true, allowFuture = true,
            description = "When the item was last changed.",
            attributes = {@Attribute(name = "visualWidth", value = "S"),
                          @Attribute(name = "dateFormat", value = "'MMM DD, yyyy h:mm:ss a'")}, options = {})
    public Long updatedAt;

    @Field.Desc(name = "Column Values",
            type = "[ { Id: Text, Title: Text, Type: Text, Text: Text, Value: Text } ]", required = false,
            description = "Every column on the board with its id, title, type and current value.")
    public List<Map<String, Object>> columnValues;

    @Field.Desc(name = "Updates",
            type = "[ { Id: Text, Body: Text, Creator: Text, Created: Text } ]", required = false,
            description = "Recent updates on the item; populated only when Include Updates is true.")
    public List<Map<String, Object>> updates;

    /** Insertion-ordered field map, keyed by the @Field display names. */
    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("Item Id", itemId);
        fieldMap.put("Name", name);
        fieldMap.put("Board Id", boardId);
        fieldMap.put("Board Name", boardName);
        fieldMap.put("Group Id", groupId);
        fieldMap.put("Group Name", groupName);
        fieldMap.put("Owners", owners);
        fieldMap.put("Owner Names", ownerNames);
        fieldMap.put("Status", status);
        fieldMap.put("Status Index", statusIndex);
        fieldMap.put("Is Done", isDone);
        fieldMap.put("Due Date", dueDate);
        fieldMap.put("Priority", priority);
        fieldMap.put("Description", description);
        fieldMap.put("State", state);
        fieldMap.put("Item URL", itemUrl);
        fieldMap.put("Created At", createdAt);
        fieldMap.put("Updated At", updatedAt);
        fieldMap.put("Column Values", columnValues);
        fieldMap.put("Updates", updates);
        return fieldMap;
    }
}
