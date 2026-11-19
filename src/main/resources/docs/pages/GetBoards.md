# Get Boards

## Overview

Lists the monday.com boards this connection can reach, with their groups and column layout. This is
how an agent answers "which boards can I use?" and how a caller finds the Board ID the other
requests need.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `boards(ids:, limit:, page:, state:,
  workspace_ids:, order_by: used_at)`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | No | Return only this board. |
| Name Filter | Text | No | Keep only boards whose name contains this text, ignoring case. |
| Workspace ID | Text | No | Restrict to one workspace. |
| State | Text | No | `all`, `active`, `archived` or `deleted`. Defaults to `active`. |
| Limit | Number | No | Maximum boards to return. Defaults to 25. |
| Page | Number | No | 1-based page number. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Boards | [ Entity(Board Details) ] | The matching boards. See [Entities](pages/entities.md). |
| Count | Number | How many boards were returned. |
| Message | Text | A readable summary. |

## Example

**Input**: `Name Filter`=`Engineering`

**Output**: `Count` = 1 and a **Board Details** entity with Board Id, Name, Workspace Name, its
Groups and its Columns.

## Business Rules

1. **Boards come back most-recently-used first** (`order_by: used_at`), which is also the order a
   boardless [Get Items](pages/GetItems.md) search scans them in.
2. **Name Filter is applied by the extension**, because monday's `boards` query has no name
   argument. It is a case-insensitive "contains" match against the page that was fetched — raise
   `Limit` if a board you expect is missing.
3. Only boards the signed-in user can see are ever returned.

## Error Handling

- **`MONDAY_INVALID_STATE`** — State was not one of the four permitted values.
- **`MONDAY_INVALID_ID`** — Board ID or Workspace ID was not numeric.
- **`MONDAY_UNAUTHENTICATED`** — sign in again; see [Authentication](pages/Authentication.md).

## Related Requests

- [Get Board Columns](pages/GetBoardColumns.md) — a board's columns and their permitted values.
- [Get Board Groups](pages/GetBoardGroups.md) — a board's groups.
