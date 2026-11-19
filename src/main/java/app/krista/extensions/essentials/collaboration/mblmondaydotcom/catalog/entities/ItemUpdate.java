package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/** An update (comment) posted on a monday.com item. */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "Item Update",
        id = "localDomainEntity_41c7b095-2e68-4ad3-bb14-6f9820d7ea5c",
        primaryKey = "Update Id",
        supportStore = false,
        description = "An update or comment posted on a monday.com item.")
public class ItemUpdate {

    @Searchable
    @Field.Text(name = "Update Id", required = false, description = "The update's unique id.")
    public String updateId;

    @Searchable
    @Field.Text(name = "Item Id", required = false, description = "Id of the item the update belongs to.")
    public String itemId;

    @ToString
    @Field(name = "Body", type = "Paragraph", required = false,
            description = "The update body as posted, including any HTML markup.",
            attributes = {}, options = {})
    public String body;

    @Searchable
    @Field(name = "Text Body", type = "Paragraph", required = false,
            description = "The update body as plain text, with markup removed.",
            attributes = {}, options = {})
    public String textBody;

    @Field.Text(name = "Creator Id", required = false, description = "Id of the user who posted it.")
    public String creatorId;

    @Searchable
    @Field.Text(name = "Creator Name", required = false, description = "Name of the user who posted it.")
    public String creatorName;

    @Field.Date(name = "Created At", required = false, includeTimeOfDay = true,
            allowPast = true, allowToday = true, allowFuture = true,
            description = "When the update was posted.",
            attributes = {@Attribute(name = "visualWidth", value = "S"),
                          @Attribute(name = "dateFormat", value = "'MMM DD, yyyy h:mm:ss a'")}, options = {})
    public Long createdAt;

    @Field.Date(name = "Updated At", required = false, includeTimeOfDay = true,
            allowPast = true, allowToday = true, allowFuture = true,
            description = "When the update was last edited.",
            attributes = {@Attribute(name = "visualWidth", value = "S"),
                          @Attribute(name = "dateFormat", value = "'MMM DD, yyyy h:mm:ss a'")}, options = {})
    public Long updatedAt;

    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("Update Id", updateId);
        fieldMap.put("Item Id", itemId);
        fieldMap.put("Body", body);
        fieldMap.put("Text Body", textBody);
        fieldMap.put("Creator Id", creatorId);
        fieldMap.put("Creator Name", creatorName);
        fieldMap.put("Created At", createdAt);
        fieldMap.put("Updated At", updatedAt);
        return fieldMap;
    }
}
