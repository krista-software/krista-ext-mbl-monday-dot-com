# Archive Item

## Overview

Archives a monday.com item so it leaves the active board view. Archiving is reversible in
monday.com and is not a delete — the item and its history remain.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items(ids:)` to confirm the item, then
  `archive_item`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | **Yes** | The item to archive. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Item | Entity(Board Item) | The item, with `State` reflecting the result. See [Entities](pages/entities.md). |
| Success | Boolean | True when the request completed. |
| Message | Text | A readable summary, or the error and its fix. |

## Example

**Input**: `Item ID`=`13590022086`

**Output**: `Success` = true, **Item** with `State` = `archived`, `Message` = "Item 13590022086 is
archived."

## Business Rules

1. **The item is read first.** monday's archive mutation takes no board id, so the extension
   confirms the item exists and is visible before acting.
2. **Archiving something already archived is not an error.** The request returns the item with a
   message saying so, which keeps retries and re-runs safe.
3. **This does not delete.** Archived items can be restored in monday.com, and are still findable
   via [Get Boards](pages/GetBoards.md) with `State` = `archived`.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Item ID was blank.
- **`MONDAY_INVALID_ID`** — Item ID was not numeric.
- **`MONDAY_ITEM_NOT_FOUND`** — no visible item with that id.
- **`MONDAY_FORBIDDEN`** — the user cannot modify that item.

## Related Requests

- [Get Item Details](pages/GetItemDetails.md) — check an item before archiving it.
- [Update Item](pages/UpdateItem.md) — change an item instead of removing it from view.
