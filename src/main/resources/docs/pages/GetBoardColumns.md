# Get Board Columns

## Overview

Reports a board's real columns — their ids, titles and types — plus the exact values a status or
dropdown column will accept. This is the request that makes a board's layout discoverable instead
of guessed, and the one to call before writing an unfamiliar column.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `boards(ids:) { columns { id title type
  settings_str … } }`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Board ID | Text | **Yes** | The board whose columns should be returned. |
| Include Archived | Boolean | No | Include archived columns. Defaults to false. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Columns | [ Entity(Board Column) ] | The board's columns. See [Entities](pages/entities.md). |
| Count | Number | How many columns were returned. |
| Message | Text | A readable summary. |

Each **Board Column** carries:

| Field | Type | Description |
|---|---|---|
| Column Id | Identifier | The id to use in Additional Column Values. |
| Title | Text | The column's display title. |
| Type | Text | monday's column type: `people`, `status`, `date`, `long_text`, `dropdown`, … |
| Friendly Name | Text | Which conversational name resolves to this column on this board. |
| Labels | [ { Index, Label } ] | Permitted values, for status and dropdown columns. |

## Example

**Input**: `Board ID`=`18440687408`

**Output**: four columns — `person` (people, Friendly Name `Owner`), `status` (status, Friendly
Name `Status`, Labels `Working on it` / `Done` / `Stuck`), `date4` (date, Friendly Name `Due Date`)
and `name`.

## Business Rules

1. **Friendly Name is the mapping, made visible.** It shows which column this board uses for Owner,
   Status, Due Date, Priority or Description, so you can confirm the resolution before relying on
   it. It is empty for columns that are none of those.
2. **Labels are parsed from the column's own settings**, so they are always the board's current
   values. A column type with no label catalogue returns an empty list rather than an error.
3. Archived columns are excluded unless you ask for them.

## Error Handling

- **`MONDAY_MISSING_REQUIRED_INPUT`** — Board ID was blank.
- **`MONDAY_INVALID_ID`** — Board ID was not numeric.
- **`MONDAY_BOARD_NOT_FOUND`** — the board does not exist or is not visible.

## Related Requests

- [Create Items](pages/CreateItems.md) / [Update Item](pages/UpdateItem.md) — the writes this
  informs.
- [Get Board Groups](pages/GetBoardGroups.md) — the board's groups.
