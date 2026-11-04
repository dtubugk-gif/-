# Personal WireGuard VPN

Scripts to run your own VPN server with [WireGuard](https://www.wireguard.com/), a fast and modern VPN protocol.
You rent a small Linux server, run one script on it, and connect your phone and computers to it.

## What you need

- A Linux server with a public IP address, for example a $4–6/month VPS from DigitalOcean, Hetzner, Vultr, Linode or AWS Lightsail.
  Supported systems: **Ubuntu 20.04+, Debian 11+, Fedora, RHEL/Rocky/Alma 9, Arch**.
- Root (sudo) access to that server.
- Inbound **UDP port 51820** open in your provider's firewall or security group.

## 1. Install the server

SSH into your server, then run:

```bash
git clone https://github.com/dtubugk-gif/-.git vpn && cd vpn
sudo ./scripts/install-server.sh
```

This script:

1. installs WireGuard,
2. generates the server keys,
3. turns on IP forwarding and NAT so your traffic can reach the internet,
4. opens the firewall (ufw or firewalld, if one is active),
5. starts the VPN and sets it to start on boot,
6. creates a first client called `client1` and prints its QR code.

Optional flags:

| Flag | Default | Purpose |
|------|---------|---------|
| `--port 51820` | `51820` | UDP port to listen on |
| `--endpoint vpn.example.com` | auto-detected public IP | Hostname or IP that clients connect to |
| `--dns "9.9.9.9"` | `1.1.1.1, 1.0.0.1` | DNS servers that clients use |

## 2. Connect your devices

Install the official WireGuard app: <https://www.wireguard.com/install/>

- **Phone (iOS/Android):** tap **+**, choose **Scan from QR code**, and scan the QR code the script printed.
- **Computer (Windows/macOS/Linux):** copy `/etc/wireguard/clients/<name>.conf` from the server to the computer, then use **Import tunnel from file**.
  ```bash
  scp root@YOUR_SERVER:/etc/wireguard/clients/client1.conf .
  ```

Turn the tunnel on. To check that it works, visit <https://ifconfig.me>: it should now show your server's IP.

## 3. Manage clients

Give each device its own client.

```bash
sudo ./scripts/add-client.sh my-phone      # new client, prints a QR code
sudo ./scripts/remove-client.sh my-phone   # revoke access
sudo ./scripts/list-clients.sh             # list clients and live connection status
sudo ./scripts/uninstall.sh                # remove the VPN completely
```

Client names can contain letters, digits, `_` and `-` (up to 32 characters). One server supports up to 253 clients.

## How it works

- VPN subnet: `10.8.0.0/24` (IPv4) and `fd42:42:42::/64` (IPv6). The server is `10.8.0.1`.
- Clients send **all** traffic through the tunnel (`AllowedIPs = 0.0.0.0/0, ::/0`).
  For split tunneling, change `AllowedIPs` in the client config to the ranges you want to route.
- Each peer has its own key pair plus a preshared key, which adds a layer of post-quantum resistance.
- Adding or removing a client applies immediately with `wg syncconf`, without disconnecting other clients.

Files on the server:

| Path | Contents |
|------|----------|
| `/etc/wireguard/wg0.conf` | Server config and all peers |
| `/etc/wireguard/params` | Settings the helper scripts use |
| `/etc/wireguard/clients/*.conf` | Client configs (contain private keys, keep them secret) |

## Troubleshooting

- **Connected but no internet:** check that UDP 51820 is open in your cloud provider's firewall, and run `sudo wg show` to see whether a handshake happened.
- **No handshake at all:** the endpoint IP or port is wrong, or the port is blocked. Reinstall with `--endpoint`.
- **Service status/logs:** `systemctl status wg-quick@wg0` and `journalctl -u wg-quick@wg0`.

## Legal note

A VPN you host yourself is legal in most countries, but some restrict VPN use. Check your local laws and your hosting provider's terms of service.
