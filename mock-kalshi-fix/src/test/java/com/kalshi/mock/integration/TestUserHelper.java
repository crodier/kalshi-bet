package com.kalshi.mock.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

@Component
@ActiveProfiles("test")
public class TestUserHelper {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public void createTestUser(String userId, String accessKey) {
        String sql = "MERGE INTO users (user_id, access_key, email, full_name) " +
                     "KEY (user_id) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, userId, accessKey, 
                           userId + "@test.com", "Test " + userId);
    }
    
    public void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM trades");
        jdbcTemplate.update("DELETE FROM orders WHERE user_id LIKE 'test-%'");
        jdbcTemplate.update("DELETE FROM positions WHERE user_id LIKE 'test-%'");
        jdbcTemplate.update("DELETE FROM users WHERE user_id LIKE 'test-%'");
    }
}