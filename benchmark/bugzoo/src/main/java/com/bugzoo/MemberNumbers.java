package com.bugzoo;

import org.springframework.stereotype.Component;

/** Hands out member numbers: 1, 2, 3 and so on. */
@Component
public class MemberNumbers {
    private final MemberRepository members;

    public MemberNumbers(MemberRepository members) {
        this.members = members;
    }

    public int next() {
        return members.highestNumber() + 1;
    }
}
