#!/usr/bin/env bash
# Removes the environment described in deploy/config.sh, to start again from nothing:
#   - MACHINES=proxmox: destroys the VMs (with their disks) and the vigia's access to Proxmox;
#   - MACHINES=existing: uninstalls k3s from the machines (the machines stay).
# Everything in the cluster is lost, the data of the services included.
#   deploy/teardown.sh          # asks first
#   deploy/teardown.sh --yes
set -euo pipefail
source "$(dirname "$0")/lib.sh"

if [ "${1:-}" != --yes ]; then
  if [ "$MACHINES" = proxmox ]; then what="VMs ${VM_IDS[*]} on $PROXMOX"; else what="k3s on ${NODE_IPS[*]}"; fi
  read -r -p "This destroys $what, and every service's data with it. Type 'yes' to go on: " answer
  [ "$answer" = yes ] || { echo "Nothing done."; exit 1; }
fi

if [ "$MACHINES" = proxmox ]; then
  log "Destroying VMs ${VM_IDS[*]} and the vigia's access"
  cat "$CONFIG" - <<'EOF' | "${SSH[@]}" "$PROXMOX" "bash -euo pipefail -s"
# The vigia first: otherwise it would turn the VMs back on as they stop
pveum user token remove aisafe-vigia@pve "vigia-${VM_IDS[0]}" &> /dev/null || true
for id in "${VM_IDS[@]}"; do
  pveum acl delete "/vms/$id" --users aisafe-vigia@pve --roles AisafeVigia &> /dev/null || true
done
for id in "${VM_IDS[@]}"; do
  qm status "$id" &> /dev/null || continue
  qm stop "$id" --timeout 60 > /dev/null
  qm destroy "$id" --purge 1 --destroy-unreferenced-disks 1
done
EOF
else
  for ip in "${NODE_IPS[@]}"; do
    log "Uninstalling k3s from $ip"
    on_node "$ip" "[ ! -x /usr/local/bin/k3s-uninstall.sh ] || /usr/local/bin/k3s-uninstall.sh > /dev/null"
  done
fi
for ip in "${NODE_IPS[@]}"; do
  ssh-keygen -q -R "$ip" -f "$KNOWN_HOSTS" &> /dev/null || true
done
log "Done. deploy/setup.sh builds it again."
