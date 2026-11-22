package tests.assertions;

import io.restassured.response.Response;
import org.assertj.core.api.Assertions;

public class HttpAssertions {

    public static void assertStatusCode(Response response, int expectedStatusCode) {
        Assertions.assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
    }

}
