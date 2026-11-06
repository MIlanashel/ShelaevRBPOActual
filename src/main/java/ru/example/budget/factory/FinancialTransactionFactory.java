package ru.example.budget.factory;

import ru.example.budget.annotation.Cumponent;
import ru.example.budget.domain.FinancialTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Cumponent
public class FinancialTransactionFactory {
    public FinancialTransaction create(Long id, Long accountId, Long categoryId, Long goalId, BigDecimal amount, String type, LocalDateTime occurredAt, boolean posted) {
        FinancialTransaction entity = new FinancialTransaction();
        entity.id = id;
        entity.accountId = accountId;
        entity.categoryId = categoryId;
        entity.goalId = goalId;
        entity.amount = amount;
        entity.type = type;
        entity.occurredAt = occurredAt;
        entity.posted = posted;
        return entity;
    }
}
