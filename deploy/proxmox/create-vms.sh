# shellcheck shell=bash disable=SC2154
# Creates or updates the cluster's VMs on Proxmox. Run by setup.sh on the Proxmox host, as root,
# after the configuration (config.sh); not meant to be run by hand.
# Prints "reboot ID" for each VM with changes that only apply when it starts again: setup.sh
# restarts those one at a time, so the cluster never loses more than one node.

IMAGE_URL=https://cloud.debian.org/images/cloud/trixie/latest/debian-13-generic-amd64.qcow2
IMAGE_SUMS_URL=https://cloud.debian.org/images/cloud/trixie/latest/SHA512SUMS
IMAGE=/var/lib/vz/import/$(basename "$IMAGE_URL")
TAGS=aisafe

say() { echo "$*" >&2; }

# Debian 13 "generic" (full kernel, with IPVS and VXLAN), checked against the official sums.
if [ ! -f "$IMAGE" ]; then
  say "Downloading the Debian 13 image"
  mkdir -p "$(dirname "$IMAGE")"
  curl -fsSL -o "$IMAGE.part" "$IMAGE_URL"
  expected=$(curl -fsSL "$IMAGE_SUMS_URL" | awk -v f="$(basename "$IMAGE_URL")" '$2 == f { print $1 }')
  [ -n "$expected" ] || { say "No checksum found for the Debian image."; exit 1; }
  echo "$expected  $IMAGE.part" | sha512sum -c --quiet
  mv "$IMAGE.part" "$IMAGE"
fi

for i in "${!NODES[@]}"; do
  read -r name ip <<< "${NODES[$i]}"
  id=${VM_IDS[$i]}
  if ! qm status "$id" &> /dev/null; then
    say "Creating VM $id ($name, $ip)"
    # The nodes accept the keys that log in to Proxmox as root (this computer's, among them).
    qm create "$id" --name "$name" --tags "$TAGS" --ostype l26 --cpu host \
      --cores "$CORES" --memory "$MEMORY_MB" \
      --scsihw virtio-scsi-single --scsi0 "$STORAGE:0,import-from=$IMAGE,discard=on" \
      --ide2 "$STORAGE:cloudinit" --boot order=scsi0 --serial0 socket --vga serial0 \
      --net0 "virtio,bridge=$BRIDGE" \
      --ipconfig0 "ip=$ip/$CIDR,gw=$GATEWAY" --nameserver "$DNS" \
      --ciuser "$NODE_USER" --sshkeys /root/.ssh/authorized_keys --agent 1 \
      --watchdog model=i6300esb,action=reset --onboot 1 --startup order=1 > /tmp/aisafe-qm.log 2>&1 \
      || { cat /tmp/aisafe-qm.log >&2; exit 1; }
    qm disk resize "$id" scsi0 "${DISK_GB}G" > /dev/null
  else
    # Keeps the "maintenance" mark, if the VM has it (see deploy/vigia/vigia.sh).
    tags=$TAGS
    grep -q '^tags:.*maintenance' "/etc/pve/qemu-server/$id.conf" && tags="$TAGS;maintenance"
    qm set "$id" --cores "$CORES" --memory "$MEMORY_MB" --onboot 1 --startup order=1 --tags "$tags" \
      --watchdog model=i6300esb,action=reset > /dev/null
    qm pending "$id" | grep -q '^new ' && qm status "$id" | grep -q running && echo "reboot $id"
  fi
  qm status "$id" | grep -q running || qm start "$id" > /dev/null
done

# The vigia used to run here, before it moved into the cluster: it must not act twice.
if systemctl is-enabled sidis-k3s-vigia.timer &> /dev/null; then
  systemctl disable --now sidis-k3s-vigia.timer &> /dev/null || true
fi
true
