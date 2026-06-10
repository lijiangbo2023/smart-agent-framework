package com.smart.agent.demo;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 示例工具集
 *
 * @description 提供Agent可调用的示例工具方法，包括获取当前时间、数学表达式计算、城市天气查询和互联网搜索功能
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public class DemoTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 获取当前时间
     *
     * @description 获取当前系统日期和时间，返回格式为 yyyy-MM-dd HH:mm:ss (星期X)
     * @return 格式化的当前日期时间字符串
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "get_current_time", description = "获取当前日期和时间")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)"));
    }

    /**
     * 数学表达式计算器
     *
     * @description 解析并计算数学表达式，支持加减乘除、括号、幂运算和取模运算，输入会经过安全字符过滤
     * @param expression 数学表达式字符串，如 '(3+5)*2' 或 '2^10'
     * @return 计算结果字符串，格式为 "表达式 = 结果"；计算失败时返回错误信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "calculator", description = "计算数学表达式，支持加减乘除、括号、幂运算等。输入为字符串形式的数学表达式")
    public String calculator(
            @ToolParam(name = "expression", description = "数学表达式，如 '(3+5)*2' 或 '2^10'") String expression) {
        try {
            String sanitized = expression.replaceAll("[^0-9+\\-*/().^%\\s]", "");
            double result = evalExpression(sanitized);
            if (result == (long) result) {
                return expression + " = " + (long) result;
            }
            return expression + " = " + result;
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 查询城市天气
     *
     * @description 通过wttr.in API查询指定城市的实时天气信息，包括天气状况、温度、湿度和风速
     * @param city 城市名称，如 '北京'、'上海'
     * @return 天气信息字符串；查询失败时返回错误信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "get_weather", description = "查询指定城市的实时天气信息")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如 '北京'、'上海'") String city) {
        try {
            String url = "https://wttr.in/" + city + "?format=%C+%t+%h+%w&lang=zh";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
                return city + " 天气: " + response.body().trim();
            }
            return "无法获取 " + city + " 的天气信息";
        } catch (Exception e) {
            return "天气查询失败: " + e.getMessage();
        }
    }

    /**
     * 互联网搜索
     *
     * @description 通过DuckDuckGo Lite搜索互联网信息并返回摘要结果，结果最大长度限制为1500字符
     * @param query 搜索关键词
     * @return 搜索结果摘要字符串；搜索失败时返回错误信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "web_search_summary", description = "搜索互联网信息并返回摘要结果")
    public String webSearch(
            @ToolParam(name = "query", description = "搜索关键词") String query) {
        try {
            String url = "https://lite.duckduckgo.com/lite/?q=" + java.net.URLEncoder.encode(query, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            body = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (body.length() > 1500) {
                body = body.substring(0, 1500) + "...";
            }
            return "搜索结果摘要:\n" + body;
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    /**
     * IP 归属地查询
     *
     * @description 通过 ipinfo.io 免费 API 查询 IP 地址的地理位置信息
     * @param ip IP 地址
     * @return IP 归属地信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "ip_lookup", description = "查询IP地址的地理位置和归属信息")
    public String ipLookup(
            @ToolParam(name = "ip", description = "IP地址，如 '8.8.8.8'") String ip) {
        try {
            String url = "https://ipinfo.io/" + ip + "/json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return "IP " + ip + " 信息:\n" + response.body();
            }
            return "无法查询 IP: " + ip;
        } catch (Exception e) {
            return "IP 查询失败: " + e.getMessage();
        }
    }

    /**
     * 获取随机笑话
     *
     * @description 从免费 API 获取一个随机英文笑话
     * @return 随机笑话内容
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "random_joke", description = "获取一个随机笑话")
    public String randomJoke() {
        try {
            String url = "https://official-joke-api.appspot.com/random_joke";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            return "获取笑话失败";
        } catch (Exception e) {
            return "获取笑话失败: " + e.getMessage();
        }
    }

    /**
     * 文本翻译
     *
     * @description 通过 MyMemory 免费翻译 API 将文本从一种语言翻译为另一种语言
     * @param text 待翻译文本
     * @param from 源语言代码
     * @param to 目标语言代码
     * @return 翻译结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "translate", description = "将文本从一种语言翻译为另一种语言。语言代码如: zh(中文), en(英文), ja(日文), ko(韩文), fr(法文), de(德文)")
    public String translate(
            @ToolParam(name = "text", description = "待翻译的文本") String text,
            @ToolParam(name = "from", description = "源语言代码，如 'zh'、'en'") String from,
            @ToolParam(name = "to", description = "目标语言代码，如 'en'、'ja'") String to) {
        try {
            String encoded = java.net.URLEncoder.encode(text, "UTF-8");
            String url = "https://api.mymemory.translated.net/get?q=" + encoded + "&langpair=" + from + "|" + to;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                int start = body.indexOf("\"translatedText\":\"");
                if (start >= 0) {
                    start += 18;
                    int end = body.indexOf("\"", start);
                    if (end > start) {
                        return "翻译结果: " + body.substring(start, end);
                    }
                }
                return "翻译结果: " + body;
            }
            return "翻译失败";
        } catch (Exception e) {
            return "翻译失败: " + e.getMessage();
        }
    }

    /**
     * 生成短链接
     *
     * @description 通过 is.gd 免费 API 将长 URL 缩短为短链接
     * @param url 需要缩短的长 URL
     * @return 短链接地址
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "url_shorten", description = "将长URL缩短为短链接")
    public String urlShorten(
            @ToolParam(name = "url", description = "需要缩短的URL") String url) {
        try {
            String encoded = java.net.URLEncoder.encode(url, "UTF-8");
            String apiUrl = "https://is.gd/create.php?format=simple&url=" + encoded;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
                return "短链接: " + response.body().trim();
            }
            return "短链接生成失败";
        } catch (Exception e) {
            return "短链接生成失败: " + e.getMessage();
        }
    }

    /**
     * Unicode 字符查询
     *
     * @description 查询指定字符的 Unicode 信息，包括编码点、名称和类型
     * @param character 要查询的字符
     * @return Unicode 字符信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "unicode_lookup", description = "查询字符的Unicode编码信息")
    public String unicodeLookup(
            @ToolParam(name = "character", description = "要查询的字符，如 '中' 或 'A'") String character) {
        if (character == null || character.isEmpty()) {
            return "请输入一个字符";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < character.length(); i++) {
            char c = character.charAt(i);
            int codePoint = Character.codePointAt(character, i);
            sb.append(String.format("字符: '%c' | Unicode: U+%04X | 十进制: %d | 名称: %s | 类型: %s",
                    c, codePoint, codePoint,
                    Character.getName(codePoint),
                    Character.getType(c) == Character.OTHER_LETTER ? "字母(其他)" :
                    Character.isLetter(c) ? "字母" :
                    Character.isDigit(c) ? "数字" :
                    Character.isWhitespace(c) ? "空白" : "符号"));
            if (Character.isHighSurrogate(c)) i++;
            if (i < character.length() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private double evalExpression(String rawExpr) {
        final String expr = rawExpr.replaceAll("\\s", "");
        return new Object() {
            int pos = -1, ch;

            void next() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
            boolean eat(int c) {
                if (ch == c) { next(); return true; }
                return false;
            }

            double parse() { next(); double v = parseAddSub(); if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char)ch); return v; }

            double parseAddSub() {
                double v = parseMulDiv();
                for (;;) {
                    if (eat('+')) v += parseMulDiv();
                    else if (eat('-')) v -= parseMulDiv();
                    else return v;
                }
            }

            double parseMulDiv() {
                double v = parsePow();
                for (;;) {
                    if (eat('*')) v *= parsePow();
                    else if (eat('/')) v /= parsePow();
                    else if (eat('%')) v %= parsePow();
                    else return v;
                }
            }

            double parsePow() {
                double v = parseUnary();
                if (eat('^')) v = Math.pow(v, parsePow());
                return v;
            }

            double parseUnary() {
                if (eat('+')) return parseUnary();
                if (eat('-')) return -parseUnary();
                double v;
                if (eat('(')) { v = parseAddSub(); eat(')'); }
                else {
                    int start = pos;
                    while (ch >= '0' && ch <= '9' || ch == '.') next();
                    v = Double.parseDouble(expr.substring(start, pos));
                }
                return v;
            }
        }.parse();
    }
}
