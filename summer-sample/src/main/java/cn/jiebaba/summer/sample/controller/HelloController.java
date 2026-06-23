package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.web.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, Object> index(@RequestParam(value = "name", defaultValue = "world") String name) {
        return Map.of(
                "message", "Hello " + name,
                "framework", "summer",
                "runtime", "JDK 25 / virtual threads / JPMS"
        );
    }

    @GetMapping("/hello/{who}")
    public Map<String, String> hello(@PathVariable String who) {
        return Map.of("hello", who);
    }

    @GetMapping("/async")
    public CompletableFuture<Map<String, Object>> async() {
        return CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("async", true, "thread", Thread.currentThread().toString());
        });
    }

    @PostMapping("/say/")
    @ResponseBody
    public Map<String, String> say(@RequestBody Map<String, String> data) {
        return Map.of("hello", data.get("name"));
    }
}