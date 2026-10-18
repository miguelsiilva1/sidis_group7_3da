package aisafe.routes.application;

import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finds alternative paths over the active routes of every replica (US216).
 */
@Service
@RequiredArgsConstructor
public class NetworkGraphService {

    private final RoutesAcrossReplicas routes;

    public List<List<String>> findAlternativePaths(String origin, String destination) {
        Map<String, List<String>> graph = buildGraph(routes.findAll().stream()
                .map(Versioned::value)
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE)
                .toList());
        List<List<String>> paths = new ArrayList<>();
        dfs(origin, destination, graph, new LinkedHashSet<>(), paths, 5);
        return paths.stream()
                .filter(path -> path.size() > 2)
                .toList();
    }

    private Map<String, List<String>> buildGraph(List<Route> routes) {
        Map<String, List<String>> graph = new HashMap<>();
        for (Route route : routes) {
            graph.computeIfAbsent(route.getOrigin().getCode(), ignored -> new ArrayList<>())
                    .add(route.getDestination().getCode());
        }
        return graph;
    }

    private void dfs(String current, String destination, Map<String, List<String>> graph,
                     LinkedHashSet<String> visited, List<List<String>> paths, int maxDepth) {
        if (visited.size() > maxDepth) {
            return;
        }
        visited.add(current);
        if (current.equals(destination)) {
            paths.add(new ArrayList<>(visited));
            visited.remove(current);
            return;
        }
        for (String next : graph.getOrDefault(current, List.of())) {
            if (!visited.contains(next)) {
                dfs(next, destination, graph, visited, paths, maxDepth);
            }
        }
        visited.remove(current);
    }
}
