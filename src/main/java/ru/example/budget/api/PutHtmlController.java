package ru.example.budget.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.example.budget.annotation.Cumponent;

@Cumponent
@RestController
public class PutHtmlController {
    private static final String HTML = """
            <!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">
            <title>PUT</title></head><body><h1>PUT request received</h1>
            <p>Static HTML response</p></body></html>
            """;

    @PutMapping(value = {"/api", "/api/**"}, produces = MediaType.TEXT_HTML_VALUE)
    public String staticHtml() {
        return HTML;
    }
}
