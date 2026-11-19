# Get Item Updates

## Overview

Returns the updates and comments posted on a monday.com item, newest first, with cursor paging for
long threads.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items(ids:) { updates_page(limit:, cursor:) }`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | **Yes** | The item whose updates should be returned. |
| Limit | Number | No | Maximum updates to return. Defaults to 25, capped at 500. |
| Cursor | Text | No | Pass the `Next Cursor` from a previous call to fetch the following page. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Updates | [ Entity(Item Update) ] | The updates. See [Entities](pages/entities.md). |
| Count | Number | How many updates were returned. |
| Next Cursor | Text | Pass back in as `Cursor`. Empty on the final page. |
| Message | Text | A readable summary, including the no-updates case. |

## Example

**Input**: `Item ID`=`13590022086`, `Limit`=`10`

**Output**: `Count` = 2, each **Item Update** carrying Update Id, Body (with monday's HTML), Text
Body (plain text), Creator Name and Created At.

## Business Rules

1. **Both body forms are returned.** `Body` keeps monday's HTML; `Text Body` is the plain-text
   version — use the latter for conversational replies.
2. An item with no updates returns `Count` = 0 and a clear message, not an error.
3. This request needs the `updates:read` scope in OAuth mode. See
   [Authentication](pages/Authentication.md).

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Item ID was blank.
- **`MONDAY_INVALID_ID`** — Item ID was not numeric.
- **`MONDAY_FORBIDDEN`** — the user cannot see that item, or the app lacks `updates:read`.

## Related Requests

- [Add Item Update](pages/AddItemUpdate.md) — post a new update.
- [Get Items](pages/GetItems.md) — its `Include Updates` input embeds recent updates inline.
