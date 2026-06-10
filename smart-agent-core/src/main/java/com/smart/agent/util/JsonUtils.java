package com.smart.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * JSON工具类
 *
 * @description 基于Jackson的JSON序列化与反序列化工具类，提供对象与JSON字符串的互转功能
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
     * JSON序列化
     *
     * @description 将Java对象序列化为JSON字符串
     * @param obj 待序列化的对象
     * @return JSON字符串，序列化失败时返回空字符串
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
     * JSON格式化序列化
     *
     * @description 将Java对象序列化为格式化（美化缩进）的JSON字符串
     * @param obj 待序列化的对象
     * @return 格式化的JSON字符串，序列化失败时返回空字符串
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
     * JSON反序列化
     *
     * @description 将JSON字符串反序列化为指定Class类型的Java对象
     * @param json JSON字符串
     * @param tClass 目标类型的Class对象
     * @return 包含反序列化结果的Optional，失败时返回Optional.empty()
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
     * JSON反序列化（泛型类型）
     *
     * @description 将JSON字符串反序列化为指定TypeReference泛型类型的Java对象，适用于带泛型的复杂类型
     * @param json JSON字符串
     * @param typeReference 目标泛型类型引用
     * @return 包含反序列化结果的Optional，失败时返回Optional.empty()
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
