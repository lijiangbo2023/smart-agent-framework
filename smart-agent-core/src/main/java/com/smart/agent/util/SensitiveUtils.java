package com.smart.agent.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sensitive data masking utility.
 *
 * @description Provides automatic identification and masking of sensitive information in text, supporting phone number and ID card number replacement
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class SensitiveUtils {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3456789]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b(\\d{6})(\\d{8})(\\d{3}[\\dXx])\\b");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(\\d{6,9})(\\d{4})(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+\\-]+)@([a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("((?:sk|pk|key|token|Bearer|AKIA)[\\-_]?[a-zA-Z0-9]{20,})");

    private SensitiveUtils() {
    }

    /**
     * Mask sensitive information.
     *
     * @description Masks phone numbers and ID card numbers in text; phone numbers keep the first 3 and last 4 digits, ID cards keep the first 6 and last 4 digits
     * @param text the text to be masked
     * @return the masked text, returned as-is when input is null or empty
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            text = maskIdCards(text);
            text = maskBankCards(text);
            text = maskPhoneNumbers(text);
            text = maskEmails(text);
            text = maskTokens(text);
            return text;
        } catch (Exception e) {
            log.warn("mask error: {}", e.getMessage());
            return text;
        }
    }

    private static String maskPhoneNumbers(String input) {
        Matcher matcher = PHONE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String phone = matcher.group(1);
            matcher.appendReplacement(sb, phone.substring(0, 3) + "****" + phone.substring(7));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskIdCards(String input) {
        Matcher matcher = ID_CARD_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + "**********" + matcher.group(3));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskBankCards(String input) {
        Matcher matcher = BANK_CARD_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String middle = matcher.group(2);
            String masked = "*".repeat(middle.length());
            matcher.appendReplacement(sb, matcher.group(1) + masked + matcher.group(3));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskEmails(String input) {
        Matcher matcher = EMAIL_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String local = matcher.group(1);
            String domain = matcher.group(2);
            String maskedLocal;
            if (local.length() <= 2) {
                maskedLocal = local.charAt(0) + "***";
            } else {
                maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
            }
            matcher.appendReplacement(sb, maskedLocal + "@" + domain);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskTokens(String input) {
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String masked;
            if (token.length() <= 8) {
                masked = "****";
            } else {
                masked = token.substring(0, 4) + "****" + token.substring(token.length() - 4);
            }
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
