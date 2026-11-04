#!/usr/bin/env bash
# shellcheck disable=SC2034  # variables are used by the scripts that source this file
# Shared helpers for the WireGuard VPN scripts.
set -euo pipefail

WG_DIR="/etc/wireguard"
WG_IF="${WG_IF:-wg0}"
WG_CONF="${WG_DIR}/${WG_IF}.conf"
WG_PARAMS="${WG_DIR}/params"
CLIENT_DIR="${WG_DIR}/clients"

info()  { printf '\033[1;32m[+]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()   { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

require_root() {
  [[ ${EUID} -eq 0 ]] || die "Run this script as root (e.g. sudo $0)."
}

load_params() {
  [[ -f ${WG_PARAMS} ]] || die "VPN server is not installed. Run install-server.sh first."
  # shellcheck source=/dev/null
  source "${WG_PARAMS}"
}

valid_client_name() {
  [[ $1 =~ ^[A-Za-z0-9_-]{1,32}$ ]]
}
