package ru.example.budget.factory;

import ru.example.budget.annotation.Cumponent;
import ru.example.budget.domain.MoneyAccount;
import java.math.BigDecimal;

@Cumponent
public class MoneyAccountFactory {
    public MoneyAccount create(Long id, Long ownerId, String name, BigDecimal initialBalance) {
        MoneyAccount entity = new MoneyAccount();
        entity.id = id;
        entity.ownerId = ownerId;
        entity.name = name;
        entity.initialBalance = initialBalance;
        return entity;
    }
}
