# Get Items

## Overview

Finds monday.com items by any combination of id, name, owner, status, due date, group or free text.
This is the request behind "show me all open items assigned to John" and "find the item related to
Customer Onboarding". When no Board ID is supplied it searches across accessible boards rather than
failing.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items`, `boards.items_page`,
  `groups.items_page` and `next_items_page`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | No | Restrict the search to one board. Omit to search accessible boards. |
| Item ID | Text | No | Return exactly this item. Takes precedence over every other filter. |
| Item Name | Text | No | Full or partial item name; matched as "contains". |
| Owner | Text | No | Filter by assigned person: a name, an email or a monday user id. |
| Status | Text | No | Filter by the board's status label, for example `Done`. |
| Due Date | Date | No | Filter by an exact due date. |
| Due Date From | Date | No | Start of a due-date range. |
| Due Date To | Date | No | End of a due-date range. |
| Group ID | Text | No | Restrict the search to one group on the board. |
| Search Text | Text | No | Free-text search across item names. Ignored if Item Name is also given. |
| Limit | Number | No | Maximum items to return. Defaults to 25; values above monday's 500 are clamped. |
| Include Column Values | Boolean | No | Return every column value for each item. Defaults to **true**. |
| Include Updates | Boolean | No | Also return recent updates for each item. Defaults to false. |
| Max Boards To Scan | Number | No | How many boards a boardless search may scan. Defaults to the Setup-tab value (25). |
| Cursor | Text | No | Pass the `Next Cursor` from a previous call to fetch the following page. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Items | [ Entity(Board Item) ] | The matching items. See [Entities](pages/entities.md). |
| Count | Number | How many items were returned. |
| Next Cursor | Text | Pass back in as `Cursor` for the next page. Empty when there are no more. |
| Search Truncated | Boolean | True when a board-scan or limit cap stopped the search early. |
| Message | Text | A readable summary, including the "nothing matched" case. |

## Example

**Input**: `Board ID`=`18440687408`, `Status`=`Done`, `Limit`=`50`

**Output**: `Count` = 3, `Message` = "Found 3 matching items.", and three **Items** each carrying
Item Id, Name, Status `Done`, Owner Names, Due Date, Item URL and the full Column Values list.

**Input**: `Item Name`=`Customer Onboarding` (no Board ID)

**Output**: the extension scans up to 25 recently used boards and returns every match, setting
`Search Truncated` = true if there were more boards it did not reach.

## Business Rules

1. **Item ID short-circuits everything.** When supplied, the item is fetched directly and no search
   runs.
2. **Boardless searches fan out.** monday has no account-wide item search, so the extension walks
   recently-used boards up to `Max Boards To Scan`. `Search Truncated` tells you when that cap bit.
3. **A board that cannot be read is skipped, not fatal.** During a fan-out, one inaccessible board
   is logged and skipped so the rest of the search still returns. Searching a single named board
   does surface the error.
4. **Filters target the board's real columns.** Status, Owner and Due Date are resolved per board,
   so the same inputs work across different layouts. A filter for a column a board does not have is
   skipped for that board.
5. **Owner names are resolved first.** A name or email becomes a monday user id before the query
   runs, because people columns match on id.
6. **No match is a result, not an error.** `Count` = 0 with an explanatory `Message`.
7. **Cursors are single-board.** `Next Cursor` is only returned when the search covered exactly one
   board; a fan-out has no single meaningful position.

## Error Handling

- **`MONDAY_INVALID_ID`** — Board ID or Item ID was not numeric. Use the ids returned by
  [Get Boards](pages/GetBoards.md) or Get Items.
- **`MONDAY_BOARD_NOT_FOUND`** — the board does not exist or is not visible to the signed-in user.
- **`MONDAY_GROUP_NOT_FOUND`** — the Group ID is not on that board; the valid group ids are listed.
- **`MONDAY_COMPLEXITY_BUDGET_EXCEEDED`** — the query was too large. Lower `Limit`, set
  `Include Column Values` to false, or name a single board. Retried automatically with backoff first.
- **`MONDAY_RATE_LIMITED`** — monday throttled the call. Retried automatically with backoff.
- **`MONDAY_UNAUTHENTICATED`** — sign in again; see [Authentication](pages/Authentication.md).

## Related Requests

- [Get Item Details](pages/GetItemDetails.md) — one item by id, always with all columns.
- [Get Board Columns](pages/GetBoardColumns.md) — what a board's status column will accept.
- [Update Item](pages/UpdateItem.md) — change what you found.
