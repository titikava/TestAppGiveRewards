package tests.steps;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import tests.clients.HealthClient;

import java.util.ArrayList;
import java.util.List;

import static tests.assertions.HttpAssertions.assertStatusCode;
import static tests.assertions.JsonAssertions.assertTextEquals;
import static tests.assertions.JsonAssertions.assertTextEqualsArray;

public class HealthSteps {

    private final HealthClient client;

    private static final String PATH_STATUS = "status";
    private static final String PATH_GROUPS = "groups";
    private static final String HEALTHY_RESULT = "UP";
    private static final List<String> REQUIRED_GROUPS = new ArrayList<>(List.of("liveness", "readiness"));

    public HealthSteps(HealthClient client) {
        this.client = client;
    }

    @Step("Запросить состояние сервиса")
    public Response getHealth() {
        Response response = client.getHealth();
        Allure.addAttachment("Ответ /actuator/health", "application/json", response.asString(), ".json");
        return response;
    }

    @Step("Проверить статус UP и группы liveness и readiness")
    public void assertHealthy(Response response) {
        assertStatusCode(response, HttpStatus.SC_OK);
        assertTextEquals(response, PATH_STATUS, HEALTHY_RESULT);
        assertTextEqualsArray(response, PATH_GROUPS, REQUIRED_GROUPS);
    }
}