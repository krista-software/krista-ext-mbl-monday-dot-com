# Search Users

## Overview

Finds monday.com users by name, email or id. People columns need a numeric user id, but a person
asking for something says "assign it to Amit" — this is the request that bridges the two.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `users(ids:, name:, emails:, limit:)`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| Name | Text | No | Full or partial user name. monday matches this fuzzily. |
| Email | Text | No | Exact email address. |
| User ID | Text | No | Exact monday.com user id. |
| Limit | Number | No | Maximum users to return. monday defaults to 200. |

Supplying nothing returns the account's users up to the limit.

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Users | [ Entity(User) ] | The matching users. See [Entities](pages/entities.md). |
| Count | Number | How many users were returned. |
| Message | Text | A readable summary. |

## Example

**Input**: `Name`=`Amit`

**Output**: `Count` = 1 with User Id `122060799`, Name `Amit Shah`, Email, and the admin/guest
flags.

## Business Rules

1. **Name is fuzzy, email is exact.** Use Email when you need certainty.
2. [Create Items](pages/CreateItems.md) and [Update Item](pages/UpdateItem.md) call this internally
   to resolve the **Owners** input, so you usually do not have to — call it directly when you want
   to confirm a person first, or when a name was ambiguous.
3. When several users match a single owner reference, an exact case-insensitive name match is
   preferred before falling back to the first result.
4. No match returns `Count` = 0, not an error.

## Error Handling

- **`MONDAY_INVALID_ID`** — User ID was not numeric.
- **`MONDAY_FORBIDDEN`** — the app lacks the `users:read` scope. See
  [Authentication](pages/Authentication.md).

## Related Requests

- [Get Current User](pages/GetCurrentUser.md) — who the caller is.
- [Create Items](pages/CreateItems.md) — assign an owner at creation.
