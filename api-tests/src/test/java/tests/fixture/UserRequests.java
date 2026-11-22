package tests.fixture;

import org.example.rewards.client.model.CreateUser;
import tests.utils.DataGenerator;

public class UserRequests {
    public CreateUser validUser() {
        CreateUser user = new CreateUser();
        String suffix = DataGenerator.randomSuffix();
        user.name("Тестовый пользователь: " + suffix);
        user.email("test_email_" + suffix + "@mail.com");
        user.password("pass_" + suffix);
        return user;
    }
}
