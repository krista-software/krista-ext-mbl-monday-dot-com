# Entities

Six entities are returned by this extension. All output is structured, so it can be composed
downstream in workflows and read back by an agent.

## Board Item

A work item on a monday.com board. Returned by [Get Items](pages/GetItems.md),
[Get Item Details](pages/GetItemDetails.md), [Create Items](pages/CreateItems.md),
[Update Item](pages/UpdateItem.md), [Move Item To Group](pages/MoveItemToGroup.md) and
[Archive Item](pages/ArchiveItem.md).

| Field | Type | Description |
|---|---|---|
| Item Id | Text | The item's unique monday.com id. |
| Name | Text | The item's name or title. |
| Board Id | Text | Id of the board the item belongs to. |
| Board Name | Text | Name of that board. |
| Group Id | Text | Id of the group the item sits in. |
| Group Name | Text | Title of that group. |
| Owners | [ { Id, Name, Kind } ] | People and teams assigned, from the board's people column. |
| Owner Names | Text | The same owners as a readable comma-separated list. |
| Status | Text | Label of the board's status column. |
| Status Index | Number | Numeric index of that label on the board. |
| Is Done | Boolean | True when monday treats the current status as a done state. |
| Due Date | Date | Value of the board's date column. |
| Priority | Text | Label of the board's priority column, when it has one. |
| Description | Paragraph | Value of the board's description or long-text column. |
| State | Text | `active`, `archived` or `deleted`. |
| Item URL | Text | Direct link to the item in monday.com. |
| Created At | Date | When the item was created. |
| Updated At | Date | When the item was last changed. |
| Column Values | [ { Id, Title, Type, Text, Value } ] | **Every** column on the board, named or not. |
| Updates | [ { Id, Body, Creator, Created } ] | Recent updates; populated only when Include Updates is true. |

> The named fields are a convenience layer over `Column Values`, resolved from the board's real
> layout. Custom columns always appear in `Column Values` even when no named field matches them —
> that list is what makes a board-specific column usable downstream.

## Board Details

A monday.com board. Returned by [Get Boards](pages/GetBoards.md).

| Field | Type | Description |
|---|---|---|
| Board Id | Text | The board's unique id. |
| Name | Text | The board's display name. |
| Description | Paragraph | The board's description. |
| State | Text | `active`, `archived` or `deleted`. |
| Board Kind | Text | `public`, `private` or `share`. |
| Board URL | Text | Direct link to the board. |
| Workspace Id | Text | Id of the containing workspace. |
| Workspace Name | Text | Name of that workspace. |
| Groups | [ { Id, Title } ] | Groups defined on the board. |
| Columns | [ { Id, Title, Type } ] | Columns defined on the board. |

## Board Group

A group (section) on a board. Returned by [Get Board Groups](pages/GetBoardGroups.md) and
[Create Group](pages/CreateGroup.md).

| Field | Type | Description |
|---|---|---|
| Group Id | Text | The group's id, unique within its board. |
| Title | Text | The group's display title. |
| Board Id | Text | Id of the owning board. |
| Color | Text | The group's colour in monday.com. |
| Position | Text | The group's ordering position. |
| Archived | Boolean | True when the group is archived. |

## Board Column

A column definition. Returned by [Get Board Columns](pages/GetBoardColumns.md).

| Field | Type | Description |
|---|---|---|
| Column Id | Text | The column's id, unique within its board. |
| Title | Text | The column's display title. |
| Type | Text | monday's column type: `people`, `status`, `date`, `long_text`, `dropdown`, … |
| Board Id | Text | Id of the owning board. |
| Description | Text | The column's description, if set. |
| Archived | Boolean | True when the column is archived. |
| Friendly Name | Text | Which conversational name resolves here: Owner, Status, Due Date, Priority or Description. Empty for other columns. |
| Labels | [ { Index, Label } ] | Permitted values for a status or dropdown column; empty otherwise. |

## Item Update

An update or comment on an item. Returned by [Get Item Updates](pages/GetItemUpdates.md) and
[Add Item Update](pages/AddItemUpdate.md).

| Field | Type | Description |
|---|---|---|
| Update Id | Text | The update's unique id. |
| Item Id | Text | Id of the item it belongs to. |
| Body | Paragraph | The update body as posted, including HTML markup. |
| Text Body | Paragraph | The same body as plain text. |
| Creator Id | Text | Id of the user who posted it. |
| Creator Name | Text | Name of that user. |
| Created At | Date | When it was posted. |
| Updated At | Date | When it was last edited. |

## User

A monday.com user. Returned by [Search Users](pages/SearchUsers.md),
[Get Current User](pages/GetCurrentUser.md) and [Test Connection](pages/TestConnection.md).

| Field | Type | Description |
|---|---|---|
| User Id | Text | The user's unique monday.com id — what a people column needs. |
| Name | Text | The user's display name. |
| Email | Email | The user's email address. |
| Enabled | Boolean | True when the account is active. |
| Is Admin | Boolean | True for account administrators. |
| Is Guest | Boolean | True for guest users. |
| Account Name | Text | The monday.com account (workspace) name. |

## A note on dates

Date fields are exchanged as Krista dates. On the way in, monday needs `YYYY-MM-DD` and the
extension converts for you; on the way out, monday's `YYYY-MM-DD` and ISO-8601 timestamps are both
parsed. A date monday returns in an unexpected shape is left empty rather than failing the read.
