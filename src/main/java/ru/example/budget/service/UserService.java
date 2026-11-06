package ru.example.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.factory.UserFactory;
import ru.example.budget.domain.User;
import ru.example.budget.repository.BudgetRepository;
import java.util.List;
import java.util.Map;

@Cumponent
@Service
public class UserService {
    private final BudgetRepository repository;
    private final FacadeMapper mapper;
    private final UserFactory factory;

    public UserService(BudgetRepository repository, FacadeMapper mapper, UserFactory factory) {
        this.repository = repository;
        this.mapper = mapper;
        this.factory = factory;
    }

    public List<ResourceFacade> all() {
        return repository.all("SELECT * FROM users ORDER BY id").stream().map(mapper::toFacade).toList();
    }

    public ResourceFacade one(long id) {
        return mapper.toFacade(repository.one("SELECT * FROM users WHERE id = ?", id));
    }

    @Transactional
    public ResourceFacade create(ResourceFacade request) {
        Map<String, Object> data = Fields.data(request);
        User entity = factory.create(null, Fields.string(data, "name", null), Fields.string(data, "email", null));
        long id = repository.insert("INSERT INTO users(name, email) VALUES (?, ?)", entity.name, entity.email);
        return one(id);
    }

    @Transactional
    public ResourceFacade patch(long id, ResourceFacade request) {
        Map<String, Object> existing = repository.one("SELECT * FROM users WHERE id = ?", id);
        Map<String, Object> data = Fields.data(request);
        User entity = factory.create(id, Fields.string(data, "name", (String) existing.get("name")),
                Fields.string(data, "email", (String) existing.get("email")));
        repository.update("UPDATE users SET name = ?, email = ? WHERE id = ?", entity.name, entity.email, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        repository.delete("users", id);
    }
}
