---
title: Settings
description: Connectivity, DNS, IPv6, appearance, backups and more.
---

Some options only show up after you turn on **Show advanced options**.

## Connectivity and routing

| Setting | What it does |
| --- | --- |
| **Bypass LAN** | Routes private IPs and LAN domains directly. |
| **Routing policy** | *Subscription provider* (default) applies routing and per-app rules from the selected subscription. *User* keeps your own. |
| **Default outbound** | Where rootful *Default outbound* app routes go. |
| **IPv6 connections** | **Off**: IPv4 only. **Auto**: uses IPv6 once a quick check through the server passes. **On**: always prefers IPv6 when the network has it. |
| **TUN interface name** | Rootful with **TUN interface** only. Names the TUN interface. |
| **When another VPN is active** | Rootful with **Transparent proxy** only. How to share the device with another VPN; tap **(?)** next to a mode for details. |
| **Tunnel tethered clients** | Rootful only. Routes Wi-Fi hotspot and USB tethering traffic through Xray. |
| **Route app traffic through Xray** | Sends Material Xray's own data updates through the active connection. Latency checks always bypass it. |
| **Connection health watchdog** | Watches the local Xray API, the tunnel and root routing, and recovers the connection when they fail. It sends no external probes. |
| **Always-on VPN** | Opens Android's VPN settings, where you manage always-on VPN for Material Xray. Not available with root service. |

If root access is unavailable, **Use root service** shows a **Retry** button: grant access in your root manager, then tap it. Root access is checked when you tap Retry, enable root mode or connect, and a running root connection keeps checking it; if access is lost, the app switches to rootless mode. Rootless mode needs Android's VPN permission.

## Device VPN and local proxy server

**Device VPN** (on by default) routes this device's apps through Xray. **Local proxy server** opens a port that SOCKS5 and HTTP clients can connect to; tap its name to configure it. Turn on either or both. The proxy server alone (Device VPN off) needs neither VPN permission nor root.

Proxy traffic follows your routing rules and DNS settings, but not per-app rules.

On the **Local proxy server** page:

- **Allow LAN access**: lets other devices on your network connect too.
- **Connection details**: what a client needs, including a ready-made `socks5://` link. HTTP clients use the same address, port and credentials. Tap the pencil to change the port, username or password.

## DNS

Xray answers every port-53 query itself, so names routed through the proxy are never looked up on the network you're on.

- **Proxied domains**: the resolver for every name routed through the proxy.
- **Direct domains**: the resolver for LAN hosts and direct rules. These queries leave over your normal connection. The default, **System**, uses the resolver of the network you're on. In rootless mode it goes through Android, so it also follows your Private DNS setting. In root mode it asks the network's resolvers directly and reconnects when they change.
- **Encrypt queries**: use encrypted DNS.
- **Prefer profile DNS**: when a JSON profile defines its own DNS, use it. A subscription can decide this for its own servers with [`mxray-use-dns-from-json`](/docs/providers/response-headers/#mxray-use-dns-from-json); switching it then changes the choice for that subscription only.

Resolvers are comma-separated and tried in order. Each one is an IP address or a DoH URL like `https://dns.quad9.net/dns-query`.

## Core

**Choose Xray-core version** opens the core manager, where you can install a GitHub release or a core from a file. **Check for new Xray-core versions automatically** checks every 3 days, 1 week (default), 2 weeks or 1 month; tap its text to change the interval, which is separate from app update checks. Choose whether a new core only notifies you or installs automatically.

**Follow provider core requirements** (on by default) lets a subscription that sends [`mxray-min-core-version` or `mxray-max-core-version`](/docs/providers/response-headers/#mxray-min-core-version) pick the core: connecting to one of its servers switches to the newest release in its range, downloading it if needed, and automatic updates skip releases outside that range. With it off, connecting warns you instead and offers a core in range.

Advanced options cover the Xray log level, buffer size, TUN MTU, a RAM threshold that restarts the core, the latency-check URL, how many servers a latency test checks at once (10 by default; each httping check starts its own Xray core, so high values can freeze slow devices), and geodata URLs and update interval.

## Appearance and notifications

- **App language**: English or Russian, or follow the system.
- **Shortcut name**: the name under the launcher icon, **Material Xray** (default) or **MXray**.
- **App icon**: the default icon or a Material-style alternative.
- **Floating connect button**: a small corner button instead of the large power button. A check mark on it means always-on VPN is active; tapping it then opens Android's VPN settings.
- **Notification fields**: pick what the connection notification shows, in what order, and how often it updates. Drag a field's handle to reorder it. **Pinned interface** is available in rootful mode. With no fields selected, the notification still shows the connection status.

## Quick Settings tile

Add the Material Xray tile to toggle the connection from the notification shade. It shows whether you're connected, connecting, or have no server selected. Long-press it to open the app.

## Data

- **Export / Import**: a backup of subscriptions, servers, app routes, settings and a hand-edited runtime config. Downloaded geodata and Xray cores are not included; they download again. Importing replaces the current configuration.
- **Clear geodata**: deletes downloaded geoip and geosite files. Advanced options only.

## Diagnostics

Material Xray sends private crash and core-reliability data by default, with no sensitive information. Turn it off under **Diagnostics and usage statistics**.
