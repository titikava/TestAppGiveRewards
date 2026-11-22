package tests.assertions;

import io.restassured.response.Response;
import org.assertj.core.api.InstanceOfAssertFactories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonAssertions {
    public static void assertTextEquals(Response response, String path, String expectedResult) {
        var result = response.jsonPath().get(path);
        assertThat(result).isEqualTo(expectedResult);
    }

    public static void assertTextEqualsArray(Response response, String path, List<String> expectedGroups) {
        var actualGroups = response.jsonPath().getList(path);
        assertThat(actualGroups).containsExactlyInAnyOrderElementsOf(expectedGroups);
    }

    public static void assertTextRegexp(Response response, String path, String expectedRegexp) {
        var result = response.jsonPath().get(path);
        assertThat(result).asInstanceOf(InstanceOfAssertFactories.STRING).matches(expectedRegexp);
    }

    public static void assertNumberRegexp(Response response, String path, Long min, Long max) {
        var result = response.jsonPath().get(path);
        assertThat(result).isInstanceOfAny(Integer.class, Long.class);
        assertThat(((Number) result).longValue()).isBetween(min, max);
    }
}
