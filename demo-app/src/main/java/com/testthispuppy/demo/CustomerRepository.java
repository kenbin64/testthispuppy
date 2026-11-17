package com.testthispuppy.demo;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes customer rows. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
