package tests.tests;

import org.example.rewards.client.api.UsersApi;
import org.junit.jupiter.api.Test;
import tests.clients.UserClients;
import tests.fixture.UserRequests;
import tests.steps.UserSteps;

public class UserTest {

    UsersApi usersApi = new UserClients().asAdmin();
    UserSteps userSteps = new UserSteps(usersApi);
    UserRequests userRequests = new UserRequests();

    @Test
    public void createUser() {
        var user = userRequests.validUser();
        var response = userSteps.createUser(user);
        userSteps.assertUserCreated(response, user);
    }
}
