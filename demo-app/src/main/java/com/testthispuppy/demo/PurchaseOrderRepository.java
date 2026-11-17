package com.testthispuppy.demo;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes purchase_order rows. */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
}
