package id.ac.ui.cs.advprog.backend.controller;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void healthShouldReturnUpStatusAndServiceName() {
        HealthController controller = new HealthController();

        Map<String, String> response = controller.health();

        assertEquals("UP", response.get("status"));
        assertEquals("bidmart-gateway", response.get("service"));
    }
}
