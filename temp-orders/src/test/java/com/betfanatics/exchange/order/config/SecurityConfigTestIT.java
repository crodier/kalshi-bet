package com.betfanatics.exchange.order.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ImportAutoConfiguration(exclude = KafkaAutoConfiguration.class)
@ActiveProfiles("test")
class SecurityConfigTestIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    RestClient restClient;
    

    @Test
    @WithMockUser(authorities = SecurityConfig.AUTHORITY_READ)
    void testPostWithWrongScope() throws Exception {

        // Given
        Map<String, String> payload = new HashMap<>();
        payload.put("key", "value");

        // When/Then
        mockMvc.perform(post("/foo/bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(payload)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void testUnauthorizedAccess() throws Exception {

        // When/Then
        mockMvc.perform(get("/foo/bar"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticatedAccess() throws Exception {

        // When/Then
        mockMvc.perform(get("/foo/bar"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetActuatorNoAuth() throws Exception {

        // When/Then
        mockMvc.perform(get("/actuator"))
            .andExpect(status().isOk());
    }
}
