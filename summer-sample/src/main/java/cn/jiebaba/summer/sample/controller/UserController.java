package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.sample.model.User;
import cn.jiebaba.summer.sample.service.UserService;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public Collection<User> list(@RequestParam(value = "name", required = false) String name) {
        Collection<User> all = userService.findAll();
        if (name == null || name.isEmpty()) return all;
        return all.stream().filter(u -> u.name().contains(name)).toList();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return userService.find(id);
    }

    @PostMapping
    @ResponseStatus(201)
    public User create(@RequestBody User user) {
        return userService.save(user);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        return Map.of("deleted", userService.delete(id));
    }

    @GetMapping("/greeting")
    public Map<String, String> greeting() {
        return Map.of("greeting", userService.greeting());
    }
}
