package ru.example.budget.domain;

import java.math.BigDecimal;

public final class BudgetCategory {
    public Long id;
    public Long ownerId;
    public String name;
    public BigDecimal monthlyLimit;
}
