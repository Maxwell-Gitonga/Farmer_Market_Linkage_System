package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.PurchaseRequest;
import ke.ac.strathmore.fmls.dto.PurchaseResponse;
import ke.ac.strathmore.fmls.dto.StatusChangeRequest;
import ke.ac.strathmore.fmls.dto.TransactionView;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    /** purchaseProduct(productID). */
    @RequireRole(Role.BUYER)
    @PostMapping
    public PurchaseResponse purchase(@Valid @RequestBody PurchaseRequest req) {
        return transactions.purchaseProduct(req);
    }

    @GetMapping("/mine")
    public List<TransactionView> mine() {
        return transactions.mine();
    }

    @PatchMapping("/{id}/status")
    public TransactionView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return transactions.changeStatus(id, req.status());
    }
}
