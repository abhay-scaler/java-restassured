package com.framework.utils;

import net.datafaker.Faker;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central source of randomized/fake test data. Using one Faker instance
 * per JVM (it's thread-safe) instead of instantiating per test keeps
 * startup cost down under parallel execution.
 */
public final class RandomDataGenerator {

    private static final Faker FAKER = new Faker();

    private RandomDataGenerator() {
    }

    public static String fullName() {
        return FAKER.name().fullName();
    }

    public static String firstName() {
        return FAKER.name().firstName();
    }

    public static String lastName() {
        return FAKER.name().lastName();
    }

    public static String email() {
        return FAKER.internet().emailAddress();
    }

    public static String uniqueEmail() {
        return "qa." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public static String jobTitle() {
        return FAKER.job().title();
    }

    public static String username() {
        return FAKER.name().username();
    }

    public static String password(int length) {
        return FAKER.internet().password(length, length + 4, true, true, true);
    }

    public static String phoneNumber() {
        return FAKER.phoneNumber().cellPhone();
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String company() {
        return FAKER.company().name();
    }

    public static int intBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static String alphanumeric(int length) {
        return FAKER.regexify("[A-Za-z0-9]{" + length + "}");
    }

    public static String futureDate() {
        return FAKER.date().future(365, java.util.concurrent.TimeUnit.DAYS).toString();
    }
}
