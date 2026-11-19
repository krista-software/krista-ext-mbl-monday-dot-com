# Health Check

## Overview

Confirms monday.com is reachable, reports how long it took, and lists the API versions monday
currently offers — useful for spotting when the pinned version is heading for retirement.

## Request Details

- **Area**: Setup
- **Type**: CHANGE_SYSTEM
- **External API**: `POST https://api.monday.com/v2` — `version` and `versions`
- **Ask Agent (MCP)**: Enabled (callable by the Krista AI agent)

## Input Parameters

None.

## Output Parameters

| Parameter | Type | Description |
|---|---|---|
| Healthy | Boolean | True when monday.com responded successfully. |
| API Version | Text | The version monday served for this call. |
| Available API Versions | [ Text ] | Every version monday currently offers. |
| Response Time Ms | Number | Round-trip time in milliseconds. |
| Message | Text | A readable summary, or the failure and its fix. |

## Example

**Input**: none

**Output**: `Healthy` = true, `API Version` = `2026-10`, `Available API Versions` =
`["2025-04", "2025-07", "2025-10", "2026-01", "2026-04", "2026-07", "2026-10", "2027-01"]`,
`Response Time Ms` = 184.

## Business Rules

1. Runs against the operator connection.
2. A failure returns `Healthy` = false with the structured error fields rather than throwing, so it
   can drive a monitoring workflow.
3. If the pinned **API Version** stops appearing in `Available API Versions`, monday has retired it
   — change the Setup-tab field. See
   [Extension Configuration](pages/ExtensionConfiguration.md).

## Error Handling

- **`MONDAY_NETWORK_ERROR`** — the appliance cannot reach `api.monday.com`.
- **`MONDAY_UNAUTHENTICATED`** — credentials are missing or rejected; see
  [Test Connection](pages/TestConnection.md).

## Related Requests

- [Test Connection](pages/TestConnection.md) — verifies credentials as well as reachability.
