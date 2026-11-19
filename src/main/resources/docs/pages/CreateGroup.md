# Create Group

## Overview

Creates a new group (section) on a monday.com board, so items can be organised into it.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `create_group`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | **Yes** | The board to add the group to. |
| Group Name | Text | **Yes** | Title for the new group. |
| Group Color | Text | No | Six-digit hex colour, for example `#037f4c`. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Group | Entity(Board Group) | The created group. See [Entities](pages/entities.md). |
| Success | Boolean | True when the group was created. |
| Message | Text | A readable summary, or the error and its fix. |

## Example

**Input**: `Board ID`=`18440687408`, `Group Name`=`Q1 Delivery`, `Group Color`=`#037f4c`

**Output**: `Success` = true and a **Board Group** carrying the new Group Id — use it as the
`Group ID` input to [Create Items](pages/CreateItems.md).

## Business Rules

1. **monday assigns the group id**, derived from the name; it is returned in the response and
   cannot be chosen.
2. **The colour is validated before the call.** A six-digit hex value with or without the leading
   `#` is accepted and normalised; anything else is rejected with a clear message.
3. **monday's deprecated `position` argument is never sent.** Ordering uses the supported
   relative-position mechanism only.
4. Creating a group changes board structure, so the signed-in user needs permission to do so.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Board ID or Group Name was blank.
- **`MONDAY_INVALID_ID`** — Board ID was not numeric.
- **`MONDAY_INVALID_COLOUR`** — Group Color was not a six-digit hex value.
- **`MONDAY_FORBIDDEN`** — the user cannot change that board's structure.

## Related Requests

- [Get Board Groups](pages/GetBoardGroups.md) — the board's existing groups.
- [Create Items](pages/CreateItems.md) — create an item directly into the new group.
