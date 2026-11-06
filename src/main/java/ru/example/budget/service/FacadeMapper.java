package ru.example.budget.service;

import org.springframework.stereotype.Service;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.api.ResourceFacade;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Cumponent
@Service
public class FacadeMapper {
    public ResourceFacade toFacade(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (!key.equalsIgnoreCase("id")) data.put(camel(key), value instanceof java.sql.Timestamp stamp ? stamp.toLocalDateTime() : value);
        });
        return new ResourceFacade(((Number) row.get("id")).longValue(), data);
    }

    private String camel(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder output = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            output.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return output.toString();
    }
}
