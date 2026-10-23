---
title: Routing rules
description: Send traffic through the proxy, around it, or nowhere, by domain, IP, port or protocol.
---

Open **Routing → Rules** to manage the rules Xray applies to each connection. Rules are checked top to bottom, and the first match wins.

The bottom Rules/Apps/Network selector floats above a gradient. The selected tab reveals its icon smoothly. Tapping a distant tab animates only the outgoing and destination tabs; skipped tabs remain unselected. Swipes and their settling flings follow the pager position. Lists scroll beneath the selector, with end padding aligned exactly to the upper edge of the faded area. Search and the selector transition smoothly together with their gradient and reserved space. Selecting rules slides and fades the selector and its gradient away, releasing the reserved space; clearing selection restores them. Routing’s top bar changes color when content scrolls beneath it, as on Home and Settings; there is no fade below the top bar.

Each rule card shows its destination outbound tag, including custom and subscription rules. Server-specific rules can also show a balancer tag.

Tap the Find in Page icon just before the toolbar menu to open a compact search panel above the keyboard, as on Logs. It filters custom, subscription-wide and server-specific rules by name, destination tag or matching conditions, including domains, geosite/geoip entries, IPs, ports and protocols. Search ignores letter case and keeps the original rule order. Search hides the normal add/refresh and menu actions on the searched tab, leaving the close button and result count. Selecting rules in search still exposes their selection actions. Close search to clear the filter. Filtering the list does not change which rules Xray applies.

Switching between Rules and Apps fades only the toolbar icons that change. The page title and unchanged icons stay in place. Opening search or selection crossfades the title together with its close button, keeping the outgoing title in its original position. Swiping between tabs is disabled while searching or selecting rules or log lines.

Toolbar actions use Material Symbols Outlined in equal 48 dp cells, with centered 24 dp icons and a horizontal overflow menu. Empty cells keep their size during selection and search; only changed icons crossfade.

When only custom rules are selected, **Remove selected** fades into the toolbar and fades out when the selection includes other rule categories. Its position stays fixed throughout the transition. Removing one or more rules always asks for confirmation; the **Remove** button uses the same red color as **Clear geodata**. Cancelling keeps the rules and selection intact.

During selection, the top bar uses its scrolled background color and shows a close button before the selected-rule count, as on Logs. Tap it to clear the selection. The top bar fades between selection and normal actions, keeping each icon in place throughout the transition.

Long-press a rule to enter selection mode. Selected rules use the same highlight as the selected server, and switches fade into drag handles. Hold a handle to move its rule within the category; hold a category header's handle to change the priority of entire categories. Haptic feedback marks the start of dragging and each change of position. Long-pressing a category header selects its rules. The list scrolls automatically near the top and bottom while dragging. Screen readers offer **Move up** and **Move down** actions on the handles.

Hold a rule's text and drag across other rules to select a range, just as on Logs. Starting on a selected rule removes the range from the selection; reversing the gesture restores the previous selection outside the shortened range. Category headers are skipped, and scrolling near the list's edges extends the range. The trailing control area is reserved for switches and reorder handles, so a selection gesture cannot start there.

Both orders are saved and determine Xray's first matching rule. DNS and LAN service rules stay ahead of these categories, and the profile's terminal default route stays after them. Filtering does not delete hidden rules. Reordering provider rules or categories switches routing policy to manual so refreshes cannot overwrite the chosen subscription-wide order. Profile order is stored with that server's local routing overrides. Close selection with the header's cross, the back gesture, or by deselecting the last rule to apply the saved order to an active connection.

The **Reset** button with a restore icon on the right of **Server-specific** and **Subscription rules** opens a confirmation dialog. Reset restores that category's original rules, enabled states and rule order. Server reset removes local routing overrides while retaining edits to the server configuration; subscription reset restores the rules supplied by the subscription identified in the dialog. Custom rules, category order and manual routing policy are kept.

Category headers share a 48 dp minimum height, so the Reset action does not make Server-specific or Subscription rules taller than Custom.

## Built-in rules

A fresh install includes:

- **Bypass Russian domains and IPs** (on): `.ru`, `.su`, `.рф`, `geosite:category-ru` and `geoip:ru` go to `direct`.
- **Block ads** (off): `geosite:category-ads-all` goes to `block`.

Toggle them, edit them, or use **Reset to default** from the menu.

## Writing a rule

The rule editor's disk-shaped **Save** icon is in the header. New rules open with the title **Add new rule**, and existing rules with **Edit rule**. The form scrolls behind the transparent system navigation bar, with enough bottom padding to keep its last fields accessible.

| Field | Example | Notes |
| --- | --- | --- |
| **Outbound tag** | `proxy`, `direct`, `block` | Where matching traffic goes. |
| **Domains** | `domain:example.com, geosite:google` | Comma-separated, in Xray's domain syntax. |
| **IPs** | `geoip:ru, 1.2.3.0/24` | Comma-separated CIDRs or geoip lists. |
| **Port** | `443` or `1000-2000` | A single port or a range. |
| **Protocols** | `http`, `tls`, `quic`, `bittorrent` | Leave empty to match everything. |

**Match mode** decides how filled fields combine:

- **All conditions (AND)** keeps everything in one Xray rule. Traffic must match every filled field.
- **Any condition group (OR)** turns each filled field into its own Xray rule. Traffic can match any one of them.

:::caution
A rule with no conditions matches **all** traffic, so no rule below it is ever reached. The editor warns you before you save one.
:::

## Geodata

The APK bundles current `geoip.dat` and `geosite.dat` from [v2fly](https://github.com/v2fly), so a new install can connect without downloading anything. Settings lets you update them or point to custom download URLs.

## Provider-managed routing

A subscription can supply [its own routing](/docs/providers/routing-header/), including custom geodata URLs. With **Routing policy** set to *Subscription provider*, those rules are applied and updated automatically. Rules are labeled **Subscription rules**, **Server-specific** or **Custom**. Editing a provider rule offers to switch to manual mode.

If the provider's geodata can't be downloaded, the app runs in **compatibility mode** and skips the rules that need it. Tap the banner to retry.

## Server per network

Open **Routing → Network** (the globe tab after Apps) and enable **Use specific server per network** in the large rounded switch at the top. Wi-Fi and mobile choices appear in a rounded group below it; SSID rules follow, with matching 12 dp gaps before and after the heading, including when the override status is hidden. The information icon near the bottom introduces left-aligned explanations and permission actions. The page keeps the app’s normal background; its network cards use the same surface color as rule cards. The main toggle uses the app’s standard switch. Choose one server for Wi-Fi and another for mobile data. **Last used on this network type** restores the server selected before leaving Wi-Fi or mobile data. Explicit SSID and network-type assignments take priority. If no previous server is known, the current selection stays unchanged. These choices work in both root and rootless modes.

The app follows the physical internet connection underneath the VPN. It waits briefly for network changes to settle, changes the selected server and switches an active tunnel once. A manual server choice remains in effect until the network or these settings change; it is remembered for that network type before switching away. This history survives app restarts, subscription refreshes and backups. Ethernet and an offline device have no automatic assignment.

Add a **Wi-Fi network override** to choose a server for an exact SSID. Names are case-sensitive and spaces are significant. An SSID override wins over the general Wi-Fi choice. The editor can fill the current network name when Android makes it available; names can also be entered manually. Server lists are searchable and grouped by subscription.

Android requires **precise location** permission and enabled **Location** to expose the SSID. On Android 10 and later, permit location **all the time** for SSID matching while the app is in the background. Grant these permissions only if you want SSID rules; the general Wi-Fi/mobile choices do not need them. If Android hides the name or permission is missing, the general Wi-Fi setting applies. The app does not request Wi-Fi scans or log SSIDs.

Automatic selection pauses when an edited active runtime configuration would be discarded or the assigned server requires an incompatible core. The Network page shows the reason. Your configuration is preserved. Choose a compatible core or remove the active edit, then reconnect or change the network setting to retry. Removed servers are not guessed by name. Assignments follow server replacements during subscription refresh and use stable server references in backups.

The status line fades in only while an SSID override matches the current Wi-Fi and its server is selected; it fades out when the override stops applying. Choose server fades its list at both edges, as the per-app Route dialog does. The footer explains only the Android location requirements and that global Wi-Fi/mobile defaults work without those permissions. Permission actions align with the text below the information icon. Find servers has a search icon. Open location permissions targets this app’s Location permission page: with root mode enabled it can open directly through the system activity manager; without root, Android may restrict direct links and require the app details fallback.
