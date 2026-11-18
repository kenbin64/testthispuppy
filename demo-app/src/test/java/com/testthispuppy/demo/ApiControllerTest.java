package com.testthispuppy.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Controller tests for the JSON API, with the database replaced by mocks. */
@WebMvcTest(ApiController.class)
class ApiControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CustomerRepository customers;
    @MockitoBean
    private PurchaseOrderRepository orders;

    private static Customer customer(String name) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(name.toLowerCase() + "@example.test");
        return c;
    }

    @Test
    void listsCustomers() throws Exception {
        when(customers.findAll()).thenReturn(List.of(customer("Lorem"), customer("Ipsum")));
        mvc.perform(get("/api/customers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].name").value("Ipsum"));
    }

    @Test
    void readsOneCustomerOr404() throws Exception {
        Customer lorem = customer("Lorem");
        ReflectionTestUtils.setField(lorem, "createdAt", LocalDateTime.of(2026, 11, 16, 9, 30));   // the database fills this in
        when(customers.findById(1L)).thenReturn(Optional.of(lorem));
        when(customers.findById(2L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/customers/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Lorem"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-16T09:30:00"));
        mvc.perform(get("/api/customers/2")).andExpect(status().isNotFound());
    }

    @Test
    void listsOrders() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerId(1L);
        order.setProduct("Dolor");
        order.setQuantity(3);
        when(orders.findAll()).thenReturn(List.of(order));
        mvc.perform(get("/api/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].product").value("Dolor"))
                .andExpect(jsonPath("$[0].quantity").value(3));
    }

    @Test
    void aNonNumericIdIsABadRequestNotACrash() throws Exception {
        mvc.perform(get("/api/customers/1 OR 1=1")).andExpect(status().isBadRequest());
    }

    @Test
    void createsAGoodCustomer() throws Exception {
        when(customers.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lorem\", \"email\": \"lorem@example.test\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Lorem"));
    }

    @Test
    void refusesACustomerWithoutAnEmail() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lorem\"}"))
                .andExpect(status().isBadRequest());
        verify(customers, never()).save(any());
    }

    // Each half of the "complete customer" rule on its own. Mutation testing (standard level) showed any one of
    // these checks could be removed and the earlier tests would still pass.
    @Test
    void refusesACustomerWithoutAName() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"lorem@example.test\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \", \"email\": \"lorem@example.test\"}"))
                .andExpect(status().isBadRequest());
        verify(customers, never()).save(any());
    }

    @Test
    void refusesAnEmailWithoutAnAtSign() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lorem\", \"email\": \"lorem.example.test\"}"))
                .andExpect(status().isBadRequest());
        verify(customers, never()).save(any());
    }
}
