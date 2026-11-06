package ru.example.budget.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Locale;
import java.util.stream.Collectors;

/** Optional local one-time DB initialization, before Spring creates the limited DB connection. */
public final class DatabaseBootstrap {
    private DatabaseBootstrap() {}

    private static String setting(String name, String defaultValue) {
        String environment = System.getenv(name);
        if (environment != null) return environment;
        return System.getProperty(name, defaultValue);
    }

    public static void runIfRequested() throws Exception {
        if (!"true".equalsIgnoreCase(setting("DB_BOOTSTRAP", "false"))) return;
        String url = setting("DB_URL",
                "jdbc:h2:file:./data/personal_budget;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
        String admin = setting("DB_ADMIN_USER", "sa");
        String adminPassword = setting("DB_ADMIN_PASSWORD", "");
        String appUser = setting("DB_USER", "budget_app");
        String appPassword = setting("DB_PASSWORD", null);
        if (appPassword == null || appPassword.isBlank()) {
            throw new IllegalArgumentException("Set DB_PASSWORD in the environment before bootstrap");
        }
        if (!appUser.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            throw new IllegalArgumentException("DB_USER must be a simple SQL identifier");
        }
        try (Connection conn = DriverManager.getConnection(url, admin, adminPassword);
             Statement statement = conn.createStatement()) {
            String script;
            try (InputStream stream = DatabaseBootstrap.class.getResourceAsStream("/schema.sql")) {
                if (stream == null) throw new IllegalStateException("schema.sql is missing");
                script = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .lines().filter(line -> !line.strip().startsWith("--"))
                        .collect(Collectors.joining("\n"));
            }
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
            // H2 has no prepared statement placeholders for CREATE USER PASSWORD.
            // Escape the SQL literal; never log the actual password.
            String escapedPassword = appPassword.replace("'", "''");
            String quotedName = appUser.toUpperCase(Locale.ROOT);
            statement.execute("CREATE USER IF NOT EXISTS " + quotedName + " PASSWORD '" + escapedPassword + "'");
            statement.execute("ALTER USER " + quotedName + " SET PASSWORD '" + escapedPassword + "'");
            // DML only. The API must not be able to CREATE, DROP or ALTER tables.
            for (String table : new String[] {"users", "money_accounts", "budget_categories",
                    "savings_goals", "financial_transactions", "account_transfers"}) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + table + " TO " + quotedName);
            }
            // Demo records, inserted once, and never overwrite user-created data.
            statement.execute("INSERT INTO users(name,email) " +
                    "SELECT 'Demo user','demo.lab3@example.local' WHERE NOT EXISTS " +
                    "(SELECT 1 FROM users WHERE email='demo.lab3@example.local')");
            statement.execute("INSERT INTO money_accounts(owner_id,name,initial_balance) " +
                    "SELECT u.id,'Demo wallet',15000 FROM users u " +
                    "WHERE u.email='demo.lab3@example.local' AND NOT EXISTS " +
                    "(SELECT 1 FROM money_accounts a WHERE a.owner_id=u.id AND a.name='Demo wallet')");
            statement.execute("INSERT INTO money_accounts(owner_id,name,initial_balance) " +
                    "SELECT u.id,'Demo savings',2000 FROM users u " +
                    "WHERE u.email='demo.lab3@example.local' AND NOT EXISTS " +
                    "(SELECT 1 FROM money_accounts a WHERE a.owner_id=u.id AND a.name='Demo savings')");
            statement.execute("INSERT INTO budget_categories(owner_id,name,monthly_limit) " +
                    "SELECT u.id,'Demo food',3000 FROM users u " +
                    "WHERE u.email='demo.lab3@example.local' AND NOT EXISTS " +
                    "(SELECT 1 FROM budget_categories c WHERE c.owner_id=u.id AND c.name='Demo food')");
            statement.execute("INSERT INTO savings_goals(owner_id,account_id,name,target_amount,status) " +
                    "SELECT u.id,a.id,'Demo trip',10000,'ACTIVE' FROM users u " +
                    "JOIN money_accounts a ON a.owner_id=u.id AND a.name='Demo savings' " +
                    "WHERE u.email='demo.lab3@example.local' AND NOT EXISTS " +
                    "(SELECT 1 FROM savings_goals g WHERE g.owner_id=u.id AND g.name='Demo trip')");
            statement.execute("INSERT INTO financial_transactions(account_id,amount,type,occurred_at,posted) " +
                    "SELECT a.id,500,'INCOME',CURRENT_TIMESTAMP,TRUE FROM money_accounts a " +
                    "JOIN users u ON u.id=a.owner_id " +
                    "WHERE u.email='demo.lab3@example.local' AND a.name='Demo wallet' " +
                    "AND NOT EXISTS (SELECT 1 FROM financial_transactions t WHERE t.account_id=a.id " +
                    "AND t.type='INCOME' AND t.amount=500)");
        }
    }
}
