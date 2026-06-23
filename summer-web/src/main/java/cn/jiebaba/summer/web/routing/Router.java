package cn.jiebaba.summer.web.routing;

import cn.jiebaba.summer.web.http.HttpMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

public final class Router {
    private final List<RouteMapping> routes = new ArrayList<>();

    public void register(RouteMapping mapping) {
        routes.add(mapping);
    }

    public void sortBySpecificity() {
        routes.sort(Comparator.comparingInt((RouteMapping m) -> m.pattern().specificity()).reversed());
    }

    public List<RouteMapping> routes() {
        return Collections.unmodifiableList(routes);
    }

    public Optional<RouteMatch> match(HttpMethod method, String path) {
        for (RouteMapping route : routes) {
            if (route.httpMethod() != method) continue;
            var vars = route.pattern().match(path);
            if (vars.isPresent()) {
                return Optional.of(new RouteMatch(route, vars.get()));
            }
        }
        return Optional.empty();
    }
}