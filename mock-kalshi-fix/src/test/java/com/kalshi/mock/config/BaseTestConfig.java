package com.kalshi.mock.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * Base configuration annotation for all tests.
 * Disables QuickFIX server and uses test profile.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "quickfix.enabled=false"
})
public @interface BaseTestConfig {
}