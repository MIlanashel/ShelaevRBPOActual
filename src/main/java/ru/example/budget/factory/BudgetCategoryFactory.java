package ru.example.budget.factory;

import ru.example.budget.annotation.Cumponent;
import ru.example.budget.domain.BudgetCategory;
import java.math.BigDecimal;

@Cumponent
public class BudgetCategoryFactory {
    public BudgetCategory create(Long id, Long ownerId, String name, BigDecimal monthlyLimit) {
        BudgetCategory entity = new BudgetCategory();
        entity.id = id;
        entity.ownerId = ownerId;
        entity.name = name;
        entity.monthlyLimit = monthlyLimit;
        return entity;
    }
}
