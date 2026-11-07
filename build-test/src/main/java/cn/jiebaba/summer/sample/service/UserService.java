package cn.jiebaba.summer.sample.service;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.core.annotation.Value;
import cn.jiebaba.summer.sample.model.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    @Value("${app.greeting}")
    private String greeting;

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public UserService() {
        store.put(1L, new User(1L, "summer", 1));
        sequence.set(1L);
    }

    public String greeting() {
        return greeting;
    }

    public User find(Long id) {
        return Optional.ofNullable(store.get(id))
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + id));
    }

    public java.util.Collection<User> findAll() {
        return store.values();
    }

    public User save(User user) {
        long id = user.id() != null ? user.id() : sequence.incrementAndGet();
        User saved = new User(id, user.name(), user.age());
        store.put(id, saved);
        return saved;
    }

    public User update(Long id, User user) {
        if (!store.containsKey(id)) {
            throw new IllegalArgumentException("user not found: " + id);
        }
        User updated = new User(id, user.name(), user.age());
        store.put(id, updated);
        return updated;
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
