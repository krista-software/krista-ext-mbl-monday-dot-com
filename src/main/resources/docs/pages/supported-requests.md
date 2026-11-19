# Supported Requests

Sixteen catalog requests across three areas. Every one is exposed as an AI-agent tool.

## Setup

| Request | Type | Purpose |
|---|---|---|
| [Test Connection](pages/TestConnection.md) | QUERY_SYSTEM | Verify the configured credentials and report the account they belong to. |
| [Health Check](pages/HealthCheck.md) | CHANGE_SYSTEM | Confirm monday.com is reachable and list available API versions. |

## Search

| Request | Type | Purpose |
|---|---|---|
| [Get Items](pages/GetItems.md) | QUERY_SYSTEM | Find items by id, name, owner, status, due date, group or free text. |
| [Get Item Details](pages/GetItemDetails.md) | QUERY_SYSTEM | Retrieve one item by id with every column value. |
| [Get Boards](pages/GetBoards.md) | QUERY_SYSTEM | List accessible boards with their groups and columns. |
| [Get Board Columns](pages/GetBoardColumns.md) | QUERY_SYSTEM | Discover a board's columns, types and permitted status labels. |
| [Get Board Groups](pages/GetBoardGroups.md) | QUERY_SYSTEM | List a board's groups. |
| [Get Item Updates](pages/GetItemUpdates.md) | QUERY_SYSTEM | Read the updates and comments on an item. |
| [Search Users](pages/SearchUsers.md) | QUERY_SYSTEM | Find users by name or email to assign as owners. |
| [Get Current User](pages/GetCurrentUser.md) | QUERY_SYSTEM | Report which monday.com account the caller acts as. |

## Management

| Request | Type | Purpose |
|---|---|---|
| [Create Items](pages/CreateItems.md) | CHANGE_SYSTEM | Create an item with owner, status, due date and any board column. |
| [Update Item](pages/UpdateItem.md) | CHANGE_SYSTEM | Change only the supplied fields on an existing item. |
| [Move Item To Group](pages/MoveItemToGroup.md) | CHANGE_SYSTEM | Move an item into another group on its board. |
| [Archive Item](pages/ArchiveItem.md) | CHANGE_SYSTEM | Archive an item. |
| [Add Item Update](pages/AddItemUpdate.md) | CHANGE_SYSTEM | Post an update or comment on an item. |
| [Create Group](pages/CreateGroup.md) | CHANGE_SYSTEM | Create a new group on a board. |

## Choosing between similar requests

| If you want to… | Use |
|---|---|
| Look up an item you already have the id for | [Get Item Details](pages/GetItemDetails.md) |
| Search for an item by anything else | [Get Items](pages/GetItems.md) |
| Change several fields at once | [Update Item](pages/UpdateItem.md) |
| Only move an item between groups | [Move Item To Group](pages/MoveItemToGroup.md) |
| Find out what a board's status column accepts | [Get Board Columns](pages/GetBoardColumns.md) |
| Check the connection works | [Test Connection](pages/TestConnection.md) |

## Error response shape

Every request reports failures the same way, with:

| Field | Meaning |
|---|---|
| Error Code | A stable code such as `MONDAY_INVALID_LABEL`. |
| Error Message | What went wrong, in plain language. |
| Operation | Which request failed. |
| Context | The relevant ids or values — never a credential. |
| Suggested Action | What to do about it. |

QUERY_SYSTEM requests return this as a failed response. CHANGE_SYSTEM requests return it inside a
successful envelope with `Success` = false, so a workflow can branch on `Success`.
