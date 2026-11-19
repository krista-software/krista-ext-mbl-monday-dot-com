# Get Current User

## Overview

Reports which monday.com account the current call is acting as. In OAuth mode that is the
signed-in end user; in ApiKey mode it is the token's owner.

## Request Details

- **Area**: Search
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `me`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

None.

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| User | Entity(User) | The monday.com user this call acts as. See [Entities](pages/entities.md). |
| Account Name | Text | The monday.com account (workspace) name. |
| Message | Text | A readable summary. |

## Example

**Input**: none

**Output**: **User** with User Id `122060799`, Name `Amit Shah`, Email, admin/guest flags, and
`Message` = "Acting as Amit Shah on monday.com."

## Business Rules

1. **This is the caller's identity, not the connection's.** When an end user or an agent acting for
   them has signed in with their own monday.com account, that is who is reported — which is the
   point of OAuth mode.
2. In ApiKey mode every caller resolves to the same token owner.
3. Useful for "who am I in monday?" and for confirming which account a conversation's actions will
   be attributed to.

## Error Handling

- **Authentication required** — this user has not connected their monday.com account yet. The
  platform prompts them to sign in.
- **`MONDAY_UNAUTHENTICATED`** — the stored token was rejected or revoked.

## Related Requests

- [Test Connection](pages/TestConnection.md) — verifies the operator connection instead.
- [Search Users](pages/SearchUsers.md) — look up other people.
