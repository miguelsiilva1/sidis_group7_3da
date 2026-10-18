#!/usr/bin/env bash
# The vigia (watcher) of the cluster's machines. It runs on every node; only the one holding the
# lease acts, so the vigia keeps working when any node is lost. Every INTERVAL seconds, per node:
#   1. machine off -> mark the node aisafe/machine-off (its replicas leave the traffic at once
#      and wait for it, without moving) and turn the machine on;
#   2. down for REVIVE_TIMEOUT seconds, and its replicas certainly stopped (machine off, or
#      started again since it went down) -> isolate it (out-of-service): its replicas are reborn
#      on the other nodes. A node that is on but does not answer (network cut) is never isolated:
#      its replicas may be alive, and two replicas with the same name would both take writes;
#   3. back and ready -> remove the marks; with every node ready, give back the replicas that
#      moved away, one at a time, so every node has one replica of each service again.
# A replica's disk stays on the node where it was created: to move a replica, its disk is
# deleted and the replica is reborn empty, recovering its data from its peers.
# A frozen machine is restarted by its watchdog, not by the vigia.
#
# Only the machines know whether they are off. They are asked through a driver (MACHINE_DRIVER):
#   proxmox  the Proxmox API (the demo); a machine with the tag "maintenance" is not turned on,
#            and is isolated as soon as it is off;
#   none     nothing is known about the machines: no machine is turned on or isolated by the
#            vigia (a person isolates a lost machine), but replicas are still given back.
# On physical machines, a driver for their management boards (e.g. Redfish) would answer the
# same three questions: is it on and since when, is it in maintenance, turn it on.
set -uo pipefail

INTERVAL=${INTERVAL:-10}
LEASE_SECONDS=${LEASE_SECONDS:-30}
REVIVE_TIMEOUT=${REVIVE_TIMEOUT:-120}
MACHINE_DRIVER=${MACHINE_DRIVER:-none}
NS=${POD_NAMESPACE:-aisafe-vigia}
ME=${POD_NAME:-$(hostname)}

ISOLATED=node.kubernetes.io/out-of-service   # isolated: its replicas are reborn on other nodes
OFF=aisafe/machine-off                       # off: its replicas leave the traffic and wait
DOWN_SINCE=aisafe/down-since                 # node annotation: when the node went down
# Each node's machine id for the driver is in the node label aisafe/machine (set by setup.sh).

say() { echo "$(date -u +%H:%M:%S) $*"; }

# A request to a k3s server that just died would wait for minutes: give up after 5 s, and try
# again on the next round. (With that option, kubectl no longer finds the cluster by itself
# from inside a pod: the pod's credentials are given in a configuration file.)
SA=/var/run/secrets/kubernetes.io/serviceaccount
export KUBECONFIG=/tmp/kubeconfig
cat > "$KUBECONFIG" <<EOF
apiVersion: v1
kind: Config
clusters: [{name: cluster, cluster: {server: "https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT", certificate-authority: $SA/ca.crt}}]
users: [{name: vigia, user: {tokenFile: $SA/token}}]
contexts: [{name: vigia, context: {cluster: cluster, user: vigia, namespace: $NS}}]
current-context: vigia
EOF
kubectl() { command kubectl --request-timeout=5s "$@"; }

# --- Lease: only one vigia acts -----------------------------------------------------------------

# Takes or renews the lease; true when this vigia holds it. A lease that nobody renewed for
# LEASE_SECONDS, as timed by this vigia's own clock (the machines' clocks may differ), is taken
# over: its holder is gone. The update fails if someone else changed the lease in between, so
# two vigias never both win.
SEEN="" SEEN_AT=0
lead() {
  local lease now renewal
  now=$(date -u +%Y-%m-%dT%H:%M:%S.000000Z)
  if ! lease=$(kubectl -n "$NS" get lease vigia -o json 2> /dev/null); then
    kubectl -n "$NS" create -f - > /dev/null 2>&1 <<EOF
{"apiVersion": "coordination.k8s.io/v1", "kind": "Lease", "metadata": {"name": "vigia"},
 "spec": {"holderIdentity": "$ME", "leaseDurationSeconds": $LEASE_SECONDS, "renewTime": "$now"}}
EOF
    return
  fi
  renewal=$(jq -r '"\(.spec.holderIdentity) \(.spec.renewTime)"' <<< "$lease")
  if [ "$renewal" != "$SEEN" ]; then
    SEEN=$renewal SEEN_AT=$SECONDS
  fi
  if [ "${renewal%% *}" != "$ME" ] && [ $((SECONDS - SEEN_AT)) -le "$LEASE_SECONDS" ]; then
    return 1
  fi
  jq --arg me "$ME" --arg now "$now" '.spec.holderIdentity = $me | .spec.renewTime = $now' <<< "$lease" \
    | kubectl replace -f - > /dev/null 2>&1
}

# --- Machine drivers -------------------------------------------------------------------------------

# Proxmox: one request lists every VM with its state, uptime and tags.
pve() {
  curl -sf --max-time 5 --cacert /proxmox/ca.pem --resolve "$PROXMOX_HOST:8006:$PROXMOX_IP" \
    -H "Authorization: PVEAPIToken=$PROXMOX_TOKEN" "$@"
}

machines_refresh() {
  MACHINES_JSON='{"data": []}'
  if [ "$MACHINE_DRIVER" = proxmox ]; then
    MACHINES_JSON=$(pve "https://$PROXMOX_HOST:8006/api2/json/cluster/resources?type=vm") \
      || { say "Proxmox does not answer: nothing is done to the machines this time"; MACHINES_JSON='{"data": []}'; }
  fi
}

# "running UPTIME", "stopped 0", or "unknown 0" (then nothing that needs certainty is done).
machine_state() {   # machine_state ID
  jq -r --arg id "$1" '[.data[] | select((.vmid | tostring) == $id)][0]
    | if . == null then "unknown 0" else "\(.status) \(.uptime // 0)" end' <<< "$MACHINES_JSON"
}

machine_in_maintenance() {   # machine_in_maintenance ID
  jq -e --arg id "$1" '.data[] | select((.vmid | tostring) == $id) | (.tags // "") | split(";") | index("maintenance")' \
    <<< "$MACHINES_JSON" > /dev/null
}

machine_start() {   # machine_start ID
  local host
  host=$(jq -r --arg id "$1" '.data[] | select((.vmid | tostring) == $id) | .node' <<< "$MACHINES_JSON")
  [ -n "$host" ] && pve -X POST "https://$PROXMOX_HOST:8006/api2/json/nodes/$host/qemu/$1/status/start" > /dev/null
}

# --- One round ---------------------------------------------------------------------------------------

round() {
  local nodes now all_ready=yes name ready taints machine since state uptime isolated off down certain limit
  # "name Ready machine down-since taints..." per node
  nodes=$(kubectl get nodes -o json | jq -r '.items[] | [.metadata.name,
      ([.status.conditions[] | select(.type == "Ready") | .status][0]),
      (.metadata.labels["aisafe/machine"] // "-"), (.metadata.annotations["aisafe/down-since"] // "-"),
      ([.spec.taints[]?.key] | join(","))] | join(" ")') || return
  machines_refresh
  now=$(date +%s)

  while read -r name ready machine since taints; do
    read -r state uptime < <(machine_state "$machine")
    isolated=no off=no
    [[ ,$taints, == *,$ISOLATED,* ]] && isolated=yes
    [[ ,$taints, == *,$OFF,* ]] && off=yes

    # The "off" mark is on exactly while the machine is off.
    if [ "$state" = stopped ] && [ "$off" = no ]; then
      kubectl taint nodes "$name" "$OFF=yes:NoExecute" --overwrite > /dev/null \
        && say "$name off: its replicas leave the traffic"
    elif [ "$state" = running ] && [ "$off" = yes ]; then
      kubectl taint nodes "$name" "$OFF-" > /dev/null && say "$name on again: off mark removed"
    fi

    # --- On and ready: forget the downtime and remove the isolation ---
    # Right after a machine starts, k3s may still take the node as ready (it did not notice the
    # fall yet): a node that was down only counts as back after 30 s on.
    if [ "$ready" = True ] && [ "$state" != stopped ] \
        && { [ "$since" = - ] || [ "$state" = unknown ] || [ "$uptime" -ge 30 ]; }; then
      if [ "$since" != - ]; then
        kubectl annotate node "$name" "$DOWN_SINCE-" > /dev/null && say "$name is back and ready"
      fi
      if [ "$isolated" = yes ]; then
        kubectl taint nodes "$name" "$ISOLATED-" > /dev/null && say "$name: isolation removed"
        all_ready=no   # replicas are given back on a later round
      fi
      continue
    fi
    all_ready=no

    # --- Down: machine off, or k3s does not take the node as ready ---
    if [ "$since" = - ]; then
      since=$now
      kubectl annotate node "$name" "$DOWN_SINCE=$now" --overwrite > /dev/null
      say "$name down (machine: $state, ready: ${ready:-?})"
    fi
    down=$((now - since))

    if [ "$state" = stopped ] && ! machine_in_maintenance "$machine"; then
      if machine_start "$machine"; then
        say "$name was off: turned on"
      else
        say "$name is off and could not be turned on (down for ${down}s)"
      fi
    fi

    # Isolate only when the node's replicas certainly stopped: the machine is off, or it started
    # again after the node went down (starting killed the old replicas).
    certain=no
    [ "$state" = stopped ] && certain=yes
    [ "$state" = running ] && [ "$uptime" -lt "$down" ] && certain=yes
    limit=$REVIVE_TIMEOUT
    machine_in_maintenance "$machine" && limit=0
    if [ "$isolated" = no ] && [ "$certain" = yes ] && [ "$down" -ge "$limit" ]; then
      kubectl taint nodes "$name" "$ISOLATED=nodeshutdown:NoExecute" --overwrite > /dev/null \
        && say "$name down for ${down}s: isolated; its replicas will be reborn on the other nodes"
    fi
  done <<< "$nodes"

  free_stuck_replicas
  [ "$all_ready" = yes ] && give_back_one_replica
}

# A replica whose disk is on an isolated node can never start: its disk is deleted and it is
# reborn empty on another node. A replica waiting for a disk already deleted is recreated.
# Only when some node is ready and not isolated: otherwise the replica has nowhere to go, and
# deleting its disk would only lose data (e.g. every machine coming back after a power cut).
# The nodes are read again here, not taken from the start of the round: a node whose isolation
# was just removed must keep its disks.
free_stuck_replicas() {
  local pending nodes isolated_nodes disks claims ns pod pod_claims claim node
  pending=$(kubectl get pods -A --field-selector=status.phase=Pending -o json \
    | jq -r '.items[] | [.metadata.namespace, .metadata.name, ([.spec.volumes[]?.persistentVolumeClaim.claimName // empty] | join(","))] | join(" ")' \
    | awk 'NF == 3') || return
  [ -n "$pending" ] || return
  nodes=$(kubectl get nodes -o json | jq -r --arg t "$ISOLATED" '.items[] | [.metadata.name,
      (if ([.spec.taints[]?.key] | index($t)) then "isolated"
       elif ([.status.conditions[] | select(.type == "Ready") | .status][0]) == "True" then "healthy"
       else "down" end)] | join(" ")') || return
  grep -q ' healthy$' <<< "$nodes" || return
  isolated_nodes=$(awk '$2 == "isolated" { print $1 }' <<< "$nodes")
  disks=$(kubectl get pv -o json | jq -r '.items[] | [.spec.claimRef.namespace, .spec.claimRef.name,
      (.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0] // "-")] | join(" ")')
  claims=$(kubectl get pvc -A -o json | jq -r '.items[] | "\(.metadata.namespace) \(.metadata.name)"')
  while read -r ns pod pod_claims; do
    for claim in ${pod_claims//,/ }; do
      node=$(awk -v n="$ns" -v c="$claim" '$1 == n && $2 == c { print $3 }' <<< "$disks")
      if ! grep -qx "$ns $claim" <<< "$claims"; then
        kubectl -n "$ns" delete pod "$pod" --wait=false > /dev/null \
          && say "replica $pod was waiting for a deleted disk: recreated with a new one"
      elif [ -n "$node" ] && grep -qx "$node" <<< "$isolated_nodes"; then
        kubectl -n "$ns" delete pvc "$claim" --wait=false > /dev/null
        kubectl -n "$ns" delete pod "$pod" --wait=false > /dev/null \
          && say "replica $pod was bound to the disk of isolated node $node: disk deleted; reborn empty on another node"
      fi
    done
  done <<< "$pending"
}

# Finds a service with two replicas on one node and none on another, and deletes one of the two
# with its disk (bound to its node): it is reborn on the empty node and recovers its data from
# its peers. Only when every replica is ready, so two replicas never recover at the same time.
give_back_one_replica() {
  local nodes pods move ns pod claim
  nodes=$(kubectl get nodes -o jsonpath='{.items[*].metadata.name}') || return
  pods=$(kubectl get pods -A -l statefulset.kubernetes.io/pod-name -o json | jq -r '.items[] | [
      "\(.metadata.namespace)/\(.metadata.ownerReferences[0].name)", "\(.metadata.namespace)/\(.metadata.name)",
      (.spec.nodeName // "-"), ([.status.conditions[]? | select(.type == "Ready") | .status][0] // "False"),
      (if .metadata.deletionTimestamp then "deleting" else "-" end)] | join(" ")') || return
  move=$(awk -v nodes="$nodes" '
    $4 != "True" || $5 != "-" { busy = 1 }
    { count[$1 " " $3]++; pod[$1 " " $3] = $2; svc[$1] = 1 }
    END {
      if (busy) exit
      n = split(nodes, node, " ")
      for (s in svc)
        for (i = 1; i <= n; i++)
          if (!count[s " " node[i]])
            for (j = 1; j <= n; j++)
              if (count[s " " node[j]] >= 2) { print pod[s " " node[j]]; exit }
    }' <<< "$pods")
  [ -n "$move" ] || return
  ns=${move%%/*} pod=${move#*/}
  for claim in $(kubectl -n "$ns" get pod "$pod" -o jsonpath='{.spec.volumes[*].persistentVolumeClaim.claimName}'); do
    kubectl -n "$ns" delete pvc "$claim" --wait=false > /dev/null
  done
  kubectl -n "$ns" delete pod "$pod" --wait=false > /dev/null \
    && say "replica $pod shared a node with another replica of its service: given back to the node without one (new disk: recovers from its peers)"
}

say "vigia $ME started (machines: $MACHINE_DRIVER)"
leading=no
while :; do
  if lead; then
    [ "$leading" = yes ] || say "this vigia now acts"
    leading=yes
    round
  else
    [ "$leading" = no ] || say "another vigia acts now"
    leading=no
  fi
  sleep "$INTERVAL"
done
