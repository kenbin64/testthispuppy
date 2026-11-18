package com.bugzoo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @Query("select coalesce(max(m.memberNumber), 0) from Member m")
    int highestNumber();
}
