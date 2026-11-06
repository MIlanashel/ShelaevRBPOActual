package ru.example.budget.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ApiException;
import ru.example.budget.repository.BudgetRepository;

@Cumponent
@Service
public class Rules {
    private final BudgetRepository repository;

    public Rules(BudgetRepository repository) {
        this.repository = repository;
    }

    public void ownerExists(Long ownerId) {
        if (!repository.exists("SELECT id FROM users WHERE id = ?", ownerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Владелец не найден");
        }
    }

    public long accountOwner(Long accountId) {
        return ((Number) repository.one("SELECT owner_id FROM money_accounts WHERE id = ?", accountId)
                .get("owner_id")).longValue();
    }

    public void goalMatchesAccount(Long goalId, Long accountId) {
        if (!repository.exists("SELECT id FROM savings_goals WHERE id = ? AND account_id = ?", goalId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Цель не связана с указанным счётом");
        }
    }

    public void categoryMatchesAccount(Long categoryId, Long accountId) {
        long ownerId = accountOwner(accountId);
        if (!repository.exists("SELECT id FROM budget_categories WHERE id = ? AND owner_id = ?", categoryId, ownerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Категория принадлежит другому владельцу или не найдена");
        }
    }
}
