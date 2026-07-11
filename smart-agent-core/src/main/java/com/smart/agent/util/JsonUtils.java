package com.smart.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * JSON utility.
 *
 * @description A Jackson-based JSON serialization and deserialization utility that provides conversion between Java objects and JSON strings
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtils() {
    }

    /**
     * Serialize to JSON.
     *
     * @description Serializes a Java object into a JSON string
     * @param obj the object to serialize
     * @return the JSON string, or an empty string if serialization fails
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("json serialize exception: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Pretty-print serialize to JSON.
     *
     * @description Serializes a Java object into a formatted (pretty-printed with indentation) JSON string
     * @param obj the object to serialize
     * @return the formatted JSON string, or an empty string if serialization fails
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String prettySerialize(Object obj) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("json serialize exception: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Deserialize from JSON.
     *
     * @description Deserializes a JSON string into a Java object of the specified Class type
     * @param json the JSON string
     * @param tClass the target Class object
     * @return an Optional containing the deserialization result, or Optional.empty() on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <T> Optional<T> deserialize(String json, Class<T> tClass) {
        try {
            return Optional.of(mapper.readValue(json, tClass));
        } catch (Exception e) {
            log.warn("json deserialize exception: {}, class: {}", e.getMessage(), tClass.getName());
        }
        return Optional.empty();
    }

    /**
     * Deserialize from JSON (generic type).
     *
     * @description Deserializes a JSON string into a Java object of the specified TypeReference generic type, suitable for complex parameterized types
     * @param json the JSON string
     * @param typeReference the target generic type reference
     * @return an Optional containing the deserialization result, or Optional.empty() on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static <T> Optional<T> deserialize(String json, TypeReference<T> typeReference) {
        try {
            return Optional.of(mapper.readValue(json, typeReference));
        } catch (Exception e) {
            log.warn("json deserialize exception: {}, type: {}", e.getMessage(), typeReference.getType());
        }
        return Optional.empty();
    }
}
