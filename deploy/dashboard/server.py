#!/usr/bin/env python3
"""Demo dashboard of AISafe: a live map of machines, replicas and requests. Not for production.

Started by dashboard.sh. Every second it reads the state of the cluster (nodes, pods, which node
announces each virtual IP, which vigia acts) and, on Proxmox, of the VMs. It follows the logs of the
entries (Envoy: one line per request and per attempt) and of the replicas (each forwarding attempt),
groups the lines of a request by its correlation id, and sends everything to the browser
(server-sent events). It also sends requests and causes failures when the page asks.
Python standard library only.
"""
import collections
import http.server
import json
import queue
import random
import re
import ssl
import string
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

CFG = json.load(open(sys.argv[1]))
PAGE = sys.argv[2]
SERVICES = ("aircraft-maintenance", "airports-routes", "flight-ops", "login", "entry", "vigia")
CLIENT = "client"


def now_ms():
    return int(time.time() * 1000)


def iso_ms(text):
    try:
        return int(datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp() * 1000)
    except ValueError:
        return now_ms()


# --- Browser connections --------------------------------------------------------------------------

clients = []
clients_lock = threading.Lock()
history = collections.deque(maxlen=150)   # last feed lines, for a page opened (or reloaded) later


def broadcast(message):
    data = json.dumps(message)
    with clients_lock:
        if message["type"] == "feed":
            history.append(data)
        for q in clients:
            q.put(data)


# --- Kubernetes API -------------------------------------------------------------------------------

class Kube:
    """The API of k3s, on whichever node answers (the control plane runs on all of them)."""

    def __init__(self):
        self.ips = [n["ip"] for n in CFG["nodes"]]
        self.good = 0
        self.token = open(CFG["token_file"]).read().strip()
        self.ctx = ssl.create_default_context(cafile=CFG["ca_file"])

    def open(self, method, path, body=None, timeout=2, prefer=None):
        """prefer: the IP to try first (for a pod's log, its own node: a frozen node then stalls
        only the logs of its own pods)."""
        last = None
        first = self.ips.index(prefer) if prefer in self.ips else self.good
        for i in range(len(self.ips)):
            ip = self.ips[(first + i) % len(self.ips)]
            req = urllib.request.Request(f"https://{ip}:6443{path}", method=method,
                                         data=json.dumps(body).encode() if body is not None else None,
                                         headers={"Authorization": f"Bearer {self.token}",
                                                  "Content-Type": "application/strategic-merge-patch+json"
                                                  if method == "PATCH" else "application/json"})
            try:
                resp = urllib.request.urlopen(req, context=self.ctx, timeout=timeout)
                if prefer is None:
                    self.good = (first + i) % len(self.ips)
                return resp
            except urllib.error.HTTPError:
                raise
            except OSError as e:
                last = e
        raise last

    def get(self, path):
        with self.open("GET", path) as resp:
            return json.load(resp)


kube = Kube()

# --- State of the cluster -------------------------------------------------------------------------

state = {"nodes": [], "pods": [], "vips": {}, "vigia": None, "vms": {}}
state_lock = threading.Lock()
pod_by_ip = {}       # ip -> (pod name, seen at): also pods that are gone, for late log lines
node_of_pod = {}     # pod name -> node


def app_of(pod):
    for s in SERVICES:
        if pod.startswith(s + "-"):
            return s
    return pod


def read_pods():
    pods = []
    for ns in ("aisafe", "aisafe-vigia", "metallb-system"):
        for p in kube.get(f"/api/v1/namespaces/{ns}/pods")["items"]:
            conds = {c["type"]: c["status"] for c in p["status"].get("conditions", [])}
            statuses = p["status"].get("containerStatuses", [])
            # Why it is not running: ContainerCreating, CrashLoopBackOff, ImagePullBackOff, Unschedulable...
            reason = next((st["state"]["waiting"].get("reason") for st in statuses if "waiting" in st.get("state", {})), None)
            if not reason and conds.get("PodScheduled") == "False":
                reason = "Unschedulable"
            pods.append({
                "name": p["metadata"]["name"], "ns": ns, "uid": p["metadata"]["uid"],
                "app": ("metallb-" + p["metadata"]["name"].split("-")[0]) if ns == "metallb-system"
                       else app_of(p["metadata"]["name"]),
                "node": p["spec"].get("nodeName"), "ip": p["status"].get("podIP"),
                "phase": p["status"].get("phase"), "ready": conds.get("Ready") == "True",
                "terminating": "deletionTimestamp" in p["metadata"],
                "restarts": sum(s.get("restartCount", 0) for s in statuses),
                "created": p["metadata"].get("creationTimestamp"), "reason": reason,
            })
    return pods


def read_nodes():
    nodes = []
    for n in kube.get("/api/v1/nodes")["items"]:
        conds = {c["type"]: c["status"] for c in n["status"].get("conditions", [])}
        nodes.append({"name": n["metadata"]["name"],
                      "ready": conds.get("Ready") == "True",
                      "taints": [t["key"] for t in n["spec"].get("taints", [])],
                      # Set by the vigia while the node is down (epoch seconds)
                      "down_since": n["metadata"].get("annotations", {}).get("aisafe/down-since")})
    return nodes


def read_vips():
    vips = {}
    try:
        items = kube.get("/apis/metallb.io/v1beta1/namespaces/metallb-system/servicel2statuses")["items"]
    except (OSError, urllib.error.HTTPError):
        return vips
    for s in items:
        labels = s["metadata"].get("labels", {})
        svc = labels.get("metallb.io/service-name", "").removesuffix("-vip")
        if labels.get("metallb.io/service-namespace") == "aisafe" and svc:
            vips[svc] = labels.get("metallb.io/node")
    return vips


def read_vigia():
    try:
        return kube.get("/apis/coordination.k8s.io/v1/namespaces/aisafe-vigia/leases/vigia")["spec"].get("holderIdentity")
    except (OSError, urllib.error.HTTPError):
        return None


def read_vms():
    """On Proxmox: whether each node's VM is on (a frozen VM is "running" for Proxmox)."""
    if CFG["machines"] != "proxmox" or not CFG["proxmox"]:
        return {}
    try:
        out = subprocess.run(CFG["ssh"] + [CFG["proxmox"], "pvesh get /cluster/resources --type vm --output-format json"],
                             capture_output=True, text=True, timeout=6).stdout
        by_id = {str(v["vmid"]): v for v in json.loads(out)}
    except (OSError, ValueError, subprocess.SubprocessError):
        return {}
    vms = {}
    for node, vm_id in zip(CFG["nodes"], CFG["vm_ids"]):
        v = by_id.get(vm_id)
        if v:
            vms[node["name"]] = {"id": vm_id, "status": v.get("status"), "uptime": v.get("uptime", 0),
                                 "maintenance": "maintenance" in (v.get("tags") or "").split(";")}
    return vms


def describe_changes(old, new):
    """Feed lines for what changed between two readings."""
    lines = []
    old_pods = {p["name"]: p for p in old["pods"]}
    new_pods = {p["name"]: p for p in new["pods"]}
    for name, p in new_pods.items():
        o = old_pods.get(name)
        if o is None:
            lines.append(("pod", f"{name} created on {p['node'] or '(no node yet)'}"))
        elif o["uid"] != p["uid"]:
            lines.append(("pod", f"{name} recreated on {p['node'] or '(no node yet)'}"))
        elif p["terminating"] and not o["terminating"]:
            lines.append(("fail", f"{name} stopping"))
        elif p["ready"] != o["ready"]:
            lines.append(("ok" if p["ready"] else "fail", f"{name} {'ready' if p['ready'] else 'not ready'}"))
        elif p["node"] != o["node"] and p["node"]:
            lines.append(("pod", f"{name} placed on {p['node']}"))
    for name in old_pods.keys() - new_pods.keys():
        lines.append(("fail", f"{name} gone"))
    old_nodes = {n["name"]: n for n in old["nodes"]}
    for n in new["nodes"]:
        o = old_nodes.get(n["name"])
        if o and o["ready"] != n["ready"]:
            lines.append(("ok" if n["ready"] else "fail", f"node {n['name']} {'ready' if n['ready'] else 'NOT ready'}"))
        if o and set(o["taints"]) != set(n["taints"]):
            added = set(n["taints"]) - set(o["taints"])
            removed = set(o["taints"]) - set(n["taints"])
            for t in added:
                lines.append(("fail", f"node {n['name']} marked {t}"))
            for t in removed:
                lines.append(("ok", f"node {n['name']} no longer marked {t}"))
    for svc, node in new["vips"].items():
        if old["vips"].get(svc) and old["vips"].get(svc) != node:
            lines.append(("vip", f"virtual IP of {svc} moved from {old['vips'][svc]} to {node}"))
    for node, vm in new["vms"].items():
        o = old["vms"].get(node)
        if o and o["status"] != vm["status"]:
            lines.append(("ok" if vm["status"] == "running" else "fail", f"VM of {node}: {vm['status']}"))
    if old["vigia"] and new["vigia"] and old["vigia"] != new["vigia"]:
        lines.append(("vigia", f"vigia {new['vigia']} now acts"))
    return lines


def poll_state():
    last_vms = 0
    vms = {}
    while True:
        try:
            if time.time() - last_vms > 2:
                vms = read_vms()
                last_vms = time.time()
            new = {"nodes": read_nodes(), "pods": read_pods(), "vips": read_vips(), "vigia": read_vigia(), "vms": vms}
            with state_lock:
                old = dict(state)
                state.update(new)
                for p in new["pods"]:
                    if p["ip"]:
                        pod_by_ip[p["ip"]] = (p["name"], time.time())
                    node_of_pod[p["name"]] = p["node"]
            for kind, text in describe_changes(old, new) if old["nodes"] else []:
                broadcast({"type": "feed", "kind": kind, "text": text, "t": now_ms()})
            broadcast({"type": "state", **new, "config": {k: CFG[k] for k in
                                                          ("nodes", "vips", "machines", "proxmox", "network")}})
            ensure_followers(new["pods"])
        except Exception as e:  # keep polling through any failure of the cluster
            broadcast({"type": "status", "ok": False, "text": f"cluster not answering: {e}"})
        time.sleep(1)


# --- Requests: grouping log lines by correlation id ---------------------------------------------

journeys = {}
journeys_lock = threading.Lock()


def add_event(key, event):
    if not key or key in ("-", "null", "None"):
        broadcast({"type": "background", **event})
        return
    with journeys_lock:
        j = journeys.setdefault(key, {"events": [], "last": 0})
        j["events"].append(event)
        j["last"] = time.time()


def flush_journeys():
    """A request is complete when no new line about it came for a moment."""
    while True:
        time.sleep(0.2)
        done = []
        with journeys_lock:
            for key, j in list(journeys.items()):
                if time.time() - j["last"] > 0.8:
                    done.append((key, j))
                    del journeys[key]
        for key, j in done:
            broadcast({"type": "journey", "id": key, "events": sorted(j["events"], key=lambda e: e["t"])})


def pod_of_ip(ip):
    if not ip:
        return None
    hit = pod_by_ip.get(ip.split(":")[0])
    return hit[0] if hit else None


def pod_of_peer(url):
    host = re.sub(r"^https?://", "", url).split(":")[0].split("/")[0]
    return host.split(".")[0]


APP_LINE = re.compile(r"^(\S+)\s+(\w+)\s+\[([^,\]]*),([^\]]*)\]\s+\d+\s+---\s+\[[^\]]*\]\s+\[[^\]]*\]\s+(\S+)\s+:\s+(.*)$")
FORWARD = re.compile(r"^(\w+) (\S+) forwarded to peer (\S+) \(attempt (\d+)\)(?:: (\d+)| failed: (.*))$")
SENT = re.compile(r"^(\w+) (\S+) sent to peer (\S+)(?:: (\d+)| failed: (.*))$")
SKIPPED = re.compile(r"^Peer (\S+) skipped for (\w+) (\S+): circuit open")
USE_CASE = re.compile(r"^Executing use case: class \S*\.(\w+)")


def peer_kind(method, path):
    if path.startswith("/internal/replicas/"):
        return "sync" if method == "GET" else "copy"
    if path.startswith("/internal/accounts/"):
        return "forward"
    return "forward"


circuit_said = {}   # (pod, peer) -> when the feed last said the circuit opened


def handle_app_line(pod, line):
    m = APP_LINE.match(line)
    if not m:
        return
    t, level, node_id, corr, logger, msg = m.groups()
    base = {"t": iso_ms(t), "pod": pod}
    if f := FORWARD.match(msg):
        method, path, peer, attempt, code, error = f.groups()
        add_event(corr, {**base, "kind": "peer", "what": peer_kind(method, path), "m": method, "p": path,
                         "peer": pod_of_peer(peer), "attempt": int(attempt),
                         "code": int(code) if code else None, "error": error})
    elif s := SENT.match(msg):
        method, path, peer, code, error = s.groups()
        add_event(corr, {**base, "kind": "peer", "what": peer_kind(method, path), "m": method, "p": path,
                         "peer": pod_of_peer(peer), "attempt": 1, "code": int(code) if code else None, "error": error})
    elif k := SKIPPED.match(msg):
        peer, method, path = k.groups()
        add_event(corr, {**base, "kind": "peer", "what": peer_kind(method, path), "m": method, "p": path,
                         "peer": pod_of_peer(peer), "attempt": 0, "code": None, "error": "circuit open: not asked"})
    elif u := USE_CASE.match(msg):
        if corr:
            add_event(corr, {**base, "kind": "usecase", "name": u.group(1)})
    elif msg.startswith("Circuit opened for peer"):
        peer = pod_of_peer(msg.split()[4])
        broadcast({"type": "circuit", "from": pod, "to": peer, "open": True, "ms": int(msg.split()[6]), "t": base["t"]})
        # Said once a minute per pair: the circuit opens again at every probe while the peer is down
        if time.time() - circuit_said.get((pod, peer), 0) > 60:
            circuit_said[(pod, peer)] = time.time()
            broadcast({"type": "feed", "kind": "fail", "t": base["t"],
                       "text": f"{pod} stops asking {peer} for a while (circuit open)"})
    elif msg.endswith("sent a request: circuit closed"):
        broadcast({"type": "circuit", "from": pod, "to": pod_of_peer(msg.split()[1]), "open": False, "t": base["t"]})
    elif msg.startswith("Shutting down") or msg.startswith("Started ") or "given up" in msg:
        broadcast({"type": "feed", "kind": "pod", "t": base["t"], "text": f"{pod}: {msg[:140]}"})


def handle_entry_line(pod, line):
    try:
        e = json.loads(line)
    except ValueError:
        return
    key = e.get("corr") if e.get("corr") not in (None, "-") else e.get("rid")
    up = e.get("up") if e.get("up") not in (None, "-") else None
    add_event(key, {"t": iso_ms(e.get("t", "")), "kind": "entry" if e.get("k") == "request" else "attempt",
                    "entry": pod, "node": node_of_pod.get(pod), "svc": e.get("svc"), "m": e.get("m"),
                    "p": e.get("p"), "code": e.get("code"), "flags": e.get("flags"), "up": pod_of_ip(up),
                    "n": e.get("n"), "ms": e.get("ms"), "from": pod_of_ip(e.get("from")) or CLIENT})


def handle_vigia_line(pod, line):
    text = line.split(" ", 1)[1] if re.match(r"^\d\d:\d\d:\d\d ", line) else line
    if "started (machines" in text:
        return
    broadcast({"type": "feed", "kind": "vigia", "t": now_ms(), "text": f"vigia: {text}"})


followers = {}   # pod uid -> thread


def follow(pod):
    """Follows one pod's log until the pod is gone."""
    handler = {"entry": handle_entry_line, "vigia": handle_vigia_line}.get(pod["app"], handle_app_line)
    path = f"/api/v1/namespaces/{pod['ns']}/pods/{pod['name']}/log?follow=true&sinceSeconds="
    node_ip = next((n["ip"] for n in CFG["nodes"] if n["name"] == pod["node"]), None)
    last = time.time() - 2
    while True:
        with state_lock:
            alive = any(p["uid"] == pod["uid"] for p in state["pods"])
        if not alive:
            break
        try:
            # Again from the last line read (a line may come twice; none is lost)
            since = max(1, int(time.time() - last) + 1)
            with kube.open("GET", path + str(since), timeout=60, prefer=node_ip) as resp:
                for raw in resp:
                    last = time.time()
                    handler(pod["name"], raw.decode(errors="replace").rstrip("\n"))
        except Exception:
            time.sleep(1)
    followers.pop(pod["uid"], None)


def ensure_followers(pods):
    for p in pods:
        if p["phase"] == "Running" and p["uid"] not in followers and p["app"] in SERVICES:
            t = threading.Thread(target=follow, args=(p,), daemon=True)
            followers[p["uid"]] = t
            t.start()


# --- Actions from the page ------------------------------------------------------------------------

session = {"tokens": {}, "last": "CS-TKA", "traffic": False}


def call(method, url, body=None, token=None, corr=None, timeout=15):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if corr:
        headers["X-Correlation-Id"] = corr
    req = urllib.request.Request(url, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        # 503 with Retry-After: the service is not sure yet, and asks the client to try again
        retry = " retry-after" if e.code == 503 and e.headers.get("Retry-After") else ""
        return e.code, e.read().decode(errors="replace") + retry
    except OSError as e:
        return 0, str(e)


def token(user="atcc"):
    if user not in session["tokens"]:
        code, body = call("POST", f"http://{CFG['vips']['login']}/api/auth/login",
                          {"username": user, "password": user + "123"})
        if code == 200:
            session["tokens"][user] = json.loads(body)["token"]
    return session["tokens"].get(user)


def base_url(svc, target):
    """vip: through the virtual IP and an entry; 0/1/2: straight to that replica (its node port)."""
    if target in (None, "", "vip"):
        return f"http://{CFG['vips'][svc]}"
    port = {"aircraft-maintenance": 30810, "airports-routes": 30820, "flight-ops": 30830}[svc] + int(target)
    return f"http://{CFG['nodes'][0]['ip']}:{port}"


def new_corr(prefix):
    return prefix + "-" + "".join(random.choices(string.ascii_lowercase + string.digits, k=6))


def letters(n):
    return "".join(random.choices(string.ascii_uppercase, k=n))


def do_request(op, target=None, prefix="demo"):
    corr = new_corr(prefix)
    t0 = now_ms()
    am = base_url("aircraft-maintenance", target)
    if op == "login":
        code, body = call("POST", f"http://{CFG['vips']['login']}/api/auth/login",
                       {"username": "atcc", "password": "atcc123"}, corr=corr)
    elif op == "create":
        reg = f"DM-{letters(3)}"
        code, body = call("POST", f"{am}/api/aircrafts", {
            "registrationNumber": reg, "modelName": "Airbus A320neo", "manufacturingDate": "2021-01-01",
            "seatCapacity": 150, "range": 5000.0, "status": "AVAILABLE", "features": []}, token(), corr)
        if code == 201:
            session["last"] = reg
    elif op == "read":
        code, body = call("GET", f"{am}/api/aircrafts/{session['last']}", token=token(), corr=corr)
    elif op == "delete":
        code, body = call("DELETE", f"{am}/api/aircrafts/{session['last']}", token=token(), corr=corr)
        if code == 204:
            session["last"] = "CS-TKA"
    elif op == "list":
        code, body = call("GET", f"{am}/api/aircrafts?size=1000", token=token(), corr=corr)
    elif op == "route":
        code, body = call("GET", f"{base_url('airports-routes', target)}/api/routes/LIS/OPO", token=token(), corr=corr)
    elif op == "compatible":
        code, body = call("GET", f"http://{CFG['vips']['airports-routes']}/api/routes/compatible?aircraft=CS-TKA",
                       token=token(), corr=corr)
    elif op == "schedule":
        # The Saga: flight-ops asks airports-routes (route) and aircraft-maintenance (aircraft), then
        # sends both a copy of the flight. A random hour in 2027-2029, so that flights do not overlap.
        dep = datetime(2027, 1, 1, tzinfo=timezone.utc) + timedelta(hours=random.randrange(3 * 365 * 24))
        code, body = call("POST", f"{base_url('flight-ops', target)}/api/flights", {
            "aircraftId": "CS-TKA", "originIataCode": "LIS", "destinationIataCode": "OPO",
            "departureDateTime": dep.isoformat(), "arrivalDateTime": (dep + timedelta(hours=1)).isoformat()},
            token(), corr)
    elif op == "flights":
        code, body = call("GET", f"{base_url('flight-ops', target)}/api/flights?aircraftId=CS-TKA", token=token(), corr=corr)
    else:
        return {"ok": False, "text": f"unknown request {op}"}
    if code in (401, 403):
        session["tokens"].clear()
    via = "virtual IP" if target in (None, "", "vip") else f"replica {target}"
    if via != "virtual IP":
        # Straight to a replica, no entry on the way: this line stands for the client's request.
        svc = {"route": "airports-routes", "schedule": "flight-ops", "flights": "flight-ops"}.get(op, "aircraft-maintenance")
        add_event(corr, {"t": t0, "kind": "direct", "pod": f"{svc}-{target}", "code": code})
    broadcast({"type": "sent", "id": corr, "op": op, "via": via, "code": code, "t": now_ms(),
               "retry": body.endswith(" retry-after"), "error": body[:160] if code == 0 else None})
    return {"ok": 200 <= code < 300, "text": f"{op} via {via}: {code or 'no answer'}", "id": corr}


def traffic():
    """Steady demo traffic through the virtual IPs: reads, lists, aircraft created and deleted, and flights scheduled.
    Each request on its own, so that a slow one (an entry trying another replica) does not hold the rest."""
    while session["traffic"]:
        op = random.choice(["read", "read", "read", "list", "route", "create", "delete", "compatible", "flights", "schedule"])
        if op == "delete" and not session["last"].startswith("DM-"):
            op = "create"
        threading.Thread(target=do_request, args=(op, "vip", "load"), daemon=True).start()
        time.sleep(1.5)


def ssh_node(node, command):
    ip = next(n["ip"] for n in CFG["nodes"] if n["name"] == node)
    subprocess.Popen(CFG["node_ssh"] + [f"{CFG['node_user']}@{ip}", f"sudo sh -c '{command}'"],
                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def do_action(a):
    op = a.get("op")
    if op == "traffic":
        session["traffic"] = bool(a.get("on"))
        if session["traffic"]:
            threading.Thread(target=traffic, daemon=True).start()
        return {"ok": True, "text": "traffic " + ("on" if session["traffic"] else "off")}
    if op == "kill":
        pod = a["pod"]
        ns = "aisafe-vigia" if pod.startswith("vigia-") else "aisafe"
        kube.open("DELETE", f"/api/v1/namespaces/{ns}/pods/{pod}", {"gracePeriodSeconds": 0}).close()
        broadcast({"type": "feed", "kind": "fail", "t": now_ms(), "text": f"dashboard: {pod} killed"})
        return {"ok": True, "text": f"{pod} killed"}
    if op in ("node-off", "node-freeze"):
        node = a["node"]
        ssh_node(node, "sysctl -q kernel.panic=0; echo " + ("o" if op == "node-off" else "c") + " > /proc/sysrq-trigger")
        what = "powered off" if op == "node-off" else "frozen"
        broadcast({"type": "feed", "kind": "fail", "t": now_ms(), "text": f"dashboard: {node} {what}"})
        return {"ok": True, "text": f"{node} {what}"}
    if op == "restart":
        svc = a["svc"]
        kind = "daemonsets" if svc == "entry" else "statefulsets"
        kube.open("PATCH", f"/apis/apps/v1/namespaces/aisafe/{kind}/{svc}", {"spec": {"template": {"metadata": {
            "annotations": {"kubectl.kubernetes.io/restartedAt": datetime.now().astimezone().isoformat()}}}}}).close()
        broadcast({"type": "feed", "kind": "pod", "t": now_ms(), "text": f"dashboard: {svc} restarting, one replica at a time"})
        return {"ok": True, "text": f"{svc} restarting"}
    return do_request(op, a.get("target"))


# --- Web server -----------------------------------------------------------------------------------

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path == "/events":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            q = queue.Queue()
            with clients_lock:
                for data in history:
                    q.put(data)
                clients.append(q)
            try:
                while True:
                    try:
                        data = q.get(timeout=15)
                        self.wfile.write(f"data: {data}\n\n".encode())
                    except queue.Empty:
                        self.wfile.write(b": keep-alive\n\n")
                    self.wfile.flush()
            except OSError:
                pass
            finally:
                with clients_lock:
                    clients.remove(q)
            return
        body = open(PAGE, "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        try:
            result = do_action(json.loads(self.rfile.read(length) or b"{}"))
        except Exception as e:
            result = {"ok": False, "text": str(e)}
        body = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    threading.Thread(target=poll_state, daemon=True).start()
    threading.Thread(target=flush_journeys, daemon=True).start()
    server = http.server.ThreadingHTTPServer(("127.0.0.1", CFG["port"]), Handler)
    server.daemon_threads = True
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
