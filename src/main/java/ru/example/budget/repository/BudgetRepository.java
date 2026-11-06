package ru.example.budget.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Cumponent
@Repository
public class BudgetRepository {
    private final JdbcTemplate jdbc;

    public BudgetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> all(String sql, Object... params) {
        return jdbc.queryForList(sql, params);
    }

    public Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = all(sql, params);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Ресурс не найден");
        return rows.getFirst();
    }

    public boolean exists(String sql, Object... params) {
        return !all(sql, params).isEmpty();
    }

    public long insert(String sql, Object... params) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null) throw new IllegalStateException("База данных не вернула идентификатор");
        return key.longValue();
    }

    public int update(String sql, Object... params) {
        return jdbc.update(sql, params);
    }

    public void delete(String table, long id) {
        // Only internal constant table names may be passed by services.
        int count = jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
        if (count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Ресурс не найден");
    }

    public BigDecimal sum(String sql, Object... params) {
        BigDecimal number = jdbc.queryForObject(sql, BigDecimal.class, params);
        return number == null ? BigDecimal.ZERO : number;
    }
}
