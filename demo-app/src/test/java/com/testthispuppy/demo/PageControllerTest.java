package com.testthispuppy.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Controller tests for the HTML forms, with the database replaced by mocks.
 * The four "regression" tests pin the bugs TestThisPuppy's wrong-input tests found on 2026-11-16,
 * so they cannot come back unnoticed.
 */
@WebMvcTest(PageController.class)
class PageControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CustomerRepository customers;
    @MockitoBean
    private PurchaseOrderRepository orders;

    @Test
    void theFormsOpen() throws Exception {
        mvc.perform(get("/customers/new")).andExpect(status().isOk()).andExpect(view().name("customer-form"));
        mvc.perform(get("/orders/new")).andExpect(status().isOk()).andExpect(view().name("order-form"));
    }

    @Test
    void aGoodCustomerIsSaved() throws Exception {
        Customer saved = new Customer();
        ReflectionTestUtils.setField(saved, "id", 12L);
        when(customers.save(any())).thenReturn(saved);
        mvc.perform(post("/customers").param("name", "  Lorem Ipsum ").param("email", " lorem@example.test ").param("phone", "801-555-0001"))
                .andExpect(status().isOk()).andExpect(view().name("saved"))
                .andExpect(model().attribute("message", "Saved customer 12"));
        // What was saved, not only that something was: mutation testing showed every field could be dropped unnoticed.
        ArgumentCaptor<Customer> sent = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(sent.capture());
        assertEquals("Lorem Ipsum", sent.getValue().getName(), "the name is saved, trimmed");
        assertEquals("lorem@example.test", sent.getValue().getEmail(), "the email is saved, trimmed");
        assertEquals("801-555-0001", sent.getValue().getPhone(), "the phone is saved");
    }

    @Test
    void regressionABlankNameIsRefused() throws Exception {
        mvc.perform(post("/customers").param("name", "  ").param("email", "lorem@example.test"))
                .andExpect(status().isBadRequest()).andExpect(view().name("customer-form"));
        verify(customers, never()).save(any());
    }

    @Test
    void regressionABadEmailIsRefused() throws Exception {
        mvc.perform(post("/customers").param("name", "Lorem").param("email", "not-an-email"))
                .andExpect(status().isBadRequest()).andExpect(model().attributeExists("error"));
        verify(customers, never()).save(any());
    }

    @Test
    void regressionAnOrderForAMissingCustomerIsRefusedNotACrash() throws Exception {
        when(customers.existsById(999999999L)).thenReturn(false);
        mvc.perform(post("/orders").param("customerId", "999999999").param("product", "Dolor")
                        .param("quantity", "2").param("orderDate", "2026-11-16"))
                .andExpect(status().isBadRequest()).andExpect(view().name("order-form"));
        verify(orders, never()).save(any());
    }

    @Test
    void regressionABlankProductIsRefused() throws Exception {
        when(customers.existsById(1L)).thenReturn(true);
        mvc.perform(post("/orders").param("customerId", "1").param("product", "")
                        .param("quantity", "2").param("orderDate", "2026-11-16"))
                .andExpect(status().isBadRequest());
        verify(orders, never()).save(any());
    }

    @Test
    void aZeroQuantityIsRefused() throws Exception {
        when(customers.existsById(1L)).thenReturn(true);
        mvc.perform(post("/orders").param("customerId", "1").param("product", "Dolor")
                        .param("quantity", "0").param("orderDate", "2026-11-16"))
                .andExpect(status().isBadRequest());
        verify(orders, never()).save(any());
    }

    @Test
    void aGoodOrderIsSaved() throws Exception {
        PurchaseOrder saved = new PurchaseOrder();
        ReflectionTestUtils.setField(saved, "id", 5L);
        when(customers.existsById(1L)).thenReturn(true);
        when(orders.save(any())).thenReturn(saved);
        mvc.perform(post("/orders").param("customerId", "1").param("product", " Dolor ")
                        .param("quantity", "2").param("orderDate", "2026-11-16"))
                .andExpect(status().isOk()).andExpect(model().attribute("message", "Saved order 5"));
        ArgumentCaptor<PurchaseOrder> sent = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orders).save(sent.capture());
        assertEquals(1L, sent.getValue().getCustomerId(), "the order belongs to the chosen customer");
        assertEquals("Dolor", sent.getValue().getProduct(), "the product is saved, trimmed");
        assertEquals(2, sent.getValue().getQuantity(), "the quantity is saved");
        assertEquals(LocalDate.of(2026, 11, 16), sent.getValue().getOrderDate(), "the order date is saved");
    }

    @Test
    void theSmallestQuantityOneIsAccepted() throws Exception {
        // The edge of the rule "at least 1": mutation testing showed the check could slip to "more than 1" unnoticed.
        when(customers.existsById(1L)).thenReturn(true);
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/orders").param("customerId", "1").param("product", "Dolor")
                        .param("quantity", "1").param("orderDate", "2026-11-16"))
                .andExpect(status().isOk()).andExpect(view().name("saved"));
    }
}
