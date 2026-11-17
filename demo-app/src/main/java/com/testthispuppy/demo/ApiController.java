package com.testthispuppy.demo;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The JSON API: list, read and create customers, and list orders. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final CustomerRepository customers;
    private final PurchaseOrderRepository orders;

    public ApiController(CustomerRepository customers, PurchaseOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @GetMapping("/customers")
    public List<Customer> listCustomers() {
        return customers.findAll();
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> readCustomer(@PathVariable Long id) {
        return customers.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/customers")
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        boolean complete = customer.getName() != null && !customer.getName().isBlank()
                && customer.getEmail() != null && customer.getEmail().contains("@");
        ResponseEntity<Customer> response = complete
                ? ResponseEntity.status(HttpStatus.CREATED).body(customers.save(customer))
                : ResponseEntity.badRequest().build();
        return response;
    }

    @GetMapping("/orders")
    public List<PurchaseOrder> listOrders() {
        return orders.findAll();
    }
}
