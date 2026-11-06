package ru.example.budget.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ApiException;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.domain.FinancialTransaction;
import ru.example.budget.factory.FinancialTransactionFactory;
import ru.example.budget.repository.BudgetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Cumponent
@Service
public class FinancialTransactionService {
    private final BudgetRepository repository;
    private final FacadeMapper mapper;
    private final Rules rules;
    private final FinancialTransactionFactory factory;
    private final Clock clock;

    public FinancialTransactionService(BudgetRepository repository, FacadeMapper mapper, Rules rules,
                                       FinancialTransactionFactory factory, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.rules = rules;
        this.factory = factory;
        this.clock = clock;
    }

    public List<ResourceFacade> all() {
        return repository.all("SELECT * FROM financial_transactions ORDER BY id")
                .stream().map(mapper::toFacade).toList();
    }

    public ResourceFacade one(long id) {
        return mapper.toFacade(repository.one("SELECT * FROM financial_transactions WHERE id = ?", id));
    }

    private String validType(String type) {
        if (!type.equals("INCOME") && !type.equals("EXPENSE") && !type.equals("CONTRIBUTION")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Тип: INCOME, EXPENSE или CONTRIBUTION");
        }
        return type;
    }

    private void validate(FinancialTransaction transaction) {
        rules.accountOwner(transaction.accountId);
        switch (transaction.type) {
            case "INCOME" -> {
                if (transaction.categoryId != null || transaction.goalId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Доход не имеет категории или цели");
                }
            }
            case "EXPENSE" -> {
                if (transaction.categoryId == null || transaction.goalId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Для расхода нужна только категория");
                }
                rules.categoryMatchesAccount(transaction.categoryId, transaction.accountId);
            }
            case "CONTRIBUTION" -> {
                if (transaction.goalId == null || transaction.categoryId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Для взноса нужна только цель");
                }
                rules.goalMatchesAccount(transaction.goalId, transaction.accountId);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Неизвестный тип операции");
        }
    }

    @Transactional
    public ResourceFacade create(ResourceFacade request) {
        Map<String, Object> data = Fields.data(request);
        FinancialTransaction transaction = factory.create(null, Fields.id(data, "accountId", null),
                Fields.optionalId(data, "categoryId", null), Fields.optionalId(data, "goalId", null),
                Fields.decimal(data, "amount", null, true), validType(Fields.string(data, "type", null)),
                LocalDateTime.now(clock), Fields.bool(data, "posted", false));
        validate(transaction);
        return one(repository.insert("INSERT INTO financial_transactions " +
                        "(account_id, category_id, goal_id, amount, type, occurred_at, posted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                transaction.accountId, transaction.categoryId, transaction.goalId, transaction.amount,
                transaction.type, transaction.occurredAt, transaction.posted));
    }

    @Transactional
    public ResourceFacade patch(long id, ResourceFacade request) {
        Map<String, Object> existing = repository.one("SELECT * FROM financial_transactions WHERE id = ?", id);
        Map<String, Object> data = Fields.data(request);
        // The request cannot set the server-controlled occurredAt timestamp.
        FinancialTransaction transaction = factory.create(id,
                Fields.id(data, "accountId", ((Number) existing.get("account_id")).longValue()),
                Fields.optionalId(data, "categoryId", (Long) existing.get("category_id")),
                Fields.optionalId(data, "goalId", (Long) existing.get("goal_id")),
                Fields.decimal(data, "amount", (BigDecimal) existing.get("amount"), true),
                validType(Fields.string(data, "type", (String) existing.get("type"))),
                (LocalDateTime) existing.get("occurred_at"),
                Fields.bool(data, "posted", (Boolean) existing.get("posted")));
        validate(transaction);
        repository.update("UPDATE financial_transactions SET account_id = ?, category_id = ?, goal_id = ?, " +
                        "amount = ?, type = ?, posted = ? WHERE id = ?", transaction.accountId,
                transaction.categoryId, transaction.goalId, transaction.amount, transaction.type,
                transaction.posted, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        repository.delete("financial_transactions", id);
    }
}
