---
title: Per-app proxy
description: Choose how each app's traffic is routed.
---

Open **Routing → Apps** to choose a route for each app. Search by name from the toolbar. The menu can also show system apps and work-profile apps.

Apps are grouped by route in the same order as the route picker, with unchanged apps last. Each group has a heading and a solid divider. **Always proxied** apps share one group, including those using a specific server; their label is **Always proxied with ServerName**. Apps within each group are sorted alphabetically.

Opening search shows up to five recently installed apps, newest first, respecting the system-app and work-profile filters. Typing switches smoothly to search results; rows animate as the query changes. Clearing the query returns to recently installed apps, and closing search restores the grouped list.

## Routes

| Route | Effect | Mode |
| --- | --- | --- |
| **Default selected server** | Uses the server selected on the home screen. This is the default. | Both |
| **Not proxied** | Uses the device network directly. With root and **Transparent proxy**, its DNS lookups still go to the [proxied domains resolver](/docs/guide/settings/#dns). | Both |
| **Default outbound** | Uses the default outbound chosen in Settings. | Rootful |
| **Specific server** | Sends the app through a server of your choice. Routing rules still apply. | Rootful |
| **Always proxied** | With a specific server: sends *all* of the app's traffic through it, ignoring routing rules. | Rootful |

In rootless mode, rootful-only routes are skipped, and the log says how many were.

## Bulk actions

The menu has **Bypass all apps**, which sets every app to *Not proxied*, and **Reset to defaults**.

## Provider-managed app routing

A subscription can [supply a per-app list](/docs/providers/per-app-routing/). While **Settings → Routing policy** is set to *Subscription provider* (the default), the selected subscription's list is applied automatically and the Apps tab shows who manages it.

If you edit a route, the app offers to **switch to manual mode**, which stops automatic updates. Your manual changes to apps the provider doesn't list survive refreshes.
