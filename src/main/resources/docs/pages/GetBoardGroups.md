# Get Board Groups

## Overview

Lists the groups (sections) on a monday.com board, with the ids needed to place an item in a group
or move it between them.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `boards(ids:) { groups { id title color … } }`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | **Yes** | The board whose groups should be returned. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Groups | [ Entity(Board Group) ] | The board's groups. See [Entities](pages/entities.md). |
| Count | Number | How many groups were returned. |
| Message | Text | A readable summary. |

## Example

**Input**: `Board ID`=`18440687408`

**Output**: `Count` = 2 with groups `topics` and `group_title`, both titled "Group Title", each
carrying the owning Board Id.

## Business Rules

1. Group ids are **strings scoped to their board**, not global numeric ids — `topics` is a typical
   value. Always take them from here rather than assuming.
2. Every returned group carries its Board Id, so a list drawn from several boards stays unambiguous.
3. The same ids are validated by [Create Items](pages/CreateItems.md),
   [Update Item](pages/UpdateItem.md) and [Move Item To Group](pages/MoveItemToGroup.md), which
   list the valid ones when a supplied group is wrong.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Board ID was blank.
- **`MONDAY_INVALID_ID`** — Board ID was not numeric.
- **`MONDAY_BOARD_NOT_FOUND`** — the board does not exist or is not visible.

## Related Requests

- [Create Group](pages/CreateGroup.md) — add a new group.
- [Move Item To Group](pages/MoveItemToGroup.md) — move an item between groups.
