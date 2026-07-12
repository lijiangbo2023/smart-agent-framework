package com.smart.agent.demo;

import com.smart.agent.rag.RagService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Demo toolset with RAG support.
 *
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 2.0
 */
public class DemoTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final RagService ragService;

    public DemoTools(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Get current time
     *
     * @description Returns the current system date and time in the format yyyy-MM-dd HH:mm:ss (day of week)
     * @return formatted current date-time string
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "get_current_time", description = "Get the current date and time")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)"));
    }

    /**
     * Math expression calculator
     *
     * @description Parses and evaluates math expressions, supporting addition, subtraction, multiplication,
     *              division, parentheses, exponentiation and modulo. Input is sanitized for safe characters.
     * @param expression math expression string, e.g. '(3+5)*2' or '2^10'
     * @return calculation result in the format "expression = result", or error message on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "calculator", description = "Evaluate math expressions, supporting addition, subtraction, multiplication, division, parentheses, exponentiation, etc. Input is a math expression as a string")
    public String calculator(
            @ToolParam(name = "expression", description = "Math expression, e.g. '(3+5)*2' or '2^10'") String expression) {
        try {
            String sanitized = expression.replaceAll("[^0-9+\\-*/().^%\\s]", "");
            double result = evalExpression(sanitized);
            if (result == (long) result) {
                return expression + " = " + (long) result;
            }
            return expression + " = " + result;
        } catch (Exception e) {
            return "Calculation failed: " + e.getMessage();
        }
    }

    /**
     * Query city weather
     *
     * @description Queries real-time weather information for the specified city via the wttr.in API,
     *              including weather condition, temperature, humidity and wind speed
     * @param city city name, e.g. 'Beijing', 'Shanghai'
     * @return weather information string, or error message on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "get_weather", description = "Query real-time weather information for a specified city")
    public String getWeather(
            @ToolParam(name = "city", description = "City name, e.g. 'Beijing', 'Shanghai'") String city) {
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
                return city + " weather: " + response.body().trim();
            }
            return "Unable to get weather for " + city;
        } catch (Exception e) {
            return "Weather query failed: " + e.getMessage();
        }
    }

    /**
     * Internet search
     *
     * @description Searches the internet via DuckDuckGo Lite and returns a summary result,
     *              with a maximum length limit of 1500 characters
     * @param query search keywords
     * @return search result summary string, or error message on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "web_search_summary", description = "Search the internet and return a summary result")
    public String webSearch(
            @ToolParam(name = "query", description = "Search keywords") String query) {
        try {
            String url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
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
            return "Search result summary:\n" + body;
        } catch (Exception e) {
            return "Search failed: " + e.getMessage();
        }
    }

    /**
     * IP geolocation lookup
     *
     * @description Queries the geolocation information of an IP address via the ipinfo.io free API
     * @param ip IP address
     * @return IP geolocation information
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "ip_lookup", description = "Query the geolocation and ownership information of an IP address")
    public String ipLookup(
            @ToolParam(name = "ip", description = "IP address, e.g. '8.8.8.8'") String ip) {
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
                return "IP " + ip + " info:\n" + response.body();
            }
            return "Unable to look up IP: " + ip;
        } catch (Exception e) {
            return "IP lookup failed: " + e.getMessage();
        }
    }

    /**
     * Get a random joke
     *
     * @description Fetches a random English joke from a free API
     * @return random joke content
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "random_joke", description = "Get a random joke")
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
            return "Failed to fetch joke";
        } catch (Exception e) {
            return "Failed to fetch joke: " + e.getMessage();
        }
    }

    /**
     * Text translation
     *
     * @description Translates text from one language to another via the MyMemory free translation API
     * @param text text to translate
     * @param from source language code
     * @param to target language code
     * @return translation result
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "translate", description = "Translate text from one language to another. Language codes: zh(Chinese), en(English), ja(Japanese), ko(Korean), fr(French), de(German), etc.")
    public String translate(
            @ToolParam(name = "text", description = "Text to translate") String text,
            @ToolParam(name = "from", description = "Source language code, e.g. 'zh', 'en'") String from,
            @ToolParam(name = "to", description = "Target language code, e.g. 'en', 'ja'") String to) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
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
                        return "Translation result: " + body.substring(start, end);
                    }
                }
                return "Translation result: " + body;
            }
            return "Translation failed";
        } catch (Exception e) {
            return "Translation failed: " + e.getMessage();
        }
    }

    /**
     * Generate short URL
     *
     * @description Shortens a long URL via the is.gd free API
     * @param url long URL to shorten
     * @return shortened URL
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Tool(name = "url_shorten", description = "Shorten a long URL")
    public String urlShorten(
            @ToolParam(name = "url", description = "URL to shorten") String url) {
        try {
            String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8);
            String apiUrl = "https://is.gd/create.php?format=simple&url=" + encoded;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", "smart-agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
                return "Short URL: " + response.body().trim();
            }
            return "Short URL generation failed";
        } catch (Exception e) {
            return "Short URL generation failed: " + e.getMessage();
        }
    }

    /**
     * Unicode character lookup
     *
     * @description Queries Unicode information for the specified character, including code point, name and type
     * @param character character to look up
     * @return Unicode character information
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    /**
     * RAG knowledge base search
     *
     * @description Search the knowledge base using RAG (Retrieval-Augmented Generation) for semantically relevant documents
     * @param query search query for the knowledge base
     * @return relevant document content from the knowledge base
     */
    @Tool(name = "knowledge_search", description = "Search the knowledge base for relevant documents using semantic retrieval. Use this when you need to find information from the organization's knowledge base.")
    public String knowledgeSearch(
            @ToolParam(name = "query", description = "Search query for semantic retrieval") String query) {
        try {
            String result = ragService.retrieve(query, 5);
            if (result == null || result.isEmpty()) {
                return "Knowledge base returned no results for: " + query;
            }
            return "Knowledge base results:\n" + result;
        } catch (Exception e) {
            return "Knowledge search failed: " + e.getMessage();
        }
    }

    @Tool(name = "unicode_lookup", description = "Query Unicode encoding information for a character")
    public String unicodeLookup(
            @ToolParam(name = "character", description = "Character to look up, e.g. 'A' or any Unicode character") String character) {
        if (character == null || character.isEmpty()) {
            return "Please enter a character";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < character.length(); i++) {
            char c = character.charAt(i);
            int codePoint = Character.codePointAt(character, i);
            sb.append(String.format("Character: '%c' | Unicode: U+%04X | Decimal: %d | Name: %s | Type: %s",
                    c, codePoint, codePoint,
                    Character.getName(codePoint),
                    Character.getType(c) == Character.OTHER_LETTER ? "Letter(Other)" :
                    Character.isLetter(c) ? "Letter" :
                    Character.isDigit(c) ? "Digit" :
                    Character.isWhitespace(c) ? "Whitespace" : "Symbol"));
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
