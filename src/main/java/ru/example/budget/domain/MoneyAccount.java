package ru.example.budget.domain;

import java.math.BigDecimal;

public final class MoneyAccount {
    public Long id;
    public Long ownerId;
    public String name;
    public BigDecimal initialBalance;
}
