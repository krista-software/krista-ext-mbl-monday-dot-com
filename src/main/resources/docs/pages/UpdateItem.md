# Update Item

## Overview

Updates an existing monday.com item, changing **only** the fields you supply and leaving everything
else exactly as it was. If the item is identified by name rather than id and more than one matches,
nothing is written and the candidates are returned instead.

## Request Details

- **Area**: Management
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `items` / `boards.items_page` to identify,
  then `change_multiple_column_values`, `change_simple_column_value` (explicit clears) and
  `move_item_to_group`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

### Identifying the item — at least one is required

| Parameter | Type | Required | Description |
|---|---|---|---|
| Item ID | Text | No | The item to update. **Used in preference to every other identifier.** |
| Board ID | Text | No | Narrows a name-based lookup to one board. |
| Item Name | Text | No | Identify the item by name when no Item ID is known. |
| Search Text | Text | No | Identify the item by free text when no Item ID is known. |

### Fields that can be changed — all optional

| Parameter | Type | Description |
|---|---|---|
| New Item Name | Text | Rename the item. |
| Owners | Text | Replace the owners with these names, emails or user ids. |
| Status | Text | Set the board's status column to this label. |
| Due Date | Date | Set the board's date column to this date. |
| Priority | Text | Set the board's priority column to this label. |
| Description | Paragraph | Set the board's description or long-text column. |
| Group ID | Text | Move the item into this group. |
| Additional Column Values | [ { Column: Text, Value: Text } ] | Any other column, by title or id. |
| Fields To Clear | [ Text ] | Fields to **empty**. Only these are cleared. |
| Create Labels If Missing | Boolean | Let monday add an unknown status label. Defaults to false. |

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Item | Entity(Board Item) | The item after the update, re-read from monday.com. |
| Success | Boolean | True when the update was applied. **False when the match was ambiguous.** |
| Updated Fields | [ Text ] | Exactly the fields that were written, with `(cleared)` marking clears. |
| Ambiguous | Boolean | True when several items matched and nothing was changed. |
| Candidates | [ { Id, Name, Board, Group, Status, Url } ] | The matching items, when ambiguous. |
| Message | Text | A readable summary of what happened, or what to do next. |

## The three states of a field

This is the behaviour that keeps updates safe:

| What you do | What happens |
|---|---|
| Do not supply the field | It is **never sent**. monday leaves the existing value alone. |
| Supply a value | That column is written. |
| Name it in **Fields To Clear** | That column is explicitly emptied. |

A blank or empty value counts as "not supplied", not as "clear it" — clearing is always explicit.
Supplying the same field as both a value and a clear is rejected as contradictory.

**Example.** *"Change the status of item 12345 to Done."* → `Item ID`=`12345`, `Status`=`Done`.
Only the status column is sent. The title, owner, due date and every custom column are untouched.

## Example

**Input**: `Item ID`=`13590022086`, `Status`=`Done`, `Due Date`=`27 Nov 2026`

**Output**: `Success` = true, `Updated Fields` = `["Status", "Date"]`, `Ambiguous` = false, and
**Item** showing the new values. Both fields go in a single call to monday.

**Ambiguous input**: `Item Name`=`API Integration` where two items match

**Output**: `Success` = false, `Ambiguous` = true, `Candidates` listing both items with their board,
group and status, and `Message` = "2 items matched. Nothing was changed — re-run Update Item with
the Item ID of the one you mean." **No write is attempted.**

## Business Rules

1. **Item ID always wins.** Supplied alongside a name, the id is used and no search runs.
2. **The item is read before it is written**, which is how the board is resolved for its columns.
3. **Exactly one match proceeds; several stop; none stops.** An ambiguous search never results in
   an arbitrary item being modified.
4. **Several fields become one call.** Status and due date together are one
   `change_multiple_column_values`, not two round-trips.
5. **Clears use a separate mutation**, because emptying a column is a different operation from
   setting one.
6. **A group change is a separate step** — monday's move mutation takes no board id, so the item's
   board is confirmed first.
7. **Nothing supplied is not an error.** The item comes back unchanged with an explanatory message.
8. **The response is re-read from monday.com** after writing, so it reflects anything monday
   computed itself.

## Error Handling

- **`MONDAY_NO_ITEM_IDENTIFIER`** — no Item ID, Item Name or Search Text was given.
- **`MONDAY_ITEM_NOT_FOUND`** — the Item ID does not exist or is not visible.
- **`MONDAY_CONTRADICTORY_UPDATE`** — a field was given both a value and a clear.
- **`MONDAY_CANNOT_CLEAR_NAME`** — an item's name cannot be emptied; supply a new name instead.
- **`MONDAY_UNKNOWN_COLUMN`** — the board has no such column; its titles are listed.
- **`MONDAY_INVALID_LABEL`** — not a valid status value; the permitted labels are listed.
- **`MONDAY_GROUP_NOT_FOUND`** — the target group is not on the item's board.
- **`MONDAY_FORBIDDEN`** — the user lacks permission to edit that item.

Failures arrive as `Success` = false with Error Code, Operation, Context and Suggested Action.

## Related Requests

- [Get Items](pages/GetItems.md) — find the item, and its id, first.
- [Get Board Columns](pages/GetBoardColumns.md) — check valid labels before setting a status.
- [Move Item To Group](pages/MoveItemToGroup.md) — move without changing anything else.
