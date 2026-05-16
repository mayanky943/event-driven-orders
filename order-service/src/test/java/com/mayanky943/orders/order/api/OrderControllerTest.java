package com.mayanky943.orders.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayanky943.orders.order.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void postCreatesOrder() throws Exception {
        CreateOrderRequest req = sampleRequest();
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("cust-x"));
    }

    @Test
    void postWithoutLinesIs400() throws Exception {
        Map<String, Object> bad = Map.of("customerId", "x", "lines", List.of());
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithoutCustomerIs400() throws Exception {
        Map<String, Object> bad = Map.of("customerId", "", "lines",
                List.of(Map.of("sku", "A", "quantity", 1, "unitPrice", "5.00")));
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsCreatedOrder() throws Exception {
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(sampleRequest())))
                .andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/orders/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private CreateOrderRequest sampleRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId("cust-x");
        CreateOrderRequest.Line line = new CreateOrderRequest.Line();
        line.setSku("SKU-A");
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("9.99"));
        req.setLines(List.of(line));
        return req;
    }
}
