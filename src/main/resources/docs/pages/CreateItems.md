# Create Items

## Overview

Creates an item on a monday.com board, setting the owner, status, due date, priority, description
and any other column that board happens to have — all resolved against that board's real layout.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `create_item`, preceded by `boards` for
  column discovery and `users` for owner resolution
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | **Yes** | The board to create the item on. |
| Item Name | Text | **Yes** | The new item's name or title. |
| Group ID | Text | No | Place the item in this group. Defaults to the board's first group. |
| Owners | Text | No | One or more owners as names, emails or user ids, comma separated. |
| Status | Text | No | A label from the board's status column, for example `Working on it`. |
| Due Date | Date | No | Value for the board's date column. |
| Priority | Text | No | A label from the board's priority column. |
| Description | Paragraph | No | Value for the board's description or long-text column. |
| Additional Column Values | [ { Column: Text, Value: Text } ] | No | Any other column, named by its title or id, with the value to set. |
| Create Labels If Missing | Boolean | No | Let monday add an unknown status label. Defaults to **false**. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Item | Entity(Board Item) | The created item, fully populated. See [Entities](pages/entities.md). |
| Success | Boolean | True when the item was created. |
| Item Id | Text | The new item's monday.com id. |
| Item URL | Text | Direct link to the item. |
| Created At | Date | Creation timestamp. |
| Message | Text | A readable summary, or the error and its fix. |

## Example

**Input**: `Board ID`=`18440687408`, `Item Name`=`Release Testing`, `Owners`=`John`,
`Status`=`Working on it`, `Due Date`=`25 Nov 2026`

**Output**: `Success` = true, `Item Id` = `13590022999`, `Item URL` pointing at the new item, and
**Item** carrying the resolved owner, status and due date.

The item is immediately retrievable with [Get Items](pages/GetItems.md) or
[Get Item Details](pages/GetItemDetails.md).

## Validation Rules

| Validation | Error Code | Resolution |
|---|---|---|
| Board ID and Item Name are present | `MONDAY_MISSING_REQUIRED_INPUT` | Supply the missing field. |
| Board ID is numeric | `MONDAY_INVALID_ID` | Use the id from [Get Boards](pages/GetBoards.md). |
| The board exists and is visible | `MONDAY_BOARD_NOT_FOUND` | Check the id and the user's access. |
| Group ID is on that board | `MONDAY_GROUP_NOT_FOUND` | Valid group ids are listed in the error. |
| A named column exists on that board | `MONDAY_UNKNOWN_COLUMN` | The board's column titles are listed in the error. |
| Status and dropdown values are real labels | `MONDAY_INVALID_LABEL` | The permitted labels are listed in the error. |
| Dates are readable | `MONDAY_INVALID_DATE` | Supply `YYYY-MM-DD`. |
| Number columns get numbers | `MONDAY_INVALID_NUMBER` | Supply a numeric value. |
| Owners resolve to real users | `MONDAY_UNKNOWN_OWNER` | Find the person with [Search Users](pages/SearchUsers.md). |

Every one of these is checked **before** the create call, so a bad input never results in a
half-created item.

## Business Rules

1. **Friendly names are resolved per board.** Owner, Status, Due Date, Priority and Description map
   to whatever columns that board uses — see [Overview](pages/overview.md).
2. **Multiple owners are supported** where the column allows it: `John, Amit` assigns both.
3. **Create Labels If Missing defaults to false**, so an unrecognised status label is rejected
   instead of silently changing the board's structure for everyone. Set it true deliberately, and
   note that monday requires board-structure permission for it.
4. **Custom columns are first-class.** Anything not covered by the named inputs goes through
   Additional Column Values by title or id.

## Error Handling

Business failures come back as `Success` = false inside a successful response, carrying
**Error Code**, **Error Message**, **Operation**, **Context** and **Suggested Action** — so a
workflow can branch on `Success` and an agent can read the fix aloud.

- **`MONDAY_FORBIDDEN`** — the signed-in user cannot create items on that board.
- **`MONDAY_RATE_LIMITED`** / **`MONDAY_COMPLEXITY_BUDGET_EXCEEDED`** — retried automatically with
  backoff before being reported.

## Related Requests

- [Get Board Columns](pages/GetBoardColumns.md) — what the board accepts before you write.
- [Get Board Groups](pages/GetBoardGroups.md) — valid Group IDs.
- [Update Item](pages/UpdateItem.md) — change an item after creating it.
