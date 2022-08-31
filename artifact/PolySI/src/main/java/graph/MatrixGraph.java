package graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Streams;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import lombok.Getter;
import lombok.Setter;

import util.UnimplementedError;

public class MatrixGraph<T> implements MutableGraph<T> {
    private final BiMap<T, Integer> nodeMap = HashBiMap.create();
    private final long adjacency[][];
    private static final int LONG_BITS = 64;

    public MatrixGraph(Graph<T> graph) {
        int i = 0;
        for (var n : graph.nodes()) {
            nodeMap.put(n, i++);
        }

        adjacency = new long[i][(i + LONG_BITS - 1) / LONG_BITS];
        for (var e : graph.edges()) {
            putEdge(e.source(), e.target());
        }
    }

    public static <T> MatrixGraph<T> ofNodes(MatrixGraph<T> graph) {
        return new MatrixGraph<>(graph.nodeMap);
    }

    private MatrixGraph(BiMap<T, Integer> nodes) {
        nodeMap.putAll(nodes);
        adjacency = new long[nodes.size()][(nodes.size() + LONG_BITS - 1)
                / LONG_BITS];
    }

    private MatrixGraph(MatrixGraph<T> graph) {
        nodeMap.putAll(graph.nodeMap);
        adjacency = new long[graph.adjacency.length][];
        for (var i = 0; i < adjacency.length; i++) {
            adjacency[i] = graph.adjacency[i].clone();
        }
    }

    private MatrixGraph<T> bfsWithNoCycle(List<Integer> topoOrder) {
        var result = new MatrixGraph<T>(nodeMap);

        for (var i = topoOrder.size() - 1; i >= 0; i--) {
            var n = topoOrder.get(i);

            for (var j : successorIds(n).toArray()) {
                assert topoOrder.indexOf(j) > i;
                result.set(n, j);
                for (var k = 0; k < adjacency[0].length; k++) {
                    result.adjacency[n][k] |= result.adjacency[j][k];
                }
            }
        }

        return result;
    }

    private MatrixGraph<T> allNodesBfs() {
        var topoOrder = topoSortId().orElse(null);
        if (topoOrder != null) {
            return bfsWithNoCycle(topoOrder);
        }

        var result = new MatrixGraph<>(this.nodeMap);
        var graph = toSparseGraph();
        for (var i = 0; i < adjacency.length; i++) {
            var q = new ArrayDeque<Integer>();

            q.add(i);
            while (!q.isEmpty()) {
                var j = q.pop();

                for (var k : graph.successors(j)) {
                    if (result.get(i, k)) {
                        continue;
                    }

                    result.set(i, k);
                    q.push(k);
                }
            }
        }

        return result;
    }

    public MatrixGraph<T> reachability() {
        return allNodesBfs();
    }

    private MatrixGraph<T> matrixProduct(MatrixGraph<T> other) {
        assert nodeMap.equals(other.nodeMap);

        var result = new MatrixGraph<>(nodeMap);
        for (var i = 0; i < adjacency.length; i++) {
            for (var j = 0; j < adjacency.length; j++) {
                if (!get(i, j)) {
                    continue;
                }

                for (var k = 0; k < adjacency[0].length; k++) {
                    result.adjacency[i][k] |= other.adjacency[j][k];
                }
            }
        }

        return result;
    }

    public MatrixGraph<T> composition(MatrixGraph<T> other) {
        return matrixProduct(other);
    }

    public MatrixGraph<T> union(MatrixGraph<T> other) {
        assert nodeMap.equals(other.nodeMap);

        var result = new MatrixGraph<>(nodeMap);
        for (var i = 0; i < adjacency.length; i++) {
            for (var j = 0; j < adjacency[0].length; j++) {
                result.adjacency[i][j] = adjacency[i][j]
                        | other.adjacency[i][j];
            }
        }

        return result;
    }

    private Optional<List<Integer>> topoSortId() {
        var nodes = new ArrayList<Integer>();
        var inDegrees = new int[adjacency.length];

        for (var i = 0; i < adjacency.length; i++) {
            inDegrees[i] = inDegree(i);
            if (inDegrees[i] == 0) {
                nodes.add(i);
            }
        }

        for (var i = 0; i < nodes.size(); i++) {
            successorIds(nodes.get(i)).forEach(n -> {
                if (--inDegrees[n] == 0) {
                    nodes.add(n);
                }
            });
        }

        return nodes.size() == adjacency.length ? Optional.of(nodes)
                : Optional.empty();
    }

    public Optional<List<T>> topologicalSort() {
        return topoSortId()
                .map(o -> o.stream().map(n -> nodeMap.inverse().get(n))
                        .collect(Collectors.toList()));
    }

    public boolean hasLoops() {
        return topoSortId().isEmpty();
    }

    private Graph<Integer> toSparseGraph() {
        MutableGraph<Integer> graph = GraphBuilder.directed()
                .allowsSelfLoops(true).build();
        for (int i = 0; i < adjacency.length; i++) {
            graph.addNode(i);
        }

        for (int i = 0; i < adjacency.length; i++) {
            for (int j = 0; j < adjacency.length; j++) {
                if (get(i, j)) {
                    graph.putEdge(i, j);
                }
            }
        }

        return graph;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append('\n');
        for (int i = 0; i < adjacency.length; i++) {
            for (int j = 0; j < adjacency.length; j++) {
                builder.append(get(i, j) ? 1 : 0);
                builder.append(' ');
            }
            builder.append('\n');
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MatrixGraph)) {
            return false;
        }

        var g = (MatrixGraph<T>) obj;
        if (!nodeMap.equals(g.nodeMap)) {
            return false;
        }

        for (var i = 0; i < adjacency.length; i++) {
            if (!Arrays.equals(adjacency[i], g.adjacency[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Set<T> nodes() {
        return nodeMap.keySet();
    }

    @Override
    public Set<EndpointPair<T>> edges() {
        var result = new HashSet<EndpointPair<T>>();
        var map = nodeMap.inverse();

        for (int i = 0; i < adjacency.length; i++) {
            for (int j = 0; j < adjacency.length; j++) {
                if (get(i, j)) {
                    result.add(EndpointPair.ordered(map.get(i), map.get(j)));
                }
            }
        }

        return result;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    @Override
    public boolean allowsSelfLoops() {
        return true;
    }

    @Override
    public ElementOrder<T> nodeOrder() {
        return ElementOrder.unordered();
    }

    @Override
    public ElementOrder<T> incidentEdgeOrder() {
        return ElementOrder.unordered();
    }

    @Override
    public Set<T> adjacentNodes(T node) {
        throw new UnimplementedError();
    }

    @Override
    public Set<T> predecessors(T node) {
        throw new UnimplementedError();
    }

    @Override
    public Set<T> successors(T node) {
        var inv = nodeMap.inverse();
        return successorIds(nodeMap.get(node)).mapToObj(inv::get)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<EndpointPair<T>> incidentEdges(T node) {
        throw new UnimplementedError();
    }

    @Override
    public int degree(T node) {
        throw new UnimplementedError();
    }

    @Override
    public int inDegree(T node) {
        return inDegree(nodeMap.get(node));
    }

    @Override
    public int outDegree(T node) {
        return outDegree(nodeMap.get(node));
    }

    @Override
    public boolean hasEdgeConnecting(T nodeU, T nodeV) {
        return get(nodeMap.get(nodeU), nodeMap.get(nodeV));
    }

    @Override
    public boolean hasEdgeConnecting(EndpointPair<T> endpoints) {
        return hasEdgeConnecting(endpoints.source(), endpoints.target());
    }

    private boolean get(int i, int j) {
        return (adjacency[i][j / LONG_BITS] & (1L << (j % LONG_BITS))) != 0;
    }

    private void set(int i, int j) {
        adjacency[i][j / LONG_BITS] |= (1L << (j % LONG_BITS));
    }

    private void clear(int i, int j) {
        adjacency[i][j / LONG_BITS] &= ~(1L << (j % LONG_BITS));
    }

    private int inDegree(int n) {
        var inDegree = 0;
        for (var i = 0; i < adjacency.length; i++) {
            inDegree += get(i, n) ? 1 : 0;
        }

        return inDegree;
    }

    private int outDegree(int n) {
        return Arrays.stream(adjacency[n]).mapToInt(Long::bitCount)
                .reduce(Integer::sum).orElse(0);
    }

    private IntStream successorIds(int n) {
        return IntStream.range(0, adjacency.length).filter(i -> get(n, i));
    }

    @Override
    public boolean addNode(T node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean putEdge(T nodeU, T nodeV) {
        var i = nodeMap.get(nodeU);
        var j = nodeMap.get(nodeV);
        boolean hasEdge = get(i, j);
        set(i, j);
        return !hasEdge;
    }

    @Override
    public boolean putEdge(EndpointPair<T> endpoints) {
        return putEdge(endpoints.source(), endpoints.target());
    }

    @Override
    public boolean removeNode(T node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeEdge(T nodeU, T nodeV) {
        var i = nodeMap.get(nodeU);
        var j = nodeMap.get(nodeV);
        boolean hasEdge = get(i, j);
        clear(i, j);
        return hasEdge;
    }

    @Override
    public boolean removeEdge(EndpointPair<T> endpoints) {
        return removeEdge(endpoints.source(), endpoints.target());
    }
}
