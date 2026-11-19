package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.entities;

import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Entity;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Searchable;
import app.krista.extension.impl.anno.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A monday.com user.
 *
 * <p>Owner assignment needs a numeric person id, but a conversational caller says "Amit". This
 * entity is what Search Users returns so a name or email can be turned into the id a people column
 * requires.
 */
@Searchable
@Domain(id = "catEntryDomain_5fa2fc97-4b17-44cf-b98f-aa91a459a091",
        name = "Collaboration",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "bb29f50b-cfca-4958-a95f-0fa52fc5c494")
@Entity(name = "User",
        id = "localDomainEntity_6e2b48af-91d7-4c05-82f3-0db5a7c6193e",
        primaryKey = "User Id",
        supportStore = false,
        description = "A monday.com user who can be assigned as an item owner.")
public class MondayUser {

    @Searchable
    @Field.Text(name = "User Id", required = false, description = "The user's unique monday.com id.")
    public String userId;

    @Searchable
    @ToString
    @Field.Text(name = "Name", required = false, description = "The user's display name.")
    public String name;

    @Searchable
    @Field(name = "Email", type = "Email", required = false, description = "The user's email address.",
            attributes = {}, options = {})
    public String email;

    @Field.Boolean(name = "Enabled", required = false, description = "True when the account is active.")
    public Boolean enabled;

    @Field.Boolean(name = "Is Admin", required = false, description = "True for account administrators.")
    public Boolean isAdmin;

    @Field.Boolean(name = "Is Guest", required = false, description = "True for guest users.")
    public Boolean isGuest;

    @Searchable
    @Field.Text(name = "Account Name", required = false, description = "The monday.com account (workspace) name.")
    public String accountName;

    public Map<String, Object> toFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("User Id", userId);
        fieldMap.put("Name", name);
        fieldMap.put("Email", email);
        fieldMap.put("Enabled", enabled);
        fieldMap.put("Is Admin", isAdmin);
        fieldMap.put("Is Guest", isGuest);
        fieldMap.put("Account Name", accountName);
        return fieldMap;
    }
}
