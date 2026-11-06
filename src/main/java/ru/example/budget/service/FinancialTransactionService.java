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

    /** Posted CRUD transactions obey the same financial limits as business actions. */
    private void checkPosting(FinancialTransaction transaction, Map<String, Object> previous) {
        if (!transaction.posted) return;
        repository.one("SELECT id FROM money_accounts WHERE id = ? FOR UPDATE", transaction.accountId);
        BigDecimal available = repository.sum(
                "SELECT a.initial_balance + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' " +
                        "THEN t.amount ELSE -t.amount END) FROM financial_transactions t " +
                        "WHERE t.account_id=a.id AND t.posted=TRUE), 0) + " +
                        "COALESCE((SELECT SUM(v.amount) FROM account_transfers v WHERE v.to_account_id=a.id),0) - " +
                        "COALESCE((SELECT SUM(v.amount) FROM account_transfers v WHERE v.from_account_id=a.id),0) " +
                        "FROM money_accounts a WHERE a.id=?", transaction.accountId);
        // An edited posted transaction must be excluded from the comparison baseline.
        if (previous != null && Boolean.TRUE.equals(previous.get("posted")) &&
                ((Number) previous.get("account_id")).longValue() == transaction.accountId) {
            BigDecimal oldAmount = (BigDecimal) previous.get("amount");
            available = available.add("INCOME".equals(previous.get("type")) ? oldAmount.negate() : oldAmount);
        }
        if (!"INCOME".equals(transaction.type) && available.compareTo(transaction.amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Недостаточно денег для проведения операции");
        }
        if ("EXPENSE".equals(transaction.type)) {
            repository.one("SELECT id FROM budget_categories WHERE id=? FOR UPDATE", transaction.categoryId);
            java.time.YearMonth month = java.time.YearMonth.from(transaction.occurredAt);
            BigDecimal spent = repository.sum("SELECT COALESCE(SUM(amount),0) FROM financial_transactions " +
                            "WHERE category_id=? AND type='EXPENSE' AND posted=TRUE AND id<>? " +
                            "AND occurred_at >= ? AND occurred_at < ?", transaction.categoryId,
                    transaction.id == null ? -1L : transaction.id,
                    month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
            BigDecimal limit = (BigDecimal) repository.one("SELECT monthly_limit FROM budget_categories " +
                    "WHERE id=?", transaction.categoryId).get("monthly_limit");
            if (spent.add(transaction.amount).compareTo(limit) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Превышен месячный лимит категории");
            }
        }
        if ("CONTRIBUTION".equals(transaction.type)) {
            Map<String, Object> goal = repository.one("SELECT * FROM savings_goals WHERE id=? FOR UPDATE", transaction.goalId);
            if (!"ACTIVE".equals(goal.get("status"))) {
                throw new ApiException(HttpStatus.CONFLICT, "Цель не активна");
            }
            BigDecimal saved = repository.sum("SELECT COALESCE(SUM(amount),0) FROM financial_transactions " +
                            "WHERE goal_id=? AND posted=TRUE AND id<>?", transaction.goalId,
                    transaction.id == null ? -1L : transaction.id);
            if (saved.add(transaction.amount).compareTo((BigDecimal) goal.get("target_amount")) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Сумма взносов превышает цель");
            }
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
        checkPosting(transaction, null);
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
        if ("CONTRIBUTION".equals(existing.get("type")) && Boolean.TRUE.equals(existing.get("posted"))) {
            Map<String, Object> goal = repository.one("SELECT * FROM savings_goals WHERE id=?", existing.get("goal_id"));
            if ("COMPLETED".equals(goal.get("status")) &&
                    (!transaction.posted || !"CONTRIBUTION".equals(transaction.type) ||
                    !java.util.Objects.equals(transaction.goalId, ((Number) existing.get("goal_id")).longValue()) ||
                    transaction.amount.compareTo((BigDecimal) existing.get("amount")) < 0)) {
                throw new ApiException(HttpStatus.CONFLICT, "Нельзя уменьшить взносы завершённой цели");
            }
        }
        checkPosting(transaction, existing);
        repository.update("UPDATE financial_transactions SET account_id = ?, category_id = ?, goal_id = ?, " +
                        "amount = ?, type = ?, posted = ? WHERE id = ?", transaction.accountId,
                transaction.categoryId, transaction.goalId, transaction.amount, transaction.type,
                transaction.posted, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        Map<String, Object> existing = repository.one("SELECT * FROM financial_transactions WHERE id=?", id);
        if ("CONTRIBUTION".equals(existing.get("type")) && Boolean.TRUE.equals(existing.get("posted"))) {
            Map<String, Object> goal = repository.one("SELECT * FROM savings_goals WHERE id=?", existing.get("goal_id"));
            if ("COMPLETED".equals(goal.get("status"))) {
                throw new ApiException(HttpStatus.CONFLICT, "Нельзя удалить взнос завершённой цели");
            }
        }
        repository.delete("financial_transactions", id);
    }
}
