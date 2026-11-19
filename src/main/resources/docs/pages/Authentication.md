# Authentication

The extension supports two mechanisms, chosen with **Auth Type** on the Setup tab. Both send the
credential to monday.com the same way; only how it is obtained differs.

## Option 1 — OAuth 2.0 (recommended, the default)

Interactive authorization-code sign-in. Each Krista user or tenant connects their own monday.com
account, and actions run as that person with exactly their permissions.

### Register the app in monday.com

1. In monday.com open **Developer Center** → **Create app**.
2. Under **OAuth & Permissions**, add this exact redirect URL:

   ```
   https://<your-krista-appliance>/rest/mondayextn/callback
   ```

   The `mondayextn` segment is fixed by the extension. Saving the Setup tab in OAuth mode writes
   the fully-resolved URL to the extension log, so you can copy it verbatim rather than assembling
   it by hand.
3. Grant the scopes in the table below.
4. Copy the **Client ID** and **Client Secret**.

### Required scopes

| Scope | Why the extension needs it |
|---|---|
| `me:read` | Identify the signed-in account for Test Connection and Get Current User |
| `users:read` | Turn an owner's name or email into the user id a people column requires |
| `boards:read` | Discover boards, groups and columns; read items |
| `boards:write` | Create items and groups; update column values; move and archive items |
| `updates:read` | Read item updates and comments |
| `updates:write` | Post an update with Add Item Update |

If your app is registered with fewer scopes, edit the **OAuth Scopes** field to match — requesting a
scope the app does not hold fails the sign-in outright. Dropping `updates:read` / `updates:write`
disables [Get Item Updates](pages/GetItemUpdates.md) and [Add Item Update](pages/AddItemUpdate.md)
but leaves everything else working.

### Connect

1. Set **Auth Type** to `OAuth`, paste the Client ID and Client Secret, save.
2. Krista opens monday.com's sign-in page. Approve the requested scopes.
3. The browser returns to the callback and the connection is live.

A monday.com administrator may need to install or approve the app for your account before other
people can sign in.

### Token lifetime and refresh

monday.com's access tokens are understood not to expire until they are revoked, and the token
response may carry no refresh token at all. The extension handles both cases: it stores a refresh
token when monday returns one and uses it when a token expires, and otherwise asks the user to sign
in again. Either way an expired or revoked token produces a sign-in prompt, never a silent failure.

## Option 2 — API token

One token, pasted once, used for everyone on the connection.

1. In monday.com open your avatar → **Administration** → **API**, and generate a personal API token.
2. Set **Auth Type** to `ApiKey` and paste it into **API Token**.
3. Save, then press **Test Connection**.

Every action runs as the token's owner and sees exactly the boards that person can see, so this
mode cannot distinguish one Krista user from another. Use it for a shared service account or a
quick trial; use OAuth when people should act as themselves.

## How credentials are protected

- API Token, Client ID and Client Secret are **secured fields** — stored encrypted by Krista and
  never shown again after saving.
- OAuth tokens live in Krista's encrypted key-value store, in separate keyspaces for the operator
  connection and each end user, so one person's token can never be used for another's request.
- No token, secret or `Authorization` header value is ever written to a log, returned from a
  catalog request, or included in an error message. Error text carries the failure and the fix, not
  the credential.
- HTTP wire logging is disabled in the shipped `log4j2.xml` for exactly this reason.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "No API Token is configured" | Auth Type is `ApiKey` but the token field is empty. Paste a token. |
| "No monday.com OAuth app is configured" | Auth Type is `OAuth` but Client ID or Client Secret is missing. |
| "This connection has not been signed in to monday.com yet" | The app credentials are saved but the browser sign-in has not been completed. Save the Setup tab again and finish it. |
| monday.com reports a redirect URI mismatch | The redirect URL registered in the Developer Center does not match `https://<appliance>/rest/mondayextn/callback` exactly. |
| "monday.com declined the authorization" | The app is missing a requested scope, or an administrator has not approved it for the account. |
| `MONDAY_UNAUTHENTICATED` on a request that used to work | The token was revoked in monday.com. Sign in again. |
| `MONDAY_FORBIDDEN` | The signed-in user cannot see or edit that board. Check their board permissions. |
