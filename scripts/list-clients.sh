#!/usr/bin/env bash
# List configured VPN clients and live connection status.
#
# Usage: sudo ./list-clients.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

require_root
load_params

echo "Configured clients:"
grep '^### Client ' "${WG_CONF}" | sed 's/^### Client /  - /' || echo "  (none)"
echo
wg show "${WG_IF}"
