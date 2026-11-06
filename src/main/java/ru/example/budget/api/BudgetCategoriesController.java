package ru.example.budget.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.service.BudgetCategoryService;
import java.util.List;

@Cumponent
@RestController
@RequestMapping("/api/budget-categories")
public class BudgetCategoriesController {
    private final BudgetCategoryService service;

    public BudgetCategoriesController(BudgetCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResourceFacade> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public ResourceFacade one(@PathVariable long id) {
        return service.one(id);
    }

    @PostMapping
    public ResponseEntity<ResourceFacade> create(@RequestBody ResourceFacade request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public ResourceFacade patch(@PathVariable long id, @RequestBody ResourceFacade request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
