package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A monday.com board, with the groups and columns discovered on it. */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "Board Details",
        id = "localDomainEntity_2f54a9b7-6d81-4c3e-ae20-9f7c85b1d46a",
        primaryKey = "Board Id",
        supportStore = false,
        description = "A monday.com board with its workspace, groups and column layout.")
public class BoardDetails {

    @Searchable
    @Field.Text(name = "Board Id", required = false, description = "The board's unique monday.com id.")
    public String boardId;

    @Searchable
    @ToString
    @Field.Text(name = "Name", required = false, description = "The board's display name.")
    public String name;

    @Field(name = "Description", type = "Paragraph", required = false,
            description = "The board's description.", attributes = {}, options = {})
    public String description;

    @Field.Text(name = "State", required = false, description = "active, archived or deleted.")
    public String state;

    @Field.Text(name = "Board Kind", required = false, description = "public, private or share.")
    public String boardKind;

    @Field.Text(name = "Board URL", required = false, description = "Direct link to the board.")
    public String boardUrl;

    @Field.Text(name = "Workspace Id", required = false, description = "Id of the containing workspace.")
    public String workspaceId;

    @Searchable
    @Field.Text(name = "Workspace Name", required = false, description = "Name of the containing workspace.")
    public String workspaceName;

    @Field.Desc(name = "Groups", type = "[ { Id: Text, Title: Text } ]", required = false,
            description = "Groups (sections) defined on the board.")
    public List<Map<String, Object>> groups;

    @Field.Desc(name = "Columns", type = "[ { Id: Text, Title: Text, Type: Text } ]", required = false,
            description = "Columns defined on the board, with their monday.com column type.")
    public List<Map<String, Object>> columns;

    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("Board Id", boardId);
        fieldMap.put("Name", name);
        fieldMap.put("Description", description);
        fieldMap.put("State", state);
        fieldMap.put("Board Kind", boardKind);
        fieldMap.put("Board URL", boardUrl);
        fieldMap.put("Workspace Id", workspaceId);
        fieldMap.put("Workspace Name", workspaceName);
        fieldMap.put("Groups", groups);
        fieldMap.put("Columns", columns);
        return fieldMap;
    }
}
