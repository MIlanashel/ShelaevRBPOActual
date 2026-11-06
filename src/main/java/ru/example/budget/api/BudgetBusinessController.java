package ru.example.budget.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.service.BudgetBusinessService;

import java.util.Map;

@Cumponent
@RestController
@RequestMapping("/api/business")
public class BudgetBusinessController {
    private final BudgetBusinessService service;

    public BudgetBusinessController(BudgetBusinessService service) {
        this.service = service;
    }

    @PostMapping("/transfers")
    public Map<String, Object> transfer(@RequestBody Map<String, Object> request) {
        return service.transfer(request);
    }

    @PostMapping("/categories/{id}/spend")
    public ResourceFacade spend(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return service.spend(id, request);
    }

    @PostMapping("/goals/{id}/contribute")
    public Map<String, Object> contribute(@PathVariable long id, @RequestBody Map<String, Object> request) {
        return service.contribute(id, request);
    }

    @PostMapping("/transactions/{id}/post")
    public ResourceFacade postDraft(@PathVariable long id) {
        return service.postDraft(id);
    }

    @PostMapping("/goals/{id}/complete")
    public ResourceFacade completeGoal(@PathVariable long id) {
        return service.completeGoal(id);
    }
}
