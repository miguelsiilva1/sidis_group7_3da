package aisafe.shared.infrastructure.replication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The replicas of this service, in a fixed order (a ring), built from the same peer list
 * every replica is given ({@code aisafe.peers}, this replica included).
 *
 * <p>A replica's node id is the first label of its host name (e.g. {@code aircraft-maintenance-1}
 * for {@code http://aircraft-maintenance-1.aircraft-maintenance...}). The successor of a replica
 * is the next one in the ring. A record is held by the replica that created it (its home) and
 * by the home's successor (replication factor 2).</p>
 */
@Component
public class ReplicaRing {

    private final String self;
    private final List<Member> members;

    public ReplicaRing(@Value("${aisafe.node.id}") String self,
                       @Value("${aisafe.peers:}") List<String> peers) {
        this.self = self;
        List<Member> list = new ArrayList<>();
        for (String peer : peers) {
            String url = peer.trim();
            if (!url.isEmpty()) {
                list.add(new Member(nodeIdOf(url), url));
            }
        }
        list.sort(Comparator.comparing(Member::nodeId));
        this.members = List.copyOf(list);
    }

    static String nodeIdOf(String url) {
        String host = URI.create(url).getHost();
        int dot = host.indexOf('.');
        return dot < 0 ? host : host.substring(0, dot);
    }

    /** Replication needs this replica in the ring and at least one other replica. */
    public boolean isEnabled() {
        return members.size() > 1 && members.stream().anyMatch(m -> m.nodeId().equals(self));
    }

    public String self() {
        return self;
    }

    public List<Member> others() {
        return members.stream().filter(m -> !m.nodeId().equals(self)).toList();
    }

    public Optional<Member> member(String nodeId) {
        return members.stream().filter(m -> m.nodeId().equals(nodeId)).findFirst();
    }

    /** The next replica in the ring; a replica that is not in the ring has no successor. */
    public Optional<String> successorOf(String nodeId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).nodeId().equals(nodeId)) {
                return Optional.of(members.get((i + 1) % members.size()).nodeId());
            }
        }
        return Optional.empty();
    }

    /** The replicas that hold a record whose home is {@code homeNodeId}: the home and its successor. */
    public List<String> holdersOf(String homeNodeId) {
        List<String> holders = new ArrayList<>();
        holders.add(homeNodeId);
        successorOf(homeNodeId).filter(s -> !s.equals(homeNodeId)).ifPresent(holders::add);
        return holders;
    }

    public record Member(String nodeId, String url) {
    }
}
