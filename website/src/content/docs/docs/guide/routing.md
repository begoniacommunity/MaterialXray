---
title: Routing rules
description: Send traffic through the proxy, around it, or nowhere, by domain, IP, port or protocol.
---

Open **Routing → Rules** to manage the rules Xray applies to each connection. Rules are checked top to bottom, and the first match wins.

Rules come in three categories: **Custom** (yours), **Subscription rules** (from the selected subscription) and **Server-specific** (from the selected server's profile). Each rule shows the outbound it sends traffic to. DNS and LAN service rules always come before these categories, and the profile's final default route after them.

Tap the search icon to filter rules by name, outbound tag or conditions such as domains, geosite/geoip entries, IPs, ports and protocols. Filtering only changes what you see, not which rules apply.

## Reordering and selecting

Long-press a rule to select it, or hold a rule's text and drag across others to select a range. While selecting:

- Drag a rule's handle to move it within its category, or a category header's handle to change that category's priority.
- **Remove selected** deletes the selected custom rules.
- Close selection to apply the new order to an active connection.

Reordering subscription rules or categories switches **Routing policy** to manual, so refreshes don't undo your order.

**Reset** next to **Server-specific** or **Subscription rules** restores that category's original rules, on/off states and order. A server reset removes your local routing overrides for that server but keeps edits to its configuration; a subscription reset restores the rules the subscription supplied. Custom rules, category order and manual routing policy are kept.

## Built-in rules

A fresh install includes:

- **Bypass Russian domains and IPs** (on): `.ru`, `.su`, `.рф`, `geosite:category-ru` and `geoip:ru` go to `direct`.
- **Block ads** (off): `geosite:category-ads-all` goes to `block`.
- **Block QUIC** (off): connections Xray sniffs as QUIC go to `block`, so apps fall back to TCP. TCP through the proxy usually costs the phone less CPU than QUIC over UDP.

Toggle them, edit them, or use **Reset to default** from the menu.

## Writing a rule

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

The APK bundles `geoip.dat` and `geosite.dat` from a pinned [v2fly](https://github.com/v2fly) release, so a new install can connect without downloading anything. Settings lets you update them or point to custom download URLs.

## Provider-managed routing

A subscription can supply [its own routing](/docs/providers/routing-header/), including custom geodata URLs. With **Routing policy** set to *Subscription provider*, those rules are applied and updated automatically. Editing a provider rule offers to switch to manual mode.

If the provider's geodata can't be downloaded, the app runs in **compatibility mode** and skips the rules that need it. Tap the banner to retry.

## Server per network

Open **Routing → Network** and turn on **Use specific server per network**. Choose one server for Wi-Fi and another for mobile data, or **Last used on this network type** to return to the server you last used there; if none is known yet, the current selection stays. The app follows the physical connection underneath the VPN: once a network change settles, it switches to the assigned server, including on an active connection. A server you pick by hand stays until the network or these settings change, and is remembered as the last used one for that network type. Ethernet and offline have no assignment. This works in both root and rootless modes.

Add a **Wi-Fi network override** to use a server on one exact SSID. Names are case-sensitive and spaces count. An override takes priority over the general Wi-Fi choice.

SSID overrides need **precise location** permission with **Location** turned on, and on Android 10 and later location access **all the time** to work in the background. When one is missing, **Add Wi-Fi network** asks for it first; you can continue without it. The general Wi-Fi and mobile choices don't need these permissions; without them, the general Wi-Fi choice applies. The app does not scan for Wi-Fi networks or log SSIDs.

Automatic switching pauses when it would discard an edited active configuration, or when the assigned server needs an incompatible core; the Network page shows why. Choose a compatible core or remove the edit, then reconnect. Assignments follow a server when a subscription refresh replaces it, but a removed server is not guessed by name.
