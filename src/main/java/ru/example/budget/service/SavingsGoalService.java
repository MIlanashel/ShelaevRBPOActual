package ru.example.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ApiException;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.domain.SavingsGoal;
import ru.example.budget.factory.SavingsGoalFactory;
import ru.example.budget.repository.BudgetRepository;
import java.util.List;
import java.util.Map;

@Cumponent
@Service
public class SavingsGoalService {
    private static final String VIEW = "SELECT g.id, g.owner_id, g.account_id, g.name, g.target_amount, " +
            "CASE WHEN COALESCE((SELECT SUM(t.amount) FROM financial_transactions t " +
            "WHERE t.goal_id = g.id AND t.posted = TRUE), 0) >= g.target_amount " +
            "THEN 'COMPLETED' ELSE g.status END AS status, " +
            "COALESCE((SELECT SUM(t.amount) FROM financial_transactions t " +
            "WHERE t.goal_id = g.id AND t.posted = TRUE), 0) AS saved_amount FROM savings_goals g";
    private final BudgetRepository repository;
    private final FacadeMapper mapper;
    private final Rules rules;
    private final SavingsGoalFactory factory;

    public SavingsGoalService(BudgetRepository repository, FacadeMapper mapper, Rules rules, SavingsGoalFactory factory) {
        this.repository = repository;
        this.mapper = mapper;
        this.rules = rules;
        this.factory = factory;
    }

    public List<ResourceFacade> all() {
        return repository.all(VIEW + " ORDER BY g.id").stream().map(mapper::toFacade).toList();
    }

    public ResourceFacade one(long id) {
        return mapper.toFacade(repository.one(VIEW + " WHERE g.id = ?", id));
    }

    private String validStatus(String status) {
        if (!status.equals("ACTIVE") && !status.equals("PAUSED") && !status.equals("COMPLETED")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Статус цели: ACTIVE, PAUSED или COMPLETED");
        }
        return status;
    }

    private void validate(SavingsGoal entity) {
        rules.ownerExists(entity.ownerId);
        if (rules.accountOwner(entity.accountId) != entity.ownerId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Счёт и цель должны принадлежать одному владельцу");
        }
    }

    @Transactional
    public ResourceFacade create(ResourceFacade request) {
        Map<String, Object> data = Fields.data(request);
        SavingsGoal entity = factory.create(null, Fields.id(data, "ownerId", null),
                Fields.id(data, "accountId", null), Fields.string(data, "name", null),
                Fields.decimal(data, "targetAmount", null, true),
                validStatus(Fields.string(data, "status", "ACTIVE")));
        validate(entity);
        if ("COMPLETED".equals(entity.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "Цель завершается отдельной бизнес-операцией");
        }
        return one(repository.insert("INSERT INTO savings_goals(owner_id, account_id, name, target_amount, status) " +
                        "VALUES (?, ?, ?, ?, ?)", entity.ownerId, entity.accountId, entity.name,
                entity.targetAmount, entity.status));
    }

    @Transactional
    public ResourceFacade patch(long id, ResourceFacade request) {
        Map<String, Object> existing = repository.one("SELECT * FROM savings_goals WHERE id = ?", id);
        Map<String, Object> data = Fields.data(request);
        SavingsGoal entity = factory.create(id,
                Fields.id(data, "ownerId", ((Number) existing.get("owner_id")).longValue()),
                Fields.id(data, "accountId", ((Number) existing.get("account_id")).longValue()),
                Fields.string(data, "name", (String) existing.get("name")),
                Fields.decimal(data, "targetAmount", (java.math.BigDecimal) existing.get("target_amount"), true),
                validStatus(Fields.string(data, "status", (String) existing.get("status"))));
        if (entity.ownerId != ((Number) existing.get("owner_id")).longValue()
                || entity.accountId != ((Number) existing.get("account_id")).longValue()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Смена владельца или счёта цели запрещена");
        }
        validate(entity);
        java.math.BigDecimal saved = repository.sum("SELECT COALESCE(SUM(amount),0) FROM financial_transactions " +
                "WHERE goal_id=? AND posted=TRUE", id);
        if (entity.targetAmount.compareTo(saved) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Целевая сумма не может быть меньше накопленной");
        }
        if ("COMPLETED".equals(entity.status) && !"COMPLETED".equals(existing.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Цель завершается отдельной бизнес-операцией");
        }
        if ("COMPLETED".equals(existing.get("status")) && !"COMPLETED".equals(entity.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "Завершённую цель нельзя снова открыть");
        }
        validate(entity);
        repository.update("UPDATE savings_goals SET name = ?, target_amount = ?, status = ? WHERE id = ?",
                entity.name, entity.targetAmount, entity.status, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        repository.delete("savings_goals", id);
    }
}
