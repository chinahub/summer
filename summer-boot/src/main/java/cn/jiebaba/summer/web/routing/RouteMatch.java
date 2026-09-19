package cn.jiebaba.summer.web.routing;

import java.util.Map;

public record RouteMatch(RouteMapping mapping, Map<String, String> pathVariables) {}
