# MBL Monday Dot Com — Krista Extension Implementation Plan

## Summary

| | |
|---|---|
| **Extension title** | MBL Monday Dot Com |
| **Module / slug** | `mbl-monday-dot-com` |
| **Version** | 1.0.0 |
| **Java package** | `app.krista.extensions.essentials.collaboration.mblmondaydotcom` |
| **Ecosystem / Domain** | Essentials / Collaboration |
| **Target system** | monday.com GraphQL API — `POST https://api.monday.com/v2` |
| **API version** | Pinned via an `API-Version` header, Setup-tab field, default `2026-10` (current stable) |
| **Auth pattern** | **A** (primary) + **C**, admin-selectable — see [Authentication](#authentication) |
| **Areas** | Setup (2 requests), Search (8), Management (6) — **16 catalog requests** |
| **Entities** | 6 — Board Item, Board Details, Board Group, Board Column, Item Update, User |
| **API coverage** | **20 of 20** operations in `api-model.json` are exercised (see [Coverage](#api-coverage)) |

Everything below is built fresh. The author reviewed all 18 overlapping catalog entries in
Essentials/Collaboration and marked every one **"new"** — see [Domain reuse](#domain-reuse).

---

## Authentication

The wizard recorded `api-key,oauth2-client-credentials`. That conflicted with section 2 of the brief
(which asks for the authorization-code flow) and with the API reference (monday documents no
client-credentials grant). You resolved it:

> *"API key AND interactive OAuth 2.0 authorization-code — admin picks the mode on the Setup tab."*

So the Setup tab carries a `@Field.PickOne(name = "Auth Type", values = {"OAuth", "ApiKey"})`,
following the servicenow Oauth/BasicAuth precedent in
`krista-extension-scaffolding/references/authentication-patterns.md`. **Both** mechanisms ship:

| Mode | Krista pattern | Setup fields | Pieces built |
|---|---|---|---|
| **ApiKey** | **A** (primary — listed first) | `API Token` (secured) | token read from attributes; `Authorization: <token>` header |
| **OAuth** | **C** | `Client ID` (secured), `Client Secret` (secured), `Redirect URL`, `Scopes` | `@InvokerRequest(AUTHENTICATOR)` → `RequestAuthenticator`, `getMustAuthorizeResponse`, JAX-RS `Application` + `GET /callback`, `@Service` token store over `KeyValueStore`, `AttributeStore`, scribejava-apis 8.3.3 |

Key details, all taken from the supplied reference (not invented):

- **`Authorization` header carries the raw token with NO `Bearer ` prefix** — true for both modes.
- Authorize `https://auth.monday.com/oauth2/authorize`, token `POST https://auth.monday.com/oauth2/token`.
- **Scopes** (your answer): `me:read`, `users:read`, `boards:read`, `boards:write`, `updates:read`,
  `updates:write`. Exposed as an editable Setup field so a differently-registered app still works.
- **`jaxrsId = "mondayextn"`** (the value the existing monday extension already uses), and every
  callback/tab URL is written as `rest/mondayextn/...` — the reference calls out that a mismatch here
  404s silently.
- **Per-user dispatch is implemented, not assumed.** Pattern C has the same three-way split as
  Pattern D (operator / real end user / MCP agent attributed to a user). Every catalog request routes
  `MustAuthorizeException` through a shared handler that **rethrows** so the platform can prompt the
  end user to sign in — swallowing it would silently break per-user and MCP sign-in.
- **Token refresh is defensive.** The reference says monday tokens are believed not to expire and may
  return no refresh token, but flags this unverified. The store persists a `refresh_token` when one
  comes back and uses it on 401; when none exists, a 401 re-drives authorization instead.
- Secrets are never logged, never returned from a credentials endpoint in plaintext, and never appear
  in any catalog input or output.

---

## Area: Setup

| Request | Type | Inputs | Output | External endpoint | Reuse |
|---|---|---|---|---|---|
| **Test Connection** | QUERY_SYSTEM | — | `Entity(User)` + Connected, Account Name, API Version, Message | `query { me { … } version { … } }` | new |
| **Health Check** | CHANGE_SYSTEM | — | Healthy, API Version, Available API Versions, Response Time Ms | `query { version versions }` | new |

## Area: Search

| Request | Type | Inputs | Output | External endpoint | Reuse |
|---|---|---|---|---|---|
| **Get Items** | QUERY_SYSTEM | Board ID, Item ID, Item Name, Owner, Status, Due Date, Due Date From, Due Date To, Group ID, Search Text, Limit, Include Column Values, Include Updates, Max Boards To Scan, Cursor *(all optional)* | `[ Entity(Board Item) ]` + Count, Next Cursor, Search Truncated, Message | `items(ids:)` / `boards.items_page(query_params:)` / `groups.items_page` / `items_page_by_column_values` / `next_items_page` / `boards(order_by: used_at)` | new |
| **Get Item Details** | QUERY_SYSTEM | **Item ID**, Include Updates | `Entity(Board Item)` + Found, Message | `items(ids:)` | new |
| **Get Boards** | QUERY_SYSTEM | Board ID, Name Filter, Workspace ID, State, Limit, Page | `[ Entity(Board Details) ]` + Count | `boards(…)` | new |
| **Get Board Columns** | QUERY_SYSTEM | **Board ID**, Include Archived | `[ Entity(Board Column) ]` + Count | `boards(ids:){ columns }` | new |
| **Get Board Groups** | QUERY_SYSTEM | **Board ID** | `[ Entity(Board Group) ]` + Count | `boards(ids:){ groups }` | new |
| **Get Item Updates** | QUERY_SYSTEM | **Item ID**, Limit, Cursor | `[ Entity(Item Update) ]` + Count, Next Cursor | `items(ids:){ updates_page }` | new |
| **Search Users** | QUERY_SYSTEM | Name, Email, User ID, Limit | `[ Entity(User) ]` + Count | `users(…)` | new |
| **Get Current User** | QUERY_SYSTEM | — | `Entity(User)` + Account Name | `me` | new |

## Area: Management

| Request | Type | Inputs | Output | External endpoint | Reuse |
|---|---|---|---|---|---|
| **Create Items** | CHANGE_SYSTEM | **Board ID**, **Item Name**, Group ID, Owners, Status, Due Date, Priority, Description, Additional Column Values, Create Labels If Missing | `Entity(Board Item)` + Success, Item Id, Item URL, Created At, Message | `create_item` (+ column discovery, `users` for owners) | new |
| **Update Item** | CHANGE_SYSTEM | Item ID, Board ID, Item Name, Search Text, New Item Name, Owners, Status, Due Date, Priority, Description, Group ID, Additional Column Values, Fields To Clear, Create Labels If Missing | `Entity(Board Item)` + Success, Updated Fields, Ambiguous, Candidates, Message | identify via `items`/`items_page`, write via `change_multiple_column_values`, `change_column_value`, `change_simple_column_value`, `move_item_to_group` | new |
| **Move Item To Group** | CHANGE_SYSTEM | **Item ID**, **Group ID** | `Entity(Board Item)` + Success, Message | `items(ids:){ board { id } }` → `move_item_to_group` | new |
| **Archive Item** | CHANGE_SYSTEM | **Item ID** | `Entity(Board Item)` + Success, Message | `archive_item` | new |
| **Add Item Update** | CHANGE_SYSTEM | **Item ID**, **Body**, Parent Update ID, Mentions | `Entity(Item Update)` + Success, Message | `create_update` | new |
| **Create Group** | CHANGE_SYSTEM | **Board ID**, **Group Name**, Group Color | `Entity(Board Group)` + Success, Message | `create_group` | new |

---

## How the three headline behaviours work

**Dynamic column discovery (brief §6).** No column id is ever hardcoded. A cached-per-board
`ColumnMap` is built from `boards(ids:){ columns { id title type settings_str } }` and maps friendly
names → real column ids by **type first, title second**: Owner → the `people` column, Status →
`status`, Due Date → `date`, Priority → a `status`/`dropdown` column titled Priority, Description →
`long_text`/`text`. A board whose due-date column is `date4` and another whose is `due` both work
untouched. Values are then converted per column type (`{"personsAndTeams":[…]}`, `{"label":"Done"}`,
`{"date":"YYYY-MM-DD"}`, …) and status/dropdown labels are validated against `settings_str` *before*
the write, so an invalid label fails with the valid labels listed instead of a raw GraphQL error.

**Partial-update semantics (brief §5).** Three states are distinguished, which is why `Fields To
Clear` exists as a separate input: a field **not supplied** never appears in the `column_values`
payload; a field **supplied** is written; a field **named in Fields To Clear** is emptied via
`change_simple_column_value` with a null value. *"Change the status of item 12345 to Done"* therefore
sends exactly one column. Supplying the same field as both a value and a clear is rejected as
contradictory.

**Ambiguity (brief §5, acceptance criteria).** Item ID always wins. Otherwise the item is resolved by
name/search text; exactly one match proceeds, several matches return `Ambiguous=true` with
`Candidates` populated and **`Success=false` and no write performed**, and zero matches returns a
not-found message. The agent presents the candidates and asks the user to pick.

**Boardless search (your answer).** `Get Items` with no Board ID fans out across accessible boards
ordered by `used_at`, capped by `Max Boards To Scan` (default **25**), setting `Search Truncated=true`
when the cap bit — monday has no account-wide item search, so this is a deliberate fan-out.

---

## Error handling

A single `MondayErrorTranslator` maps failures to structured responses carrying **Error Code, Error
Message, Operation, Context and Suggested Action**. It inspects the GraphQL `errors` array **on HTTP
200** as well as HTTP status codes, because monday returns most failures as a 200. Covered:
authentication failure, expired/invalid token (→ re-authorize in OAuth mode), insufficient
permission, invalid board id, invalid item id, missing mandatory field, unresolvable owner, invalid
status label, invalid date, unsupported column type, ambiguous match, `ComplexityException` and 429
rate limits (retry with backoff via an interceptor), network timeout, and generic GraphQL errors. No
message ever includes a token, client secret or Authorization header value.

---

## Capabilities

| Capability | Enabled | Why |
|---|---|---|
| MCP (`tool = true`) | **Yes** | Brief §7 — all 16 requests are agent-callable; needs `krista-apis` ≥ 1.0.125-sp1, so **1.0.126** is pinned |
| Entity search | **No** | *Deliberate scope call — see below* |
| Webhooks / WAIT_FOR_EVENT | No | No monday webhook surface in the supplied reference, and the brief asks for none |
| Solutions tab | No | Not requested |
| File handling | No | No upload/download operation in scope |

> **Stated exclusion — entity search.** Krista entity-search stores (`@Searchable` + `EntityStore` +
> dialect + resolvers, the `entity-search-implementation` skill) are **not** in this plan. Every
> search requirement in the brief is met through `Get Items`' typed filters. Enabling native entity
> search would let Studio resolve *"the Customer Onboarding task"* as a first-class entity reference,
> which is a genuine enhancement — say the word and it goes in, but it is a meaningful scope
> increase and the brief does not ask for it.

---

## Domain reuse

`domain-catalog.json` returned **`found: true`** for Essentials/Collaboration — 16 entities and 60
requests already published. I surfaced **18 genuine semantic matches** for per-item review: monday.com
shaped entries almost certainly from the earlier monday extension (`Board`, `Group`, `Group Item`,
`Activity Log`, `Get Group List`, `Fetch Group Items`, `Get ActivityLogs`) plus a SharePoint-style
list/item CRUD set covering the same business concepts (`Get Items`, `Get Item`, `Create ListItem`,
`Update ListItem`, `Delete Item`, `Get List`, `Get Lists`, `ListDetails`, `Item`, `Label`,
`Health Check`).

**You marked all 18 "new".** Nothing is reused; nothing was avoided; no capability was dropped by the
review. Three consequences you should see explicitly:

1. **Title collisions are intentional.** To keep Studio legible I named the new entities in this
   domain's own established `<X> Details` style — **Board Details**, **Board Item**, **Board Group**,
   **Board Column** — rather than re-colliding with the published thin `Item` {Details}, `Board`
   {Name, Id} and `Group` entities. Request names follow the brief verbatim, so **`Get Items` is a
   second request of that title in this domain** (the existing one is SharePoint's). Tell me if you'd
   rather they collide exactly or diverge further.
2. **`Label` → a field, not an entity.** Status/dropdown labels ship as a `Labels` multi-field on
   **Board Column**. A Krista `@Entity` cannot nest another `@Entity`
   (`field-types.md`), so a standalone label entity would have had no legal consumer.
3. **`Activity Log` → partially covered.** Item **updates/comments** are delivered (`Get Item
   Updates`, `Add Item Update`, `Include Updates`). monday's `activity_logs` query is **absent from
   the supplied API reference**, so board activity-log retrieval is *not* built — I will not invent an
   endpoint. Add it to `resources/` and it's a small follow-up.

Everything new is named vendor-agnostically (no `Monday Item`), so the next Collaboration extension
can reuse it.

---

## ⚠️ Placeholders and gaps — must be handled before deploy

| # | Item | Status |
|---|---|---|
| 1 | **`@Domain` `domainId`** | 🔴 **PLACEHOLDER.** `domain-catalog.json` gave titles and local ids only, not the registered domain/ecosystem UUIDs. A stable placeholder UUID is minted so the code compiles — **it will not bind to the real Essentials/Collaboration domain** until replaced. Copy from a sibling extension in this domain (`git grep '@Domain'`). |
| 2 | **`ecosystemId`** | 🔴 **PLACEHOLDER** — same, must be byte-identical to its siblings' value. |
| 3 | **`ecosystemVersion`** | 🟡 A stable UUID is fine (the one loose value). |
| 4 | `localDomainRequest_*` / `localDomainEntity_*` ids | 🟢 Local to the extension — generated as `uuid5` of `"<ext>.<area>.<method>"`, stable across releases. No action. |
| 5 | **monday OAuth app** | 🟡 Client ID/secret are Setup-tab fields, never hardcoded. You must register the app in monday Developer Center with redirect `…/rest/mondayextn/callback` and the six approved scopes; an MBL monday admin may need to approve it for the account. |
| 6 | **API signatures read at `2027-01`** | 🟡 The reference was introspected at release-candidate `2027-01` but the extension pins `2026-10`. All surface used is long-standing, but the reference says it is **not verified at `2026-10`** — the Test Connection request reports the live version so a drift shows up immediately. |
| 7 | **Unverified shapes** | 🟡 Flagged in the source material, to be confirmed on a live board: status/people compare-value shapes inside `ItemsQueryRule`, `{"index":N}` as a status write, and whether `create_labels_if_missing:false` really errors on an unknown label. Each has a unit test pinning the behaviour we implement, and a fallback path. |
| 8 | **Parent Item ID (brief §4)** | 🟡 **Not implemented.** The brief hedges *"if supported for the target board"*; monday creates sub-items with `create_subitem`, which is **not** in the supplied reference. Omitted rather than invented. |

---

## Build order

1. **Phases 1–2** — module skeleton (`build.gradle`, Java 21, `krista-apis` = `extension-impl-anno-processors` = `1.0.126`), `MblMondayDotComExtension`, `Attributes`, `ApiClient`.
2. **Phase 3** — auth: `TokenAuthenticator` (A) + `RequestAuthenticator`/`AuthCallbackResource`/JAX-RS `Application`/token store (C), behind the `Auth Type` PickOne.
3. **Phase 4** — 6 `@Entity` classes + transformers; `SetupArea`, `SearchArea`, `ManagementArea` with typed `@Field` inputs and typed `Entity(X)` outputs; then the real business logic (GraphQL client, interceptors for auth/retry/rate-limit, `ColumnMap` discovery+cache, value converters, validation, error translator, telemetry).
4. **Phase 6** — Docsify documentation tab under `src/main/resources/docs/`.
5. **Phase 7** — pure-Java unit tests (every `tests` entry in `implementation-plan.json`), self-audit against the Phase-0 inventory, `gradle clean jar` → `build/libs/mbl-monday-dot-com-1.0.0.jar`.

### API coverage

All 20 operations in `api-model.json` are exercised. 19 map to a catalog request directly;
`change_column_value` is used inside **Update Item** for single typed-column writes (alongside
`change_multiple_column_values` for batches and `change_simple_column_value` for clears) rather than
being exposed as its own request, since a standalone version would duplicate Update Item.
`versions` is surfaced through **Health Check**'s *Available API Versions*.
