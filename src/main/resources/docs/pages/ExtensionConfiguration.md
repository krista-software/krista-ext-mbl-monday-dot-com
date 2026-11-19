# Extension Configuration

All configuration lives on the extension's **Setup** tab in Krista.

## Setup-tab fields

| Field | Required | Default | Description |
|---|---|---|---|
| Auth Type | Yes | OAuth | `OAuth` signs each user in to their own monday.com account. `ApiKey` uses one pasted token for everyone on this connection. |
| API Token | ApiKey mode only | — | A monday.com API token. Secured — never displayed again after saving. |
| Client ID | OAuth mode only | — | The Client ID of your monday.com Developer Center app. Secured. |
| Client Secret | OAuth mode only | — | The Client Secret of that app. Secured. |
| OAuth Scopes | No | `me:read users:read boards:read boards:write updates:read updates:write` | Scopes requested at sign-in. Must be scopes the app is registered for. |
| API Version | No | `2026-10` | Sent as the `API-Version` header on every call. |
| Timeout Seconds | No | `60` | HTTP connect, read and write timeout. |
| Max Boards To Scan | No | `25` | How many boards [Get Items](pages/GetItems.md) may scan when no Board ID is given. |

Which credential fields apply depends on **Auth Type**. Saving is validated: choosing `ApiKey`
without a token, or `OAuth` without both Client ID and Client Secret, is rejected with a message
explaining what is missing.

## Choosing an Auth Type

| | OAuth | ApiKey |
|---|---|---|
| Who the actions run as | each signed-in user, individually | the token owner, for everyone |
| Per-user and per-tenant accounts | Yes | No |
| Setup effort | register an app in the Developer Center | paste one token |
| Visible boards | whatever that user can open | whatever the token owner can open |

OAuth is the default and is what the per-user requirement needs. See
[Authentication](pages/Authentication.md) for both walkthroughs.

## About the API Version

monday.com versions its API quarterly and the default moves with it, so this extension pins the
version explicitly rather than drifting. `2026-10` was the current stable version when 1.0.0 was
built.

To move to a newer version, change the field and run [Test Connection](pages/TestConnection.md) —
it reports the version monday actually served, so a mismatch shows up immediately.
[Health Check](pages/HealthCheck.md) lists every version monday currently offers.

## About Max Boards To Scan

monday.com has no account-wide item search: a search must name a board. When
[Get Items](pages/GetItems.md) is called without a Board ID, the extension searches the most
recently used boards one at a time, up to this cap, and sets `Search Truncated` when the cap stopped
it early.

Raise it for wider searches at the cost of more API calls and a bigger share of monday's complexity
budget; lower it for faster, cheaper lookups. A caller can also override it per request.

## Verifying the configuration

1. Save the Setup tab.
2. In OAuth mode, complete the monday.com sign-in when prompted.
3. Press **Test Connection**. It makes a real authenticated call and names the monday.com user it
   connected as — proving the credentials work, not merely that they exist.
