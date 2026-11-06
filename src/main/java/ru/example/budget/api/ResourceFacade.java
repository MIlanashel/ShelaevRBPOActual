package ru.example.budget.api;

import java.util.Map;

/** All API request/response facades have precisely two fields. */
public record ResourceFacade(Long id, Map<String, Object> data) {
}
