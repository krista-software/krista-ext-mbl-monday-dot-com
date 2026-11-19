package app.krista.extensions.essentials.collaboration.mblmondaydotcom.integration;

import app.krista.extensions.essentials.collaboration.mblmondaydotcom.MondayAttributes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The monday.com GraphQL connector — one endpoint, one execute path, one error-mapping point.
 *
 * <p>Characteristics of this API that shape the implementation:
 * <ul>
 *   <li>Every operation is a POST to {@code https://api.monday.com/v2} with
 *       {@code {"query": ..., "variables": ...}}.</li>
 *   <li>The access token goes in {@code Authorization} <b>without</b> a {@code Bearer } prefix.</li>
 *   <li>The {@code API-Version} header pins the schema version (default {@code 2026-10}).</li>
 *   <li>Failures usually arrive as HTTP 200 with a populated {@code errors} array, so the body is
 *       inspected on every response — not only on non-2xx.</li>
 * </ul>
 *
 * <p>A fresh instance is built per request from freshly-loaded attributes; the OkHttp client itself
 * is cheap to construct and pools connections. The token is never logged.
 */
public class MondayGraphQLClient {

    private static final Logger LOG = LoggerFactory.getLogger(MondayGraphQLClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Rate limit / complexity responses are retried this many times with exponential backoff. */
    static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 1_000L;

    private final String endpoint;
    private final String accessToken;
    private final String apiVersion;
    private final OkHttpClient http;

    public MondayGraphQLClient(MondayAttributes cfg, String accessToken) {
        this.endpoint = cfg.getApiBaseUrl();
        this.accessToken = accessToken;
        this.apiVersion = cfg.getApiVersion();
        this.http = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /** Test seam: lets a unit test supply a client pointed at a stub server. */
    MondayGraphQLClient(String endpoint, String accessToken, String apiVersion, OkHttpClient http) {
        this.endpoint = endpoint;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.http = http;
    }

    // ---------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------

    public JsonObject me() {
        String query = "query { me { id name email is_admin is_guest enabled "
                + "account { id name slug } } }";
        return execute("me", query, new JsonObject()).getAsJsonObject("me");
    }

    public JsonObject version() {
        String query = "query { version { value kind display_name } }";
        return execute("version", query, new JsonObject()).getAsJsonObject("version");
    }

    public JsonArray versions() {
        String query = "query { versions { value kind display_name } }";
        JsonObject data = execute("versions", query, new JsonObject());
        return data.has("versions") && data.get("versions").isJsonArray()
                ? data.getAsJsonArray("versions") : new JsonArray();
    }

    /**
     * Board discovery. {@code order_by: used_at} puts recently-used boards first, which is what the
     * cross-board fan-out in Get Items relies on when it has to cap the number of boards scanned.
     */
    public JsonArray boards(JsonArray boardIds, Integer limit, Integer page, String state, JsonArray workspaceIds) {
        String query = "query($ids:[ID!],$limit:Int,$page:Int,$state:State,$workspace_ids:[ID]) {"
                + " boards(ids:$ids, limit:$limit, page:$page, state:$state, workspace_ids:$workspace_ids,"
                + " order_by: used_at) {"
                + " id name description state board_kind url"
                + " workspace { id name }"
                + " groups { id title color position archived deleted }"
                + " columns { id title type settings_str description width archived }"
                + " } }";
        JsonObject variables = new JsonObject();
        if (boardIds != null && !boardIds.isEmpty()) {
            variables.add("ids", boardIds);
        }
        if (limit != null) {
            variables.addProperty("limit", limit);
        }
        if (page != null) {
            variables.addProperty("page", page);
        }
        if (state != null && !state.isBlank()) {
            variables.addProperty("state", state);
        }
        if (workspaceIds != null && !workspaceIds.isEmpty()) {
            variables.add("workspace_ids", workspaceIds);
        }
        return asArray(execute("boards", query, variables), "boards");
    }

    /** Board columns only — the cheap query behind dynamic column discovery. */
    public JsonArray boardColumns(JsonArray boardIds) {
        String query = "query($ids:[ID!]) { boards(ids:$ids) { id name"
                + " columns { id title type settings_str description width archived } } }";
        JsonObject variables = new JsonObject();
        variables.add("ids", boardIds);
        return asArray(execute("boardColumns", query, variables), "boards");
    }

    /** Board groups only. */
    public JsonArray boardGroups(JsonArray boardIds) {
        String query = "query($ids:[ID!]) { boards(ids:$ids) { id name"
                + " groups { id title color position archived deleted } } }";
        JsonObject variables = new JsonObject();
        variables.add("ids", boardIds);
        return asArray(execute("boardGroups", query, variables), "boards");
    }

    /** Fetch items by id. Also the cheap board-ownership check before an item-only mutation. */
    public JsonArray items(JsonArray itemIds, boolean includeColumnValues) {
        String query = "query($ids:[ID!]) { items(ids:$ids) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + (includeColumnValues ? columnValuesSelection() : "")
                + " } }";
        JsonObject variables = new JsonObject();
        variables.add("ids", itemIds);
        return asArray(execute("items", query, variables), "items");
    }

    /** Board-scoped item search driven by an {@code ItemsQuery} rule set. */
    public JsonObject boardItemsPage(String boardId, int limit, String cursor,
                                     JsonObject queryParams, boolean includeColumnValues) {
        String query = "query($ids:[ID!],$limit:Int!,$cursor:String,$qp:ItemsQuery) {"
                + " boards(ids:$ids) { id name"
                + " items_page(limit:$limit, cursor:$cursor, query_params:$qp) {"
                + " cursor items {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + (includeColumnValues ? columnValuesSelection() : "")
                + " } } } }";
        JsonObject variables = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(boardId);
        variables.add("ids", ids);
        variables.addProperty("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            variables.addProperty("cursor", cursor);
        }
        if (queryParams != null && !queryParams.entrySet().isEmpty()) {
            variables.add("qp", queryParams);
        }
        JsonArray boards = asArray(execute("boardItemsPage", query, variables), "boards");
        if (boards.isEmpty()) {
            return emptyItemsPage();
        }
        JsonObject board = boards.get(0).getAsJsonObject();
        return board.has("items_page") && board.get("items_page").isJsonObject()
                ? board.getAsJsonObject("items_page") : emptyItemsPage();
    }

    /** Group-scoped item search — {@code items_page} exists on Group with the same arguments. */
    public JsonObject groupItemsPage(String boardId, String groupId, int limit, String cursor,
                                     JsonObject queryParams, boolean includeColumnValues) {
        String query = "query($ids:[ID!],$groupIds:[String!],$limit:Int!,$cursor:String,$qp:ItemsQuery) {"
                + " boards(ids:$ids) { id name"
                + " groups(ids:$groupIds) { id title"
                + " items_page(limit:$limit, cursor:$cursor, query_params:$qp) {"
                + " cursor items {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + (includeColumnValues ? columnValuesSelection() : "")
                + " } } } } }";
        JsonObject variables = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(boardId);
        variables.add("ids", ids);
        JsonArray groupIds = new JsonArray();
        groupIds.add(groupId);
        variables.add("groupIds", groupIds);
        variables.addProperty("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            variables.addProperty("cursor", cursor);
        }
        if (queryParams != null && !queryParams.entrySet().isEmpty()) {
            variables.add("qp", queryParams);
        }
        JsonArray boards = asArray(execute("groupItemsPage", query, variables), "boards");
        if (boards.isEmpty()) {
            return emptyItemsPage();
        }
        JsonObject board = boards.get(0).getAsJsonObject();
        JsonArray groups = board.has("groups") && board.get("groups").isJsonArray()
                ? board.getAsJsonArray("groups") : new JsonArray();
        if (groups.isEmpty()) {
            return emptyItemsPage();
        }
        JsonObject group = groups.get(0).getAsJsonObject();
        return group.has("items_page") && group.get("items_page").isJsonObject()
                ? group.getAsJsonObject("items_page") : emptyItemsPage();
    }

    /** Exact column-value match search. */
    public JsonObject itemsPageByColumnValues(String boardId, JsonArray columns, int limit, String cursor) {
        String query = "query($board_id:ID!,$columns:[ItemsPageByColumnValuesQuery!],$limit:Int!,$cursor:String) {"
                + " items_page_by_column_values(board_id:$board_id, columns:$columns, limit:$limit, cursor:$cursor) {"
                + " cursor items {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + columnValuesSelection()
                + " } } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.add("columns", columns);
        variables.addProperty("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            variables.addProperty("cursor", cursor);
        }
        JsonObject data = execute("itemsPageByColumnValues", query, variables);
        return data.has("items_page_by_column_values") && data.get("items_page_by_column_values").isJsonObject()
                ? data.getAsJsonObject("items_page_by_column_values") : emptyItemsPage();
    }

    /** Cursor pagination across any items_page result. */
    public JsonObject nextItemsPage(String cursor, int limit) {
        String query = "query($cursor:String!,$limit:Int!) {"
                + " next_items_page(cursor:$cursor, limit:$limit) {"
                + " cursor items {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + columnValuesSelection()
                + " } } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("cursor", cursor);
        variables.addProperty("limit", limit);
        JsonObject data = execute("nextItemsPage", query, variables);
        return data.has("next_items_page") && data.get("next_items_page").isJsonObject()
                ? data.getAsJsonObject("next_items_page") : emptyItemsPage();
    }

    /** Item updates (comments / activity notes). */
    public JsonObject itemUpdatesPage(String itemId, int limit, String cursor) {
        String query = "query($ids:[ID!],$limit:Int,$cursor:String) {"
                + " items(ids:$ids) { id updates_page(limit:$limit, cursor:$cursor) {"
                + " cursor updates { id body text_body created_at updated_at creator { id name } }"
                + " } } }";
        JsonObject variables = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(itemId);
        variables.add("ids", ids);
        variables.addProperty("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            variables.addProperty("cursor", cursor);
        }
        JsonArray items = asArray(execute("itemUpdatesPage", query, variables), "items");
        if (items.isEmpty()) {
            return emptyUpdatesPage();
        }
        JsonObject item = items.get(0).getAsJsonObject();
        return item.has("updates_page") && item.get("updates_page").isJsonObject()
                ? item.getAsJsonObject("updates_page") : emptyUpdatesPage();
    }

    /** User lookup — turns a friendly owner name or email into the numeric person id. */
    public JsonArray users(JsonArray ids, String name, JsonArray emails, Integer limit) {
        String query = "query($ids:[ID!],$name:String,$emails:[String!],$limit:Int) {"
                + " users(ids:$ids, name:$name, emails:$emails, limit:$limit) {"
                + " id name email enabled is_admin is_guest } }";
        JsonObject variables = new JsonObject();
        if (ids != null && !ids.isEmpty()) {
            variables.add("ids", ids);
        }
        if (name != null && !name.isBlank()) {
            variables.addProperty("name", name);
        }
        if (emails != null && !emails.isEmpty()) {
            variables.add("emails", emails);
        }
        if (limit != null) {
            variables.addProperty("limit", limit);
        }
        return asArray(execute("users", query, variables), "users");
    }

    // ---------------------------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------------------------

    public JsonObject createItem(String boardId, String itemName, String groupId,
                                 String columnValuesJson, boolean createLabelsIfMissing) {
        String query = "mutation($board_id:ID!,$item_name:String!,$group_id:String,$column_values:JSON,"
                + "$create_labels_if_missing:Boolean) {"
                + " create_item(board_id:$board_id, item_name:$item_name, group_id:$group_id,"
                + " column_values:$column_values, create_labels_if_missing:$create_labels_if_missing) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + columnValuesSelection()
                + " } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.addProperty("item_name", itemName);
        if (groupId != null && !groupId.isBlank()) {
            variables.addProperty("group_id", groupId);
        }
        if (columnValuesJson != null && !columnValuesJson.isBlank()) {
            variables.addProperty("column_values", columnValuesJson);
        }
        variables.addProperty("create_labels_if_missing", createLabelsIfMissing);
        return execute("createItem", query, variables).getAsJsonObject("create_item");
    }

    /** The multi-field update path — only the column ids present in the JSON are touched. */
    public JsonObject changeMultipleColumnValues(String boardId, String itemId,
                                                 String columnValuesJson, boolean createLabelsIfMissing) {
        String query = "mutation($board_id:ID!,$item_id:ID,$column_values:JSON!,$create_labels_if_missing:Boolean) {"
                + " change_multiple_column_values(board_id:$board_id, item_id:$item_id,"
                + " column_values:$column_values, create_labels_if_missing:$create_labels_if_missing) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + columnValuesSelection()
                + " } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.addProperty("item_id", itemId);
        variables.addProperty("column_values", columnValuesJson);
        variables.addProperty("create_labels_if_missing", createLabelsIfMissing);
        return execute("changeMultipleColumnValues", query, variables)
                .getAsJsonObject("change_multiple_column_values");
    }

    /** Single typed-column write (a JSON value for one column). */
    public JsonObject changeColumnValue(String boardId, String itemId, String columnId,
                                        String valueJson, boolean createLabelsIfMissing) {
        String query = "mutation($board_id:ID!,$item_id:ID,$column_id:String!,$value:JSON!,"
                + "$create_labels_if_missing:Boolean) {"
                + " change_column_value(board_id:$board_id, item_id:$item_id, column_id:$column_id,"
                + " value:$value, create_labels_if_missing:$create_labels_if_missing) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title }"
                + columnValuesSelection()
                + " } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.addProperty("item_id", itemId);
        variables.addProperty("column_id", columnId);
        variables.addProperty("value", valueJson);
        variables.addProperty("create_labels_if_missing", createLabelsIfMissing);
        return execute("changeColumnValue", query, variables).getAsJsonObject("change_column_value");
    }

    /**
     * The explicit-clear path: {@code value: null} empties a column. This is what distinguishes
     * "the caller asked to clear this field" from "the caller did not mention this field".
     */
    public JsonObject clearColumnValue(String boardId, String itemId, String columnId) {
        String query = "mutation($board_id:ID!,$item_id:ID,$column_id:String!,$value:String) {"
                + " change_simple_column_value(board_id:$board_id, item_id:$item_id,"
                + " column_id:$column_id, value:$value) {"
                + " id name state url updated_at } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.addProperty("item_id", itemId);
        variables.addProperty("column_id", columnId);
        variables.add("value", com.google.gson.JsonNull.INSTANCE);
        return execute("clearColumnValue", query, variables).getAsJsonObject("change_simple_column_value");
    }

    public JsonObject moveItemToGroup(String itemId, String groupId) {
        String query = "mutation($item_id:ID,$group_id:String!) {"
                + " move_item_to_group(item_id:$item_id, group_id:$group_id) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title } } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("item_id", itemId);
        variables.addProperty("group_id", groupId);
        return execute("moveItemToGroup", query, variables).getAsJsonObject("move_item_to_group");
    }

    public JsonObject archiveItem(String itemId) {
        String query = "mutation($item_id:ID) { archive_item(item_id:$item_id) {"
                + " id name state url created_at updated_at"
                + " board { id name } group { id title } } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("item_id", itemId);
        return execute("archiveItem", query, variables).getAsJsonObject("archive_item");
    }

    public JsonObject createGroup(String boardId, String groupName, String groupColor) {
        // NOTE: the deprecated `position` argument is deliberately never sent.
        String query = "mutation($board_id:ID!,$group_name:String!,$group_color:String) {"
                + " create_group(board_id:$board_id, group_name:$group_name, group_color:$group_color) {"
                + " id title color position archived deleted } }";
        JsonObject variables = new JsonObject();
        variables.addProperty("board_id", boardId);
        variables.addProperty("group_name", groupName);
        if (groupColor != null && !groupColor.isBlank()) {
            variables.addProperty("group_color", groupColor);
        }
        return execute("createGroup", query, variables).getAsJsonObject("create_group");
    }

    public JsonObject createUpdate(String itemId, String body, String parentId, JsonArray mentions) {
        String query = "mutation($item_id:ID,$body:String!,$parent_id:ID,$mentions_list:[UpdateMention]) {"
                + " create_update(item_id:$item_id, body:$body, parent_id:$parent_id,"
                + " mentions_list:$mentions_list) {"
                + " id body text_body created_at updated_at creator { id name } } }";
        JsonObject variables = new JsonObject();
        if (itemId != null && !itemId.isBlank()) {
            variables.addProperty("item_id", itemId);
        }
        variables.addProperty("body", body);
        if (parentId != null && !parentId.isBlank()) {
            variables.addProperty("parent_id", parentId);
        }
        if (mentions != null && !mentions.isEmpty()) {
            variables.add("mentions_list", mentions);
        }
        return execute("createUpdate", query, variables).getAsJsonObject("create_update");
    }

    // ---------------------------------------------------------------------------------------------
    // The single execute path
    // ---------------------------------------------------------------------------------------------

    /**
     * Runs one GraphQL operation and returns its {@code data} object, retrying throttling failures
     * with exponential backoff.
     *
     * @throws MondayApiException for every failure mode, already classified and user-facing.
     */
    public JsonObject execute(String operation, String query, JsonObject variables) {
        MondayApiException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return executeOnce(operation, query, variables);
            } catch (MondayApiException e) {
                last = e;
                if (!e.isRetryable() || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                long backoff = BASE_BACKOFF_MILLIS * (1L << (attempt - 1));
                LOG.warn("monday.com operation '{}' hit {} (attempt {}/{}), backing off {}ms",
                        operation, e.getErrorCode(), attempt, MAX_ATTEMPTS, backoff);
                sleep(backoff);
            }
        }
        throw last;
    }

    private JsonObject executeOnce(String operation, String query, JsonObject variables) {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", query);
        payload.add("variables", variables == null ? new JsonObject() : variables);

        Request request = new Request.Builder()
                .url(endpoint)
                // monday takes the raw token — NO "Bearer " prefix.
                .addHeader("Authorization", accessToken)
                .addHeader("API-Version", apiVersion)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        // Never log the payload or the Authorization header; operation name + version only.
        LOG.debug("monday.com GraphQL '{}' (apiVersion={})", operation, apiVersion);

        String body;
        int status;
        try (Response response = http.newCall(request).execute()) {
            status = response.code();
            body = response.body() != null ? response.body().string() : "";
        } catch (IOException e) {
            LOG.error("monday.com transport failure during '{}'", operation, e);
            throw MondayErrorTranslator.network(operation, e);
        }

        JsonObject parsed = parseBody(operation, status, body);

        // monday reports most failures as HTTP 200 + an errors array, so check the body first.
        if (parsed.has("errors") && parsed.get("errors").isJsonArray()) {
            MondayApiException mapped = MondayErrorTranslator.fromGraphQlErrors(
                    parsed.getAsJsonArray("errors"), parsed, operation);
            if (mapped != null) {
                LOG.error("monday.com GraphQL error during '{}' (httpStatus={}, code={}): {}",
                        operation, status, mapped.getErrorCode(), mapped.getMessage());
                throw mapped;
            }
        }

        if (status < 200 || status >= 300) {
            String vendorMessage = parsed.has("error_message") && !parsed.get("error_message").isJsonNull()
                    ? parsed.get("error_message").getAsString() : "";
            LOG.error("monday.com HTTP {} during '{}'", status, operation);
            throw MondayErrorTranslator.fromHttpStatus(status, operation, vendorMessage);
        }

        if (!parsed.has("data") || parsed.get("data").isJsonNull()) {
            throw new MondayApiException(MondayApiException.Kind.GRAPHQL,
                    "MONDAY_EMPTY_RESPONSE",
                    "monday.com returned no data for this operation.",
                    operation, java.util.Map.of("httpStatus", status),
                    "Retry the request; if it persists, verify the board and item ids.");
        }
        return parsed.getAsJsonObject("data");
    }

    private JsonObject parseBody(String operation, int status, String body) {
        if (body == null || body.isBlank()) {
            throw MondayErrorTranslator.fromHttpStatus(status, operation, "empty response body");
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new IllegalStateException("not a JSON object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            LOG.error("monday.com returned an unparseable body during '{}' (httpStatus={})", operation, status);
            throw new MondayApiException(MondayApiException.Kind.GRAPHQL,
                    "MONDAY_UNPARSEABLE_RESPONSE",
                    "monday.com returned a response that could not be read as JSON.",
                    operation, java.util.Map.of("httpStatus", status),
                    "Retry the request; if it persists, check the pinned API Version on the Setup tab.");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Shared column-value selection, including the typed inline fragments. */
    private static String columnValuesSelection() {
        return " column_values {"
                + " id type text value"
                + " column { id title type settings_str }"
                + " ... on StatusValue { label index is_done }"
                + " ... on PeopleValue { persons_and_teams { id kind } }"
                + " ... on DateValue { date time }"
                + " }";
    }

    private static JsonArray asArray(JsonObject data, String field) {
        return data.has(field) && data.get(field).isJsonArray()
                ? data.getAsJsonArray(field) : new JsonArray();
    }

    private static JsonObject emptyItemsPage() {
        JsonObject page = new JsonObject();
        page.add("cursor", com.google.gson.JsonNull.INSTANCE);
        page.add("items", new JsonArray());
        return page;
    }

    private static JsonObject emptyUpdatesPage() {
        JsonObject page = new JsonObject();
        page.add("cursor", com.google.gson.JsonNull.INSTANCE);
        page.add("updates", new JsonArray());
        return page;
    }
}
