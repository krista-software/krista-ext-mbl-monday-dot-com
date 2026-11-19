# Get Item Details

## Overview

Retrieves one monday.com item by id, always with every column value. Use this when the id is
already known — for "get the details of item 123456789" — and
[Get Items](pages/GetItems.md) when it has to be searched for.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items(ids:)`, plus `items.updates_page`
  when updates are requested
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | **Yes** | The monday.com item id. |
| Include Updates | Boolean | No | Also return the item's recent updates. Defaults to false. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Item | Entity(Board Item) | The item, with all columns. See [Entities](pages/entities.md). |
| Found | Boolean | False when no visible item has that id. |
| Message | Text | A readable summary, or what to try instead. |

## Example

**Input**: `Item ID`=`13590022086`

**Output**: `Found` = true and an **Item** carrying Name, Board Id, Board Name, Group, Owners,
Status, Due Date, Item URL, Created At, Updated At and the full Column Values list.

## Business Rules

1. Column values are always included — this request exists to return everything about one item.
2. A missing item is reported as `Found` = false with guidance, not as an error, because "does this
   exist?" is a legitimate question.
3. Named fields (Status, Owners, Due Date, Priority, Description) are resolved from the item's own
   board layout.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Item ID was blank.
- **`MONDAY_INVALID_ID`** — Item ID was not numeric.
- **`MONDAY_FORBIDDEN`** — the user cannot see that item's board.

## Related Requests

- [Get Items](pages/GetItems.md) — find an item when the id is unknown.
- [Get Item Updates](pages/GetItemUpdates.md) — paged access to the item's comments.
