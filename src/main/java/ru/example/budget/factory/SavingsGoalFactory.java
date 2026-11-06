package ru.example.budget.factory;

import ru.example.budget.annotation.Cumponent;
import ru.example.budget.domain.SavingsGoal;
import java.math.BigDecimal;

@Cumponent
public class SavingsGoalFactory {
    public SavingsGoal create(Long id, Long ownerId, Long accountId, String name, BigDecimal targetAmount, String status) {
        SavingsGoal entity = new SavingsGoal();
        entity.id = id;
        entity.ownerId = ownerId;
        entity.accountId = accountId;
        entity.name = name;
        entity.targetAmount = targetAmount;
        entity.status = status;
        return entity;
    }
}
