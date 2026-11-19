# Move Item To Group

## Overview

Moves a monday.com item into a different group on its own board, changing nothing else about it.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items(ids:) { board { id } }` to resolve
  the board, then `move_item_to_group`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | **Yes** | The item to move. |
| Group ID | Text | **Yes** | The destination group, on the item's own board. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Item | Entity(Board Item) | The item after the move. See [Entities](pages/entities.md). |
| Success | Boolean | True when the move was applied. |
| Message | Text | A readable summary, or the error and its fix. |

## Example

**Input**: `Item ID`=`13590022086`, `Group ID`=`topics`

**Output**: `Success` = true, `Message` = "Moved item 13590022086 to group Group Title.", and
**Item** showing the new group.

## Business Rules

1. **The item's board is resolved first.** monday's move mutation takes no board id, so the
   extension looks the item up before moving it — this is also what makes the group check possible.
2. **The destination group must exist on that board.** If it does not, the request fails with the
   board's valid group ids listed, rather than monday rejecting it opaquely.
3. **Items cannot move between boards** with this request; monday's mutation only moves between
   groups on the same board.
4. [Update Item](pages/UpdateItem.md) can also move an item via its `Group ID` input, as part of a
   larger update. Use this request when moving is the only thing you want to do.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Item ID or Group ID was blank.
- **`MONDAY_INVALID_ID`** — Item ID was not numeric.
- **`MONDAY_ITEM_NOT_FOUND`** — no visible item with that id.
- **`MONDAY_GROUP_NOT_FOUND`** — that group is not on the item's board; valid ids are listed.
- **`MONDAY_FORBIDDEN`** — the user cannot modify that item.

## Related Requests

- [Get Board Groups](pages/GetBoardGroups.md) — valid destination group ids.
- [Update Item](pages/UpdateItem.md) — move as part of a wider update.
