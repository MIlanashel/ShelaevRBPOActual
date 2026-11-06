package ru.example.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class FinancialTransaction {
    public Long id;
    public Long accountId;
    public Long categoryId;
    public Long goalId;
    public BigDecimal amount;
    public String type;
    public LocalDateTime occurredAt;
    public boolean posted;
}
