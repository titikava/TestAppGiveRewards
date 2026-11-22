package tests.utils;

import java.util.UUID;

public class DataGenerator {
    public static String randomSuffix() {
        return UUID.randomUUID().toString();
    }
}
