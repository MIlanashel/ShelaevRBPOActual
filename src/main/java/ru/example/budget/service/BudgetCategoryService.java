package ru.example.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.domain.BudgetCategory;
import ru.example.budget.factory.BudgetCategoryFactory;
import ru.example.budget.repository.BudgetRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Cumponent
@Service
public class BudgetCategoryService {
    private final BudgetRepository repository;
    private final FacadeMapper mapper;
    private final Rules rules;
    private final BudgetCategoryFactory factory;

    public BudgetCategoryService(BudgetRepository repository, FacadeMapper mapper, Rules rules, BudgetCategoryFactory factory) {
        this.repository = repository;
        this.mapper = mapper;
        this.rules = rules;
        this.factory = factory;
    }

    public List<ResourceFacade> all() {
        return repository.all("SELECT * FROM budget_categories ORDER BY id").stream().map(mapper::toFacade).toList();
    }

    public ResourceFacade one(long id) {
        return mapper.toFacade(repository.one("SELECT * FROM budget_categories WHERE id = ?", id));
    }

    @Transactional
    public ResourceFacade create(ResourceFacade request) {
        Map<String, Object> data = Fields.data(request);
        BudgetCategory entity = factory.create(null, Fields.id(data, "ownerId", null),
                Fields.string(data, "name", null), Fields.decimal(data, "monthlyLimit", null, false));
        rules.ownerExists(entity.ownerId);
        return one(repository.insert("INSERT INTO budget_categories(owner_id, name, monthly_limit) VALUES (?, ?, ?)",
                entity.ownerId, entity.name, entity.monthlyLimit));
    }

    @Transactional
    public ResourceFacade patch(long id, ResourceFacade request) {
        Map<String, Object> existing = repository.one("SELECT * FROM budget_categories WHERE id = ?", id);
        Map<String, Object> data = Fields.data(request);
        BudgetCategory entity = factory.create(id,
                Fields.id(data, "ownerId", ((Number) existing.get("owner_id")).longValue()),
                Fields.string(data, "name", (String) existing.get("name")),
                Fields.decimal(data, "monthlyLimit", (BigDecimal) existing.get("monthly_limit"), false));
        if (entity.ownerId != ((Number) existing.get("owner_id")).longValue()) {
            throw new ru.example.budget.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Смена владельца категории запрещена");
        }
        repository.update("UPDATE budget_categories SET name = ?, monthly_limit = ? WHERE id = ?",
                entity.name, entity.monthlyLimit, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        repository.delete("budget_categories", id);
    }
}
