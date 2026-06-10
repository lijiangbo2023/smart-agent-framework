package com.smart.agent.rag;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
 * 向量嵌入服务
 *
 * @description 基于DashScope API实现文本向量嵌入，支持单条和批量文本的向量化处理
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
     * 单条文本向量嵌入
     *
     * @description 将单条文本转换为向量表示，内部委托给批量嵌入方法处理
     * @param text 待嵌入的文本内容
     * @return 文本对应的浮点数向量，嵌入失败时返回空列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<Float> embed(String text) {
        List<List<Float>> result = embedBatch(List.of(text));
        return result.isEmpty() ? List.of() : result.get(0);
    }

    /**
     * 批量文本向量嵌入
     *
     * @description 通过DashScope Embedding API将多条文本批量转换为向量表示
     * @param texts 待嵌入的文本列表
     * @return 每条文本对应的浮点数向量列表，调用失败时返回空列表
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
            JSONArray dataArray = responseBody.getJSONArray("data");

            List<List<Float>> embeddings = new ArrayList<>();
            for (int i = 0; i < dataArray.size(); i++) {
                JSONArray embedding = dataArray.getJSONObject(i).getJSONArray("embedding");
                List<Float> vector = new ArrayList<>();
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
