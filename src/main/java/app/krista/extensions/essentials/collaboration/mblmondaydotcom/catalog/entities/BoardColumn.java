package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A column definition on a monday.com board.
 *
 * <p>This is what makes a board's layout discoverable rather than assumed. Friendly Name reports
 * which conversational field name (Owner, Status, Due Date, Priority, Description) this extension
 * resolved to this column on this board, and Labels lists the values a status or dropdown column
 * will accept — so a caller can be told what is valid instead of guessing.
 */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "Board Column",
        id = "localDomainEntity_9ad4c361-80fe-4b27-93da-5c6e2178b40f",
        primaryKey = "Column Id",
        supportStore = false,
        description = "A column on a monday.com board, with its type and permitted label values.")
public class BoardColumn {

    @Searchable
    @Field.Text(name = "Column Id", required = false, description = "The column's unique id on its board.")
    public String columnId;

    @Searchable
    @ToString
    @Field.Text(name = "Title", required = false, description = "The column's display title.")
    public String title;

    @Searchable
    @Field.Text(name = "Type", required = false,
            description = "monday.com column type, for example people, status, date or long_text.")
    public String type;

    @Searchable
    @Field.Text(name = "Board Id", required = false, description = "Id of the board the column belongs to.")
    public String boardId;

    @Field.Text(name = "Description", required = false, description = "The column's description, if set.")
    public String description;

    @Field.Boolean(name = "Archived", required = false, description = "True when the column is archived.")
    public Boolean archived;

    @Field.Text(name = "Friendly Name", required = false,
            description = "The conversational field name this column answers to on this board "
                    + "(Owner, Status, Due Date, Priority or Description), when it is one of them.")
    public String friendlyName;

    @Field.Desc(name = "Labels", type = "[ { Index: Text, Label: Text } ]", required = false,
            description = "Permitted values for a status or dropdown column; empty for other types.")
    public List<Map<String, Object>> labels;

    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("Column Id", columnId);
        fieldMap.put("Title", title);
        fieldMap.put("Type", type);
        fieldMap.put("Board Id", boardId);
        fieldMap.put("Description", description);
        fieldMap.put("Archived", archived);
        fieldMap.put("Friendly Name", friendlyName);
        fieldMap.put("Labels", labels);
        return fieldMap;
    }
}
