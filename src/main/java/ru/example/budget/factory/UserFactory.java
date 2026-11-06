package ru.example.budget.factory;

import ru.example.budget.annotation.Cumponent;
import ru.example.budget.domain.User;

@Cumponent
public class UserFactory {
    public User create(Long id, String name, String email) {
        User entity = new User();
        entity.id = id;
        entity.name = name;
        entity.email = email;
        return entity;
    }
}
