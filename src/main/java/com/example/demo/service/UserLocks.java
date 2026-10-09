package com.example.demo.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// Un candado por usuario para las operaciones que registran pérdidas.
// Evita que dos peticiones simultáneas del mismo usuario registren dos veces la misma pérdida.
@Component
public class UserLocks {

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public Object forUser(Long userId) {
        return locks.computeIfAbsent(userId, id -> new Object());
    }
}
