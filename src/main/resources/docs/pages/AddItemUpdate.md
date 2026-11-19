# Add Item Update

## Overview

Posts an update (a comment) on a monday.com item, optionally as a reply to an existing update and
optionally mentioning people.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items(ids:)` to confirm the item, then
  `create_update`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | **Yes** | The item to post the update on. |
| Body | Paragraph | **Yes** | The update text. |
| Parent Update ID | Text | No | Reply to this update instead of posting a new one. |
| Mentions | [ { Id: Text, Type: Text } ] | No | People, teams or boards to mention. `Type` defaults to `User`. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Update | Entity(Item Update) | The posted update. See [Entities](pages/entities.md). |
| Success | Boolean | True when the update was posted. |
| Message | Text | A readable summary, or the error and its fix. |

## Example

**Input**: `Item ID`=`13590022086`, `Body`=`Deployment finished, moving to Done.`

**Output**: `Success` = true and an **Item Update** carrying Update Id, Body, Text Body, Creator
Name and Created At.

## Business Rules

1. **Mentions go in the Mentions input, not the text.** monday does not turn an `@` in the body
   into a real mention — typing `@Amit` posts the literal characters. Use Mentions with the user's
   id, which [Search Users](pages/SearchUsers.md) returns.
2. **The item is confirmed first**, because monday's `create_update` takes no board id.
3. **Parent Update ID turns the post into a reply** in that thread.
4. This request needs the `updates:write` scope in OAuth mode. See
   [Authentication](pages/Authentication.md).

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Item ID or Body was blank.
- **`MONDAY_INVALID_ID`** — Item ID was not numeric.
- **`MONDAY_ITEM_NOT_FOUND`** — no visible item with that id.
- **`MONDAY_FORBIDDEN`** — the user cannot post on that item, or the app lacks `updates:write`.

## Related Requests

- [Get Item Updates](pages/GetItemUpdates.md) — read the thread.
- [Search Users](pages/SearchUsers.md) — find the user ids to mention.
