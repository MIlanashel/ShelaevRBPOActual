package ru.example.budget.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ResourceFacade;
import ru.example.budget.domain.MoneyAccount;
import ru.example.budget.factory.MoneyAccountFactory;
import ru.example.budget.repository.BudgetRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Cumponent
@Service
public class MoneyAccountService {
    private static final String VIEW = "SELECT a.*, a.initial_balance + " +
            "COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END) " +
            "FROM financial_transactions t WHERE t.account_id = a.id AND t.posted = TRUE), 0) " +
            "+ COALESCE((SELECT SUM(x.amount) FROM account_transfers x WHERE x.to_account_id = a.id), 0) " +
            "- COALESCE((SELECT SUM(x.amount) FROM account_transfers x WHERE x.from_account_id = a.id), 0) " +
            "AS current_balance " +
            "FROM money_accounts a";
    private final BudgetRepository repository;
    private final FacadeMapper mapper;
    private final Rules rules;
    private final MoneyAccountFactory factory;

    public MoneyAccountService(BudgetRepository repository, FacadeMapper mapper, Rules rules, MoneyAccountFactory factory) {
        this.repository = repository;
        this.mapper = mapper;
        this.rules = rules;
        this.factory = factory;
    }

    public List<ResourceFacade> all() {
        return repository.all(VIEW + " ORDER BY a.id").stream().map(mapper::toFacade).toList();
    }

    public ResourceFacade one(long id) {
        return mapper.toFacade(repository.one(VIEW + " WHERE a.id = ?", id));
    }

    @Transactional
    public ResourceFacade create(ResourceFacade request) {
        Map<String, Object> data = Fields.data(request);
        MoneyAccount entity = factory.create(null, Fields.id(data, "ownerId", null),
                Fields.string(data, "name", null), Fields.decimal(data, "initialBalance", BigDecimal.ZERO, false));
        rules.ownerExists(entity.ownerId);
        return one(repository.insert("INSERT INTO money_accounts(owner_id, name, initial_balance) VALUES (?, ?, ?)",
                entity.ownerId, entity.name, entity.initialBalance));
    }

    @Transactional
    public ResourceFacade patch(long id, ResourceFacade request) {
        Map<String, Object> existing = repository.one("SELECT * FROM money_accounts WHERE id = ?", id);
        Map<String, Object> data = Fields.data(request);
        MoneyAccount entity = factory.create(id,
                Fields.id(data, "ownerId", ((Number) existing.get("owner_id")).longValue()),
                Fields.string(data, "name", (String) existing.get("name")),
                Fields.decimal(data, "initialBalance", (BigDecimal) existing.get("initial_balance"), false));
        rules.ownerExists(entity.ownerId);
        // Changing ownership could break the ownership of related goals/categories/transactions.
        if (entity.ownerId != ((Number) existing.get("owner_id")).longValue()) {
            throw new ru.example.budget.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Смена владельца существующего счёта запрещена");
        }
        repository.update("UPDATE money_accounts SET name = ?, initial_balance = ? WHERE id = ?",
                entity.name, entity.initialBalance, id);
        return one(id);
    }

    @Transactional
    public void delete(long id) {
        repository.delete("money_accounts", id);
    }
}
