package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/** A group (section) on a monday.com board. */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "Board Group",
        id = "localDomainEntity_b38e7f10-c4a2-49d5-8e61-7a05fd93c2bb",
        primaryKey = "Group Id",
        supportStore = false,
        description = "A group (section) on a monday.com board, used to place and move items.")
public class BoardGroup {

    @Searchable
    @Field.Text(name = "Group Id", required = false, description = "The group's unique id on its board.")
    public String groupId;

    @Searchable
    @ToString
    @Field.Text(name = "Title", required = false, description = "The group's display title.")
    public String title;

    @Searchable
    @Field.Text(name = "Board Id", required = false, description = "Id of the board the group belongs to.")
    public String boardId;

    @Field.Text(name = "Color", required = false, description = "The group's colour in monday.com.")
    public String color;

    @Field.Text(name = "Position", required = false, description = "The group's ordering position.")
    public String position;

    @Field.Boolean(name = "Archived", required = false, description = "True when the group is archived.")
    public Boolean archived;

    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("Group Id", groupId);
        fieldMap.put("Title", title);
        fieldMap.put("Board Id", boardId);
        fieldMap.put("Color", color);
        fieldMap.put("Position", position);
        fieldMap.put("Archived", archived);
        return fieldMap;
    }
}
