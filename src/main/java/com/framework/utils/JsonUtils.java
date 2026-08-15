package com.framework.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.framework.exceptions.FrameworkException;
import io.restassured.path.json.JsonPath;

import java.io.File;
import java.util.List;

/**
 * Central Jackson wrapper. One ObjectMapper instance, configured once,
 * reused everywhere — avoids every test class configuring its own mapper
 * differently (and getting different null/date handling as a result).
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonUtils() {
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new FrameworkException("Failed to serialize object to JSON: " + object, e);
        }
    }

    public static String toPrettyJson(Object object) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            throw new FrameworkException("Failed to pretty-print object to JSON: " + object, e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new FrameworkException("Failed to deserialize JSON to " + clazz.getSimpleName(), e);
        }
    }

    public static <T> List<T> fromJsonList(String json, Class<T> elementClass) {
        try {
            return MAPPER.readerForListOf(elementClass).readValue(json);
        } catch (Exception e) {
            throw new FrameworkException("Failed to deserialize JSON array to List<"
                    + elementClass.getSimpleName() + ">", e);
        }
    }

    public static <T> T fromFile(File file, Class<T> clazz) {
        try {
            return MAPPER.readValue(file, clazz);
        } catch (Exception e) {
            throw new FrameworkException("Failed to read JSON file: " + file.getAbsolutePath(), e);
        }
    }

    /** Wraps a raw JSON body for ad-hoc JsonPath queries without a POJO. */
    public static JsonPath jsonPath(String json) {
        return new JsonPath(json);
    }
}
