package com.example.HotelBooking;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring context smoke test.
 * Tagged "spring-context" so CI can exclude it when a backend is already
 * running on port 9090 (would cause a port conflict with @SpringBootTest).
 *
 * Run locally:   mvn test
 * Run in CI:     mvn test -DexcludedGroups=spring-context
 */
@Tag("spring-context")
@SpringBootTest
class HotelBookingApplicationTests {

    @Test
    void contextLoads() {
    }

}
