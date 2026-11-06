package ru.example.budget.domain;

import java.math.BigDecimal;

public final class SavingsGoal {
    public Long id;
    public Long ownerId;
    public Long accountId;
    public String name;
    public BigDecimal targetAmount;
    public String status;
}
