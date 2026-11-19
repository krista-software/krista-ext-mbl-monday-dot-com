# Test Connection

## Overview

Confirms that the configured monday.com credentials actually work, and reports which monday.com
account they belong to. It makes a real authenticated call rather than only checking that a
credential is present, so a pass is meaningful.

## Request Details

- **Area**: Setup
- **Type**: QUERY_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `me` and `version`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

None.

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| User | Entity(User) | The monday.com user the connection acts as. See [Entities](pages/entities.md). |
| Connected | Boolean | True when the call succeeded. |
| Account Name | Text | The monday.com account (workspace) name. |
| API Version | Text | The API version monday actually served. |
| Message | Text | A readable summary, or the failure and its fix. |

## Example

**Input**: none

**Output**: `Connected` = true, `Account Name` = `mblpartners`, `API Version` = `2026-10`,
`Message` = "Connected to monday.com as Amit Shah."

## Business Rules

1. Always runs against the **operator** connection — the one configured on the Setup tab — even
   when an end user triggers it.
2. Reports the API version monday served, so drift from the pinned **API Version** shows up here
   first.
3. A failure is returned as `Connected` = false with the structured error fields, not as a thrown
   error, so it can be used inside a workflow.

## Error Handling

- **`MONDAY_NO_API_TOKEN`** — Auth Type is `ApiKey` but no token is configured.
- **`MONDAY_OAUTH_NOT_CONFIGURED`** — Auth Type is `OAuth` but Client ID or Client Secret is missing.
- **Authentication required** — OAuth is configured but nobody has signed in yet. Save the Setup
  tab and complete the monday.com sign-in.
- **`MONDAY_UNAUTHENTICATED`** — the token was rejected or revoked. See
  [Authentication](pages/Authentication.md).

The Setup tab's own **Test Connection** button performs the same check against the saved
configuration and reports its result inline.

## Related Requests

- [Health Check](pages/HealthCheck.md) — reachability and available API versions.
- [Get Current User](pages/GetCurrentUser.md) — the identity of the *calling* user, which in OAuth
  mode may differ from the operator connection.
