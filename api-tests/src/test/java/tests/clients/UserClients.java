package tests.clients;

import io.restassured.RestAssured;
import org.example.rewards.client.ApiClient;
import org.example.rewards.client.api.UsersApi;

public class UserClients {
    public UsersApi asAdmin() {
        var client = ApiClient.api(ApiClient.Config.apiConfig());
        UsersApi usersApi = client.users();
        var auth = RestAssured.preemptive().basic("admin", System.getenv("ADMIN_PASSWORD"));
        return usersApi.reqSpec(spec -> spec.setAuth(auth));
    }
}
