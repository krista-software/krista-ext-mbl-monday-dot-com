# Release Notes

| | |
|---|---|
| Extension | MBL Monday Dot Com |
| Current version | 1.0.0 |
| Ecosystem / Domain | Essentials / Collaboration |
| Target system | monday.com GraphQL API, `POST https://api.monday.com/v2` |
| Pinned API version | `2026-10` (configurable on the Setup tab) |
| Krista Service APIs Java | 1.0.126 |
| Java | 21 |

---

## Version 1.0.0 (November 2026)

First release. Built against Krista Service APIs Java 1.0.126.

### Added

**Authentication**

- OAuth 2.0 authorization-code sign-in, so each Krista user or tenant connects their own
  monday.com account. Redirect URL `https://<appliance>/rest/mondayextn/callback`.
- API-token mode for a single shared service account, selectable with **Auth Type** on the Setup
  tab.
- Tokens held in Krista's encrypted store, with separate keyspaces for the operator connection and
  each end user. Refresh tokens are used when monday issues them; otherwise an expired token
  re-drives sign-in.
- Scopes: `me:read`, `users:read`, `boards:read`, `boards:write`, `updates:read`, `updates:write`.

**Catalog requests** — sixteen, all AI-agent callable. See
[Supported Requests](pages/supported-requests.md).

- Setup: [Test Connection](pages/TestConnection.md), [Health Check](pages/HealthCheck.md).
- Search: [Get Items](pages/GetItems.md), [Get Item Details](pages/GetItemDetails.md),
  [Get Boards](pages/GetBoards.md), [Get Board Columns](pages/GetBoardColumns.md),
  [Get Board Groups](pages/GetBoardGroups.md), [Get Item Updates](pages/GetItemUpdates.md),
  [Search Users](pages/SearchUsers.md), [Get Current User](pages/GetCurrentUser.md).
- Management: [Create Items](pages/CreateItems.md), [Update Item](pages/UpdateItem.md),
  [Move Item To Group](pages/MoveItemToGroup.md), [Archive Item](pages/ArchiveItem.md),
  [Add Item Update](pages/AddItemUpdate.md), [Create Group](pages/CreateGroup.md).

**Behaviour**

- Dynamic board and column discovery: Owner, Status, Due Date, Priority and Description resolve to
  each board's real columns, by column type first and title second. No column id is hardcoded.
- Partial updates that distinguish "not supplied" from "supplied" from "explicitly cleared", so an
  update never overwrites a field the caller did not mention.
- Ambiguous item lookups return the candidates and write nothing.
- Cross-board search when no Board ID is given, capped by **Max Boards To Scan** (default 25) and
  flagged with `Search Truncated`.
- Cursor paging on item and update lists; limits clamped to monday's 500 maximum.
- Structured errors carrying Error Code, Error Message, Operation, Context and Suggested Action,
  including GraphQL errors returned inside an HTTP 200.
- Automatic retry with exponential backoff for rate limiting and complexity-budget failures.

**Entities** — Board Item, Board Details, Board Group, Board Column, Item Update, User. See
[Entities](pages/entities.md).

### Known limitations

- **Sub-items are not supported.** monday creates them with `create_subitem`, which is not covered
  by the API reference this release was built from, so `Create Items` has no Parent Item ID input.
- **Board activity logs are not exposed.** Item updates and comments are
  ([Get Item Updates](pages/GetItemUpdates.md)), but monday's `activity_logs` query was outside the
  reference used here.
- **Free-text search covers item names**, not every column: monday's API offers no all-column text
  search on this surface.
- **Items cannot be moved between boards** — monday's move mutation only moves between groups on
  the same board.
- **Krista entity search is not enabled.** Searching is done through `Get Items`' typed filters
  rather than native entity resolution.

### Notes for upgraders

The API reference used to build this release was read at monday's `2027-01` release candidate,
while the extension pins the current stable `2026-10`. Everything used is long-standing API
surface, but the first run of [Test Connection](pages/TestConnection.md) in a new environment is
worth doing — it reports the version monday actually served.
