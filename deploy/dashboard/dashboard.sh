#!/usr/bin/env bash
# Demo dashboard: a live map of the machines, the replicas and the requests going between them, in
# the browser. For demonstrations only, not for production.
# It runs on this computer, outside the cluster, so it keeps showing everything while machines
# fail. Needs python3, and to reach the nodes and the virtual IPs (as deploy/manifests/failure-test.sh).
#   deploy/dashboard/dashboard.sh            # then open http://localhost:8099
#   PORT=9000 deploy/dashboard/dashboard.sh
set -euo pipefail
source "$(dirname "$0")/../lib.sh"
DIR=$DEPLOY_DIR/dashboard
PORT=${PORT:-8099}

log "Giving the dashboard access to the cluster"
kube apply -f - < "$DIR/rbac.yaml" > /dev/null
work=$(mktemp -d)
trap 'rm -rf "${work:?}"' EXIT
kube -n aisafe create token dashboard --duration=24h > "$work/token"
kube -n aisafe get configmap kube-root-ca.crt -o 'jsonpath={.data.ca\.crt}' > "$work/ca.crt"

# Everything the server needs from deploy/config.sh, as JSON
NODE_NAMES_LIST="${NODE_NAMES[*]}" NODE_IPS_LIST="${NODE_IPS[*]}" VM_IDS_LIST="${VM_IDS[*]:-}" \
NODE_SSH_JSON="$(printf '%s\n' "${NODE_SSH[@]}" | python3 -c 'import json, sys; print(json.dumps(sys.stdin.read().splitlines()))')" \
SSH_JSON="$(printf '%s\n' "${SSH[@]}" | python3 -c 'import json, sys; print(json.dumps(sys.stdin.read().splitlines()))')" \
WORK=$work PORT=$PORT NODE_USER=$NODE_USER MACHINES=$MACHINES PROXMOX=${PROXMOX:-} \
BRIDGE=${BRIDGE:-} CIDR=${CIDR:-} GATEWAY=${GATEWAY:-} \
VIP_AIRCRAFT_MAINTENANCE=$VIP_AIRCRAFT_MAINTENANCE VIP_AIRPORTS_ROUTES=$VIP_AIRPORTS_ROUTES VIP_FLIGHT_OPS=$VIP_FLIGHT_OPS \
VIP_LOGIN=$VIP_LOGIN \
python3 - > "$work/config.json" <<'EOF'
import json, os
e = os.environ
print(json.dumps({
    "port": int(e["PORT"]),
    "nodes": [{"name": n, "ip": i} for n, i in zip(e["NODE_NAMES_LIST"].split(), e["NODE_IPS_LIST"].split())],
    "vm_ids": e["VM_IDS_LIST"].split(),
    "machines": e["MACHINES"],
    "proxmox": e["PROXMOX"],
    "network": {"bridge": e["BRIDGE"], "cidr": e["CIDR"], "gateway": e["GATEWAY"]},
    "ssh": json.loads(e["SSH_JSON"]),
    "node_ssh": json.loads(e["NODE_SSH_JSON"]),
    "node_user": e["NODE_USER"],
    "vips": {"aircraft-maintenance": e["VIP_AIRCRAFT_MAINTENANCE"], "airports-routes": e["VIP_AIRPORTS_ROUTES"],
             "flight-ops": e["VIP_FLIGHT_OPS"], "login": e["VIP_LOGIN"]},
    "token_file": e["WORK"] + "/token",
    "ca_file": e["WORK"] + "/ca.crt",
}))
EOF

log "Dashboard on http://localhost:$PORT (Ctrl+C to stop)"
python3 "$DIR/server.py" "$work/config.json" "$DIR/index.html"
