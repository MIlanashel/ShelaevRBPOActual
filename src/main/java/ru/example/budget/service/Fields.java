package ru.example.budget.service;

import org.springframework.http.HttpStatus;
import ru.example.budget.api.ApiException;
import ru.example.budget.api.ResourceFacade;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Fields {
    private Fields() {
    }

    public static Map<String, Object> data(ResourceFacade facade) {
        if (facade == null || facade.data() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Поле data обязательно");
        }
        return facade.data();
    }

    public static String string(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        if (value == null) {
            if (fallback == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Обязательное поле: " + key);
            }
            return fallback;
        }
        if (!(value instanceof String result) || result.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректное поле: " + key);
        }
        return result.trim();
    }

    public static Long id(Map<String, Object> data, String key, Long fallback) {
        Object value = data.get(key);
        if (value == null) {
            if (fallback == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Обязательное поле: " + key);
            }
            return fallback;
        }
        try {
            long result = Long.parseLong(value.toString());
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректное поле: " + key);
        }
    }

    public static Long optionalId(Map<String, Object> data, String key, Long fallback) {
        if (!data.containsKey(key)) return fallback;
        if (data.get(key) == null) return null;
        return id(data, key, fallback);
    }

    public static BigDecimal decimal(Map<String, Object> data, String key, BigDecimal fallback, boolean positive) {
        Object value = data.get(key);
        if (value == null) {
            if (fallback == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Обязательное поле: " + key);
            return fallback;
        }
        try {
            BigDecimal result = new BigDecimal(value.toString());
            if (result.scale() > 2 || (positive ? result.signum() <= 0 : result.signum() < 0)) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректная сумма: " + key);
        }
    }

    public static boolean bool(Map<String, Object> data, String key, boolean fallback) {
        if (!data.containsKey(key)) return fallback;
        if (!(data.get(key) instanceof Boolean result)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректное булево поле: " + key);
        }
        return result;
    }

    public static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
