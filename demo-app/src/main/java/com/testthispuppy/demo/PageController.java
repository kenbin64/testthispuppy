package com.testthispuppy.demo;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The HTML pages: a new-customer form and a new-order form, each confirming what it saved.
 * The server checks every value itself, because the browser's own checks can be skipped.
 * (TestThisPuppy's wrong-input tests found the four gaps these checks close.)
 */
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
    public String saveCustomer(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String email,
                               @RequestParam(required = false) String phone, Model model, HttpServletResponse response) {
        List<String> problems = new ArrayList<>();
        if (name.isBlank()) {
            problems.add("Name is required.");
        }
        // Checked after trimming, like the name: a pasted address often carries a space at either end.
        // (A stricter test, written after mutation testing, found the untrimmed check refused those.)
        if (!email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            problems.add("Email must be a valid email address.");
        }
        String view;
        if (problems.isEmpty()) {
            Customer customer = new Customer();
            customer.setName(name.trim());
            customer.setEmail(email.trim());
            customer.setPhone(phone);
            model.addAttribute("message", "Saved customer " + customers.save(customer).getId());
            view = "saved";
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", String.join(" ", problems));
            view = "customer-form";
        }
        return view;
    }

    @GetMapping("/orders/new")
    public String showOrderForm(Model model) {
        model.addAttribute("customers", customers.findAll());
        return "order-form";
    }

    @PostMapping("/orders")
    public String saveOrder(@RequestParam Long customerId, @RequestParam(defaultValue = "") String product,
                            @RequestParam Integer quantity, @RequestParam LocalDate orderDate,
                            Model model, HttpServletResponse response) {
        List<String> problems = new ArrayList<>();
        if (!customers.existsById(customerId)) {
            problems.add("That customer does not exist.");
        }
        if (product.isBlank()) {
            problems.add("Product is required.");
        }
        if (quantity < 1) {
            problems.add("Quantity must be at least 1.");
        }
        String view;
        if (problems.isEmpty()) {
            PurchaseOrder order = new PurchaseOrder();
            order.setCustomerId(customerId);
            order.setProduct(product.trim());
            order.setQuantity(quantity);
            order.setOrderDate(orderDate);
            model.addAttribute("message", "Saved order " + orders.save(order).getId());
            view = "saved";
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", String.join(" ", problems));
            model.addAttribute("customers", customers.findAll());
            view = "order-form";
        }
        return view;
    }
}
