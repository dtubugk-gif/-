#!/usr/bin/env bash
# Stop WireGuard and remove all server and client configuration.
#
# Usage: sudo ./uninstall.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

require_root
read -r -p "This deletes the VPN server and ALL client configs. Continue? [y/N] " ans
[[ ${ans} =~ ^[Yy]$ ]] || { echo "Aborted."; exit 0; }

systemctl disable --now "wg-quick@${WG_IF}" 2>/dev/null || true
rm -rf "${WG_CONF}" "${WG_PARAMS}" "${CLIENT_DIR}"
rm -f /etc/sysctl.d/99-wireguard.conf
sysctl --system >/dev/null
info "WireGuard VPN removed. (Packages were left installed.)"
