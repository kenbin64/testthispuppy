package com.bugzoo;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The new-order form. */
@Controller
public class OrderController {
    private final MemberRepository members;
    private final OrderRepository orders;

    public OrderController(MemberRepository members, OrderRepository orders) {
        this.members = members;
        this.orders = orders;
    }

    @GetMapping("/orders/new")
    public String showForm(Model model) {
        model.addAttribute("members", members.findAll());
        return "order-form";
    }

    @PostMapping("/orders")
    public String save(@RequestParam Long memberId, @RequestParam(defaultValue = "") String product,
                       @RequestParam Integer quantity, @RequestParam LocalDate orderDate,
                       Model model, HttpServletResponse response) {
        if (product.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Product is required.");
            model.addAttribute("members", members.findAll());
            return "order-form";
        }
        if (quantity < 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Quantity must be at least 1.");
            model.addAttribute("members", members.findAll());
            return "order-form";
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setMemberId(memberId);
        order.setProduct(product.trim());
        order.setQuantity(quantity);
        order.setOrderDate(orderDate);
        model.addAttribute("message", "Saved order " + orders.save(order).getId());
        return "saved";
    }
}
