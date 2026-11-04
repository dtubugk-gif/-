#!/usr/bin/env bash
# Add a VPN client and print its config + QR code.
#
# Usage: sudo ./add-client.sh <name>
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

NAME="${1:-}"
[[ -n ${NAME} ]] || die "Usage: $0 <client-name>"
valid_client_name "${NAME}" || die "Client name must be 1-32 chars: letters, digits, '_' or '-'."

require_root
load_params

CLIENT_CONF="${CLIENT_DIR}/${NAME}.conf"
[[ -f ${CLIENT_CONF} ]] && die "Client '${NAME}' already exists (${CLIENT_CONF})."

# Find the first free host number (2..254).
OCTET=""
for i in $(seq 2 254); do
  if ! grep -q "AllowedIPs = ${SUBNET_V4}.${i}/32" "${WG_CONF}"; then OCTET=$i; break; fi
done
[[ -n ${OCTET} ]] || die "Subnet is full (253 clients max)."

umask 077
CLIENT_PRIV="$(wg genkey)"
CLIENT_PUB="$(printf '%s' "${CLIENT_PRIV}" | wg pubkey)"
PSK="$(wg genpsk)"

cat >> "${WG_CONF}" <<PEER

### Client ${NAME}
[Peer]
PublicKey = ${CLIENT_PUB}
PresharedKey = ${PSK}
AllowedIPs = ${SUBNET_V4}.${OCTET}/32, ${SUBNET_V6}${OCTET}/128
PEER

cat > "${CLIENT_CONF}" <<CLIENT
[Interface]
PrivateKey = ${CLIENT_PRIV}
Address = ${SUBNET_V4}.${OCTET}/32, ${SUBNET_V6}${OCTET}/128
DNS = ${DNS}

[Peer]
PublicKey = ${SERVER_PUB}
PresharedKey = ${PSK}
Endpoint = ${ENDPOINT}:${PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
CLIENT

# Apply without dropping existing connections.
wg syncconf "${WG_IF}" <(wg-quick strip "${WG_IF}")

info "Client '${NAME}' added with IP ${SUBNET_V4}.${OCTET}"
info "Config file: ${CLIENT_CONF}"
if command -v qrencode >/dev/null; then
  echo
  echo "Scan this with the WireGuard mobile app:"
  qrencode -t ansiutf8 < "${CLIENT_CONF}"
fi
