package cn.jiebaba.summer.web.routing;

import cn.jiebaba.summer.web.http.HttpMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 路由表：按 HTTP 方法分组、以路径段构造前缀树索引，将匹配从 O(路由数) 降为
 * O(路径段数) 量级。命中多个模式时取排序后索引最小者，与原线性扫描
 * 「按特异性降序取首个命中」语义一致。
 */
public final class Router {
    private final List<RouteMapping> routes = new ArrayList<>();
    private volatile boolean trieReady = false;
    private volatile EnumMap<HttpMethod, TrieNode> trie = new EnumMap<>(HttpMethod.class);

    public void register(RouteMapping mapping) {
        routes.add(mapping);
        trieReady = false;
    }

    /** 按特异性降序排序；排序后标记前缀树需重建。 */
    public void sortBySpecificity() {
        routes.sort(Comparator.comparingInt((RouteMapping m) -> m.pattern().specificity()).reversed());
        trieReady = false;
    }

    public List<RouteMapping> routes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * 匹配路由：按方法取出前缀树，沿请求路径段收集所有命中终端，取排序索引最小者。
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 命中时包含路由与路径变量的 {@link Optional}，否则为空
     */
    public Optional<RouteMatch> match(HttpMethod method, String path) {
        ensureTrie();
        TrieNode root = trie.get(method);
        if (root == null) return Optional.empty();
        String[] segs = RoutePattern.requestSegments(path);
        Winner winner = new Winner();
        collect(root, segs, 0, winner);
        return winner.best == null ? Optional.empty()
                : Optional.of(new RouteMatch(winner.best.route, winner.buildVars(segs)));
    }

    /**
     * 线性匹配（参考实现，保留作语义基准与测试对照）：按特异性降序遍历全部路由，
     * 返回首个模式命中者。
     */
    Optional<RouteMatch> matchLinear(HttpMethod method, String path) {
        for (RouteMapping route : routes) {
            if (route.httpMethod() != method) continue;
            var vars = route.pattern().match(path);
            if (vars.isPresent()) return Optional.of(new RouteMatch(route, vars.get()));
        }
        return Optional.empty();
    }

    /** 首次匹配前（或路由变更后）按排序后的路由列表构建前缀树。 */
    private void ensureTrie() {
        if (trieReady) return;
        synchronized (this) {
            if (trieReady) return;
            EnumMap<HttpMethod, TrieNode> built = new EnumMap<>(HttpMethod.class);
            for (int i = 0; i < routes.size(); i++) {
                RouteMapping r = routes.get(i);
                TrieNode root = built.computeIfAbsent(r.httpMethod(), k -> new TrieNode());
                insert(root, r, i);
            }
            trie = built;
            trieReady = true;
        }
    }

    /**
     * 将一条路由插入前缀树：按模式分段逐级创建字面量/参数/通配符子节点，
     * 在末端记录终端（含参数位置与变量名），catch-all 终端单独存放。
     */
    private static void insert(TrieNode root, RouteMapping route, int sortedIndex) {
        RoutePattern p = route.pattern();
        String[] segs = p.segments();
        TrieNode node = root;
        List<Integer> paramPositions = new ArrayList<>();
        List<String> varNames = new ArrayList<>();
        for (int i = 0; i < segs.length; i++) {
            String seg = segs[i];
            if (seg.startsWith("{") && seg.endsWith("}")) {
                paramPositions.add(i);
                varNames.add(seg.substring(1, seg.length() - 1));
                node = (node.param == null) ? (node.param = new TrieNode()) : node.param;
            } else if ("*".equals(seg)) {
                node = (node.wildcard == null) ? (node.wildcard = new TrieNode()) : node.wildcard;
            } else {
                node = node.literal.computeIfAbsent(seg, k -> new TrieNode());
            }
        }
        int[] ppos = new int[paramPositions.size()];
        for (int k = 0; k < ppos.length; k++) ppos[k] = paramPositions.get(k);
        Terminal terminal = new Terminal(route, sortedIndex, ppos, varNames.toArray(new String[0]));
        (p.catchAll() ? node.catchAllTerminals : node.exactTerminals).add(terminal);
    }

    /**
     * 沿请求路径段深度优先收集命中终端：在每个节点检查 catch-all（路径长度不小于前缀长度即命中），
     * 在段耗尽处检查精确终端；并按字面量、参数、通配符顺序继续下探。
     */
    private static void collect(TrieNode node, String[] segs, int idx, Winner winner) {
        if (segs.length >= idx && !node.catchAllTerminals.isEmpty()) {
            for (Terminal t : node.catchAllTerminals) winner.consider(t);
        }
        if (idx == segs.length) {
            for (Terminal t : node.exactTerminals) winner.consider(t);
            return;
        }
        if (idx > segs.length) return;
        String seg = segs[idx];
        TrieNode lit = node.literal.get(seg);
        if (lit != null) collect(lit, segs, idx + 1, winner);
        if (node.param != null) collect(node.param, segs, idx + 1, winner);
        if (node.wildcard != null) collect(node.wildcard, segs, idx + 1, winner);
    }

    /** 前缀树节点：字面量子节点按段名索引，参数与通配符各一个子节点。 */
    private static final class TrieNode {
        final Map<String, TrieNode> literal = new HashMap<>();
        TrieNode param;
        TrieNode wildcard;
        final List<Terminal> exactTerminals = new ArrayList<>();
        final List<Terminal> catchAllTerminals = new ArrayList<>();
    }

    /** 路由终端：关联路由、排序索引、参数位置与变量名。 */
    private record Terminal(RouteMapping route, int sortedIndex, int[] paramPositions, String[] varNames) {}

    /** 命中收集器：在所有命中终端中取排序索引最小者，并按其参数位置构建路径变量。 */
    private static final class Winner {
        Terminal best;

        void consider(Terminal t) {
            if (best == null || t.sortedIndex < best.sortedIndex) best = t;
        }

        Map<String, String> buildVars(String[] segs) {
            Map<String, String> vars = new LinkedHashMap<>();
            int[] ppos = best.paramPositions;
            String[] names = best.varNames;
            for (int k = 0; k < ppos.length; k++) {
                vars.put(names[k], segs[ppos[k]]);
            }
            return vars;
        }
    }
}
