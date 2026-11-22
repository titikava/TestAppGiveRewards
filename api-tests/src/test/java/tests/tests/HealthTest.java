package tests.tests;

import org.junit.jupiter.api.Test;
import tests.clients.HealthClient;
import tests.steps.HealthSteps;

public class HealthTest {

    HealthClient client = new HealthClient();
    HealthSteps steps = new HealthSteps(client);

    @Test
    void checkAliveStatus() {
        var health = steps.getHealth();
        steps.assertHealthy(health);
    }
}
