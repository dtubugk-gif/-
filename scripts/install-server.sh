#!/usr/bin/env bash
# Install and configure a WireGuard VPN server.
#
# Usage: sudo ./install-server.sh [--port 51820] [--endpoint vpn.example.com] [--dns "1.1.1.1, 1.0.0.1"]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

PORT=51820
ENDPOINT=""
DNS="1.1.1.1, 1.0.0.1"
SUBNET_V4="10.8.0"        # clients get 10.8.0.2 - 10.8.0.254
SUBNET_V6="fd42:42:42::"  # private IPv6 range

while [[ $# -gt 0 ]]; do
  case $1 in
    --port)     PORT=$2; shift 2 ;;
    --endpoint) ENDPOINT=$2; shift 2 ;;
    --dns)      DNS=$2; shift 2 ;;
    -h|--help)  sed -n '2,5p' "$0"; exit 0 ;;
    *)          die "Unknown option: $1" ;;
  esac
done

require_root
[[ -f ${WG_PARAMS} ]] && die "WireGuard already installed (${WG_PARAMS} exists). Run uninstall.sh first to reinstall."
if ! [[ ${PORT} =~ ^[0-9]+$ ]] || (( PORT < 1 || PORT > 65535 )); then die "Invalid port: ${PORT}"; fi

# --- Install packages -------------------------------------------------------
[[ -f /etc/os-release ]] || die "Cannot detect OS (missing /etc/os-release)."
# shellcheck source=/dev/null
source /etc/os-release
info "Detected OS: ${PRETTY_NAME:-$ID}"

case "${ID} ${ID_LIKE:-}" in
  *ubuntu*|*debian*)
    apt-get update -y
    DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard wireguard-tools iptables qrencode curl
    ;;
  *fedora*)
    dnf install -y wireguard-tools iptables qrencode curl
    ;;
  *rhel*|*centos*|*rocky*|*almalinux*)
    dnf install -y epel-release
    dnf install -y wireguard-tools iptables qrencode curl
    ;;
  *arch*)
    pacman -Sy --noconfirm wireguard-tools iptables qrencode curl
    ;;
  *)
    die "Unsupported distribution: ${ID}. Supported: Ubuntu, Debian, Fedora, RHEL/Rocky/Alma, Arch."
    ;;
esac

# --- Detect network details -------------------------------------------------
PUBLIC_NIC="$(ip -4 route show default | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"
[[ -n ${PUBLIC_NIC} ]] || die "Could not detect the public network interface."

if [[ -z ${ENDPOINT} ]]; then
  ENDPOINT="$(curl -4 -fsS --max-time 5 https://api.ipify.org || true)"
  [[ -n ${ENDPOINT} ]] || ENDPOINT="$(ip -4 addr show "${PUBLIC_NIC}" | awk '/inet /{sub(/\/.*/,"",$2); print $2; exit}')"
fi
[[ -n ${ENDPOINT} ]] || die "Could not detect the public IP. Pass --endpoint <ip-or-hostname>."
info "Public interface: ${PUBLIC_NIC}, endpoint: ${ENDPOINT}:${PORT}"

# --- Keys & config ----------------------------------------------------------
umask 077
mkdir -p "${WG_DIR}" "${CLIENT_DIR}"
SERVER_PRIV="$(wg genkey)"
SERVER_PUB="$(printf '%s' "${SERVER_PRIV}" | wg pubkey)"

cat > "${WG_PARAMS}" <<PARAMS
SERVER_PUB=${SERVER_PUB}
ENDPOINT=${ENDPOINT}
PORT=${PORT}
DNS="${DNS}"
SUBNET_V4=${SUBNET_V4}
SUBNET_V6=${SUBNET_V6}
PUBLIC_NIC=${PUBLIC_NIC}
PARAMS

cat > "${WG_CONF}" <<CONF
[Interface]
Address = ${SUBNET_V4}.1/24, ${SUBNET_V6}1/64
ListenPort = ${PORT}
PrivateKey = ${SERVER_PRIV}
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -o ${PUBLIC_NIC} -j MASQUERADE; ip6tables -A FORWARD -i %i -j ACCEPT; ip6tables -t nat -A POSTROUTING -o ${PUBLIC_NIC} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -o ${PUBLIC_NIC} -j MASQUERADE; ip6tables -D FORWARD -i %i -j ACCEPT; ip6tables -t nat -D POSTROUTING -o ${PUBLIC_NIC} -j MASQUERADE
CONF

# --- Kernel forwarding ------------------------------------------------------
cat > /etc/sysctl.d/99-wireguard.conf <<SYSCTL
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
SYSCTL
sysctl --system >/dev/null

# --- Firewall ---------------------------------------------------------------
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow "${PORT}/udp"
  ufw route allow in on "${WG_IF}" out on "${PUBLIC_NIC}"
elif command -v firewall-cmd >/dev/null && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port="${PORT}/udp"
  firewall-cmd --permanent --add-masquerade
  firewall-cmd --reload
fi

# --- Start ------------------------------------------------------------------
systemctl enable --now "wg-quick@${WG_IF}"
info "WireGuard server is running on UDP port ${PORT}."
warn "If your cloud provider has a firewall/security group, allow inbound UDP ${PORT}."

# Create a first client so the user can connect right away.
"${SCRIPT_DIR}/add-client.sh" "client1"
