package com.smart.agent.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类
 *
 * @description 提供文本中敏感信息的自动识别与脱敏处理，支持手机号和身份证号的掩码替换
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class SensitiveUtils {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3456789]\\d{9})");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b(\\d{6})(\\d{8})(\\d{4})\\b");

    private SensitiveUtils() {
    }

    /**
     * 敏感信息脱敏
     *
     * @description 对文本中的手机号和身份证号进行掩码处理，手机号保留前3后4位，身份证号保留前6后4位
     * @param text 待脱敏的文本内容
     * @return 脱敏后的文本，输入为null或空时原样返回
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            text = maskPhoneNumbers(text);
            return maskIdCards(text);
        } catch (Exception e) {
            log.info("mask error: {}", e.getMessage());
            return text;
        }
    }

    private static String maskPhoneNumbers(String input) {
        Matcher matcher = PHONE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String phone = matcher.group(1);
            matcher.appendReplacement(sb, phone.substring(0, 3) + "****" + phone.substring(7));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskIdCards(String input) {
        Matcher matcher = ID_CARD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + "**********" + matcher.group(3));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
