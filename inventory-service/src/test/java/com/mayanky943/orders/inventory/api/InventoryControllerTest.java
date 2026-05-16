package com.mayanky943.orders.inventory.api;

import com.mayanky943.orders.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InventoryControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getAllReturnsSeededItems() throws Exception {
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").isNumber());
    }

    @Test
    void getBySkuReturnsItem() throws Exception {
        mockMvc.perform(get("/inventory/SKU-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-A"));
    }
}
