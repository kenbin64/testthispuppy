package com.bugzoo;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The new-member form. */
@Controller
public class MemberController {
    private final MemberRepository members;
    private final MemberNumbers numbers;

    public MemberController(MemberRepository members, MemberNumbers numbers) {
        this.members = members;
        this.numbers = numbers;
    }

    @GetMapping("/members/new")
    public String showForm() {
        return "member-form";
    }

    @PostMapping("/members")
    public String save(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String email,
                       Model model, HttpServletResponse response) {
        if (name.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Name is required.");
            return "member-form";
        }
        Member member = new Member();
        member.setName(name.trim());
        member.setMemberNumber(numbers.next());
        Member saved = members.save(member);
        model.addAttribute("message", "Saved member " + saved.getId());
        return "saved";
    }
}
