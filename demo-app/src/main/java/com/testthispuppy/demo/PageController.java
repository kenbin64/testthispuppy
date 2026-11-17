package com.testthispuppy.demo;

import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The HTML pages: a new-customer form and a new-order form, each confirming what it saved. */
@Controller
public class PageController {
    private final CustomerRepository customers;
    private final PurchaseOrderRepository orders;

    public PageController(CustomerRepository customers, PurchaseOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @GetMapping("/customers/new")
    public String showCustomerForm() {
        return "customer-form";
    }

    @PostMapping("/customers")
    public String saveCustomer(@RequestParam String name, @RequestParam String email,
                               @RequestParam(required = false) String phone, Model model) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        model.addAttribute("message", "Saved customer " + customers.save(customer).getId());
        return "saved";
    }

    @GetMapping("/orders/new")
    public String showOrderForm(Model model) {
        model.addAttribute("customers", customers.findAll());
        return "order-form";
    }

    @PostMapping("/orders")
    public String saveOrder(@RequestParam Long customerId, @RequestParam String product,
                            @RequestParam Integer quantity, @RequestParam LocalDate orderDate, Model model) {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerId(customerId);
        order.setProduct(product);
        order.setQuantity(quantity);
        order.setOrderDate(orderDate);
        model.addAttribute("message", "Saved order " + orders.save(order).getId());
        return "saved";
    }
}
