package tests.steps;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.example.rewards.client.api.UsersApi;
import org.example.rewards.client.model.CreateUser;

import static tests.assertions.HttpAssertions.assertStatusCode;
import static tests.assertions.JsonAssertions.assertNumberRegexp;
import static tests.assertions.JsonAssertions.assertTextEquals;

public class UserSteps {

    private final UsersApi client;

    public UserSteps(UsersApi client) {
        this.client = client;
    }

    @Step("Создать юзера")
    public Response createUser(CreateUser user) {
        Response response = client.createUser().body(user).execute(res -> res);
        response.then().log().body().log().status();
        Allure.addAttachment("Ответ запроса создания юзера", "application/json", response.asString());
        return response;
    }

    @Step("Проверить корректное создание юзера")
    public void assertUserCreated(Response response, CreateUser user) {
        assertStatusCode(response, HttpStatus.SC_CREATED);
        assertTextEquals(response, "name", user.getName());
        assertTextEquals(response, "email", user.getEmail());
        assertNumberRegexp(response, "id", 100_000L, 999_999_999_999L);
    }
}
