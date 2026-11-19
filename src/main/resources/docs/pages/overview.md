# Overview

## What this extension does

MBL Monday Dot Com connects Krista to [monday.com](https://monday.com), so agents, conversations and
workflows can work with board items directly. People ask for what they need in plain language; the
extension finds, creates and updates the items on their behalf.

It covers three headline capabilities, plus the discovery requests that make them work on any board:

| Capability | Request |
|---|---|
| Find items by id, name, owner, status, due date, group or free text | [Get Items](pages/GetItems.md) |
| Create an item with any combination of fields | [Create Items](pages/CreateItems.md) |
| Change only the fields the user mentioned | [Update Item](pages/UpdateItem.md) |

## Why board layout does not matter

Every monday.com board is laid out differently. One board's due date lives in a column called
`date4`, another's in `due`; one calls the people column "Person", another "Assigned To".

This extension never assumes. Before any read or write it asks the board what columns it has, then
maps friendly names onto the real ones:

| What a person says | What the extension resolves it to |
|---|---|
| Owner, assignee, assigned to | the board's `people` column |
| Status, state | the board's `status` column |
| Due date, due, deadline | the board's `date` column |
| Priority | a status or dropdown column titled Priority |
| Description, notes | the board's long-text column |

Columns outside that list are still fully usable — pass them by title or id through **Additional
Column Values**, and every column is returned on the way back in **Column Values**. See
[Get Board Columns](pages/GetBoardColumns.md).

## Two behaviours worth knowing

**Updates change only what you asked for.** A field you do not mention is never sent, so it keeps
its existing value. Clearing a field is a separate, explicit instruction. "Change the status of item
12345 to Done" touches the status column and nothing else. See [Update Item](pages/UpdateItem.md).

**Ambiguous matches never guess.** If you identify an item by name and more than one matches,
nothing is written — the candidates come back so the right one can be chosen. See
[Update Item](pages/UpdateItem.md).

## Typical conversations

> "Show me all open items assigned to John."
> "Find the item related to Customer Onboarding."
> "Create an item called Release Testing, assign it to John, set the status to In Progress and the due date to 25 November 2026."
> "Update item 123456789 and change its status to Done."
> "Move the due date of the Customer Onboarding task to 10 December 2026."

Every request is exposed as an AI-agent tool, so the agent can collect missing details and call the
right one itself.

## Before you start

You need a monday.com connection configured on the Setup tab — either an OAuth sign-in or a pasted
API token. See [Extension Configuration](pages/ExtensionConfiguration.md) and
[Authentication](pages/Authentication.md).
