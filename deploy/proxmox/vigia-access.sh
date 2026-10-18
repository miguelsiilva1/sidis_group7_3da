# shellcheck shell=bash disable=SC2154
# Gives the vigia (in the cluster) the right to see the cluster's VMs and turn them on or off,
# and nothing else. Run by setup.sh on the Proxmox host, as root, after the configuration.
# Prints, for the cluster's Secret: the token, the host name in Proxmox's certificate, and the
# certificate authority that signed it (the token's secret only exists when it is created, so a
# new token replaces the old one on every run).

USER_ID=aisafe-vigia@pve
ROLE=AisafeVigia
PRIVS="VM.Audit VM.PowerMgmt"

if pveum role list --output-format json | grep -q "\"roleid\":\"$ROLE\""; then
  pveum role modify "$ROLE" --privs "$PRIVS"
else
  pveum role add "$ROLE" --privs "$PRIVS"
fi
pveum user list --output-format json | grep -q "\"userid\":\"$USER_ID\"" || pveum user add "$USER_ID" --comment "AISafe vigia"
for id in "${VM_IDS[@]}"; do
  pveum acl modify "/vms/$id" --users "$USER_ID" --roles "$ROLE"
done
# One token per cluster (named after its first VM), so several clusters can share a Proxmox host.
token=vigia-${VM_IDS[0]}
pveum user token remove "$USER_ID" "$token" &> /dev/null || true
secret=$(pveum user token add "$USER_ID" "$token" --privsep 0 --output-format json | sed -n 's/.*"value":"\([^"]*\)".*/\1/p')
[ -n "$secret" ] || { echo "Could not create the vigia's token." >&2; exit 1; }

echo "token=$USER_ID!$token=$secret"
echo "host=$(hostname -f)"
echo "ca=$(base64 -w0 /etc/pve/pve-root-ca.pem)"
