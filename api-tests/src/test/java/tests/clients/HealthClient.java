package tests.clients;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;

import static tests.config.TestConstants.API_BASE_URL;

public class HealthClient {

    private static final String HEALTH_PATH = "/actuator/health";

    public Response getHealth() {
        var response = RestAssured.given()
                .baseUri(API_BASE_URL)
                .log().method()
                .log().uri()
                .get(HEALTH_PATH);
        response.then().log().status().log().body();
        return response;

    }
}
