package ru.example.budget.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ApiException;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.repository.BudgetRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

/** Five state-dependent, atomic financial workflows. */
@Cumponent
@Service
public class BudgetBusinessService {
    private final BudgetRepository repository;
    private final Clock clock;
    private final MoneyAccountService accounts;
    private final SavingsGoalService goals;
    private final FinancialTransactionService transactions;

    public BudgetBusinessService(BudgetRepository repository, Clock clock, MoneyAccountService accounts,
                                 SavingsGoalService goals, FinancialTransactionService transactions) {
        this.repository = repository;
        this.clock = clock;
        this.accounts = accounts;
        this.goals = goals;
        this.transactions = transactions;
    }

    private void require(boolean condition, String reason) {
        if (!condition) throw new ApiException(HttpStatus.CONFLICT, reason);
    }

    private Map<String, Object> account(long id) {
        return repository.one("SELECT * FROM money_accounts WHERE id = ? FOR UPDATE", id);
    }

    private BigDecimal balance(long id) {
        return (BigDecimal) accounts.one(id).data().get("currentBalance");
    }

    private BigDecimal saved(long goalId) {
        return repository.sum("SELECT COALESCE(SUM(amount), 0) FROM financial_transactions " +
                "WHERE goal_id = ? AND posted = TRUE", goalId);
    }

    private BigDecimal spent(long categoryId) {
        YearMonth month = YearMonth.now(clock);
        return repository.sum("SELECT COALESCE(SUM(amount),0) FROM financial_transactions " +
                        "WHERE category_id = ? AND type = 'EXPENSE' AND posted = TRUE " +
                        "AND occurred_at >= ? AND occurred_at < ?", categoryId,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private Map<String, Object> category(long id) {
        return repository.one("SELECT * FROM budget_categories WHERE id = ? FOR UPDATE", id);
    }

    private Map<String, Object> goal(long id) {
        return repository.one("SELECT * FROM savings_goals WHERE id = ? FOR UPDATE", id);
    }

    private void checkLimit(long id, BigDecimal amount) {
        Map<String, Object> category = category(id);
        require(spent(id).add(amount).compareTo((BigDecimal) category.get("monthly_limit")) <= 0,
                "Месячный лимит категории будет превышен");
    }

    private long insertPosted(long accountId, Long categoryId, Long goalId,
                              BigDecimal amount, String type) {
        return repository.insert("INSERT INTO financial_transactions " +
                        "(account_id, category_id, goal_id, amount, type, occurred_at, posted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, TRUE)",
                accountId, categoryId, goalId, amount, type, LocalDateTime.now(clock));
    }

    /** 1. Transfer available money between two accounts belonging to the same user. */
    @Transactional
    public Map<String, Object> transfer(Map<String, Object> payload) {
        long from = Fields.id(payload, "fromAccountId", null);
        long to = Fields.id(payload, "toAccountId", null);
        BigDecimal amount = Fields.decimal(payload, "amount", null, true);
        require(from != to, "Счета перевода должны различаться");
        // Deterministic lock order prevents deadlocks for concurrent opposite transfers.
        Map<String, Object> first = account(Math.min(from, to));
        Map<String, Object> second = account(Math.max(from, to));
        Map<String, Object> sender = from < to ? first : second;
        Map<String, Object> recipient = from < to ? second : first;
        require(((Number) sender.get("owner_id")).longValue() ==
                        ((Number) recipient.get("owner_id")).longValue(),
                "Переводы доступны только между счетами одного пользователя");
        require(balance(from).compareTo(amount) >= 0, "Недостаточно денег на счёте отправителя");
        long transferId = repository.insert("INSERT INTO account_transfers " +
                        "(from_account_id, to_account_id, amount, occurred_at) VALUES (?, ?, ?, ?)",
                from, to, amount, LocalDateTime.now(clock));
        return Map.of("transferId", transferId, "fromAccount", accounts.one(from),
                "toAccount", accounts.one(to));
    }

    /** 2. Spend within category limit and current account balance. */
    @Transactional
    public ResourceFacade spend(long categoryId, Map<String, Object> payload) {
        long accountId = Fields.id(payload, "accountId", null);
        BigDecimal amount = Fields.decimal(payload, "amount", null, true);
        Map<String, Object> a = account(accountId);
        Map<String, Object> c = category(categoryId);
        require(((Number) a.get("owner_id")).longValue() == ((Number) c.get("owner_id")).longValue(),
                "Счёт и категория должны принадлежать одному пользователю");
        checkLimit(categoryId, amount);
        require(balance(accountId).compareTo(amount) >= 0, "Недостаточно денег на счёте");
        return transactions.one(insertPosted(accountId, categoryId, null, amount, "EXPENSE"));
    }

    /** 3. Put money into an ACTIVE savings goal with sufficient balance and remaining target. */
    @Transactional
    public Map<String, Object> contribute(long goalId, Map<String, Object> payload) {
        BigDecimal amount = Fields.decimal(payload, "amount", null, true);
        Map<String, Object> g = goal(goalId);
        long accountId = ((Number) g.get("account_id")).longValue();
        account(accountId);
        require("ACTIVE".equals(g.get("status")), "Взносы разрешены только в активную цель");
        require(saved(goalId).add(amount).compareTo((BigDecimal) g.get("target_amount")) <= 0,
                "Взнос превышает оставшуюся сумму цели");
        require(balance(accountId).compareTo(amount) >= 0, "Недостаточно денег на счёте");
        long transactionId = insertPosted(accountId, null, goalId, amount, "CONTRIBUTION");
        return Map.of("transaction", transactions.one(transactionId), "goal", goals.one(goalId),
                "account", accounts.one(accountId));
    }

    /** 4. Post an existing draft, checking linked account, category and goal state. */
    @Transactional
    public ResourceFacade postDraft(long transactionId) {
        Map<String, Object> t = repository.one("SELECT * FROM financial_transactions WHERE id = ? FOR UPDATE", transactionId);
        require(!Boolean.TRUE.equals(t.get("posted")), "Операция уже проведена");
        long accountId = ((Number) t.get("account_id")).longValue();
        account(accountId);
        BigDecimal amount = (BigDecimal) t.get("amount");
        String type = (String) t.get("type");
        if ("EXPENSE".equals(type)) {
            long categoryId = ((Number) t.get("category_id")).longValue();
            Map<String, Object> c = category(categoryId);
            require(((Number) c.get("owner_id")).longValue() ==
                    ((Number) account(accountId).get("owner_id")).longValue(), "Категория другого пользователя");
            checkLimit(categoryId, amount);
        } else if ("CONTRIBUTION".equals(type)) {
            long goalId = ((Number) t.get("goal_id")).longValue();
            Map<String, Object> g = goal(goalId);
            require(((Number) g.get("account_id")).longValue() == accountId, "Цель привязана к другому счёту");
            require("ACTIVE".equals(g.get("status")), "Цель не активна");
            require(saved(goalId).add(amount).compareTo((BigDecimal) g.get("target_amount")) <= 0,
                    "Сумма превышает целевую");
        }
        if (!"INCOME".equals(type)) {
            require(balance(accountId).compareTo(amount) >= 0, "Недостаточно денег на счёте");
        }
        repository.update("UPDATE financial_transactions SET posted = TRUE WHERE id = ?", transactionId);
        return transactions.one(transactionId);
    }

    /** 5. Complete only a fully financed savings goal. */
    @Transactional
    public ResourceFacade completeGoal(long goalId) {
        Map<String, Object> g = goal(goalId);
        account(((Number) g.get("account_id")).longValue());
        require("ACTIVE".equals(g.get("status")), "Завершить можно только активную цель");
        require(saved(goalId).compareTo((BigDecimal) g.get("target_amount")) >= 0,
                "Цель ещё не накоплена полностью");
        repository.update("UPDATE savings_goals SET status = 'COMPLETED' WHERE id = ?", goalId);
        return goals.one(goalId);
    }
}
