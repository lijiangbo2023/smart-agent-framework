package com.smart.agent.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Vector embedding service.
 *
 * @description Implements text vector embedding based on the DashScope API,
 *              supporting both single and batch text vectorization.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${rag.embedding.model:text-embedding-v3}")
    private String embeddingModel;

    @Value("${rag.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Single text vector embedding.
     *
     * @description Converts a single text to its vector representation,
     *              internally delegating to the batch embedding method.
     * @param text the text content to embed
     * @return float vector for the text; empty list on embedding failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<Float> embed(String text) {
        List<List<Float>> result = embedBatch(List.of(text));
        return result.isEmpty() ? List.of() : result.get(0);
    }

    /**
     * Batch text vector embedding.
     *
     * @description Converts multiple texts to vector representations in batch
     *              via the DashScope Embedding API.
     * @param texts list of texts to embed
     * @return list of float vectors corresponding to each text; empty list on API failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", embeddingModel);
            body.put("input", texts);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject responseBody = JSON.parseObject(response.body());

            // Check for API-level errors before accessing data
            if (responseBody.containsKey("error")) {
                log.error("Embedding API error: {}", responseBody.get("error"));
                return List.of();
            }

            JSONArray dataArray = responseBody.getJSONArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                log.warn("Embedding API returned no data, response: {}", response.body());
                return List.of();
            }

            List<List<Float>> embeddings = new ArrayList<>();
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item == null) continue;
                JSONArray embedding = item.getJSONArray("embedding");
                if (embedding == null) continue;
                List<Float> vector = new ArrayList<>(embedding.size());
                for (int j = 0; j < embedding.size(); j++) {
                    vector.add(embedding.getFloat(j));
                }
                embeddings.add(vector);
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Embedding failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
