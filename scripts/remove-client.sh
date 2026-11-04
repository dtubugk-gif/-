#!/usr/bin/env bash
# Revoke a VPN client.
#
# Usage: sudo ./remove-client.sh <name>
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

NAME="${1:-}"
[[ -n ${NAME} ]] || die "Usage: $0 <client-name>"
valid_client_name "${NAME}" || die "Invalid client name."

require_root
load_params

grep -qx "### Client ${NAME}" "${WG_CONF}" || die "Client '${NAME}' not found."

# Delete the "### Client NAME" block (marker line through the blank line before the next block).
sed -i "/^### Client ${NAME}\$/,/^\$/d" "${WG_CONF}"
rm -f "${CLIENT_DIR}/${NAME}.conf"

wg syncconf "${WG_IF}" <(wg-quick strip "${WG_IF}")
info "Client '${NAME}' revoked."
