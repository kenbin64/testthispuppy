package com.bugzoo;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The JSON API: members, member search, orders, and renaming a product. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final MemberRepository members;
    private final OrderRepository orders;
    private final MemberNumbers numbers;
    private final JdbcTemplate jdbc;

    public ApiController(MemberRepository members, OrderRepository orders, MemberNumbers numbers, JdbcTemplate jdbc) {
        this.members = members;
        this.orders = orders;
        this.numbers = numbers;
        this.jdbc = jdbc;
    }

    @GetMapping("/members")
    public List<Member> listMembers() {
        return members.findAll();
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<Member> readMember(@PathVariable Long id) {
        return members.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/members")
    public ResponseEntity<Member> createMember(@RequestBody Member member) {
        if (member.getName() == null || member.getName().isBlank() || member.getEmail() == null || !member.getEmail().contains("@")) {
            return ResponseEntity.badRequest().build();
        }
        member.setMemberNumber(numbers.next());
        return ResponseEntity.status(HttpStatus.CREATED).body(members.save(member));
    }

    @GetMapping("/members/search")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String name) {
        return jdbc.queryForList("SELECT id, name, email FROM member WHERE name LIKE '%" + name + "%'");
    }

    @GetMapping("/orders")
    public List<PurchaseOrder> listOrders() {
        return orders.findAll();
    }

    @PutMapping("/products/{id}/name")
    public ResponseEntity<Void> renameProduct(@PathVariable Long id, @RequestBody String name) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM product WHERE id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            return ResponseEntity.notFound().build();
        }
        jdbc.update("UPDATE product SET name = ?", name.trim());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> listProducts() {
        return jdbc.queryForList("SELECT id, name, stock FROM product ORDER BY id");
    }
}
