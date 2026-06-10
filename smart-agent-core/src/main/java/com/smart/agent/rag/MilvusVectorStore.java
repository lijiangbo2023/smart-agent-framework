package com.smart.agent.rag;

import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Milvus向量数据库存储服务
 *
 * @description 封装Milvus向量数据库的连接管理、数据插入和相似性搜索操作，为RAG检索提供向量存储能力
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class MilvusVectorStore {

    @Value("${rag.milvus.uri:http://localhost:19530}")
    private String milvusUri;

    @Value("${rag.milvus.token:}")
    private String milvusToken;

    @Value("${rag.milvus.collection:knowledge_base}")
    private String collectionName;

    @Value("${rag.milvus.dimension:1024}")
    private int dimension;

    @Value("${rag.milvus.top-k:5}")
    private int topK;

    private MilvusClientV2 client;

    /**
     * 初始化Milvus客户端连接
     *
     * @description 在Bean初始化后自动连接Milvus服务，连接失败时禁用RAG功能
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostConstruct
    public void init() {
        try {
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(milvusUri);
            if (milvusToken != null && !milvusToken.isEmpty()) {
                builder.token(milvusToken);
            }
            client = new MilvusClientV2(builder.build());
            log.info("MilvusVectorStore initialized, uri={}, collection={}", milvusUri, collectionName);
        } catch (Exception e) {
            log.warn("MilvusVectorStore init failed: {}. RAG disabled.", e.getMessage());
        }
    }

    /**
     * 关闭Milvus客户端连接
     *
     * @description 在Bean销毁前释放Milvus客户端资源
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 插入向量数据
     *
     * @description 将文本及其对应的向量数据插入Milvus集合中，支持附加元数据
     * @param id 文档唯一标识
     * @param text 原始文本内容
     * @param vector 文本对应的嵌入向量
     * @param metadata 附加元数据键值对，可为null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void insert(String id, String text, List<Float> vector, Map<String, Object> metadata) {
        if (client == null) return;
        try {
            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            data.addProperty("text", text);
            data.add("vector", com.google.gson.JsonParser.parseString(vector.toString()));
            if (metadata != null) {
                metadata.forEach((k, v) -> data.addProperty(k, String.valueOf(v)));
            }
            InsertResp resp = client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(data))
                    .build());
            log.debug("Milvus insert ok, id={}, insertCnt={}", id, resp.getInsertCnt());
        } catch (Exception e) {
            log.error("Milvus insert failed, id={}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * 向量相似性搜索
     *
     * @description 使用默认topK值在Milvus集合中执行向量相似性搜索
     * @param queryVector 查询向量
     * @return 按相似度排序的搜索结果列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<SearchResult> search(List<Float> queryVector) {
        return search(queryVector, topK, null);
    }

    /**
     * 向量相似性搜索（带参数）
     *
     * @description 在Milvus集合中执行向量相似性搜索，支持自定义topK和过滤条件
     * @param queryVector 查询向量
     * @param topK 返回结果的最大数量
     * @param filter Milvus过滤表达式，可为null
     * @return 按相似度排序的搜索结果列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<SearchResult> search(List<Float> queryVector, int topK, String filter) {
        if (client == null) return List.of();
        try {
            SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(topK)
                    .outputFields(List.of("text", "id"));
            if (filter != null && !filter.isEmpty()) {
                builder.filter(filter);
            }
            List<List<SearchResp.SearchResult>> results = client.search(builder.build()).getSearchResults();
            if (results.isEmpty()) return List.of();

            List<SearchResult> searchResults = new ArrayList<>();
            for (SearchResp.SearchResult r : results.get(0)) {
                Map<String, Object> entity = r.getEntity();
                String text = entity != null ? String.valueOf(entity.get("text")) : "";
                String docId = entity != null ? String.valueOf(entity.get("id")) : "";
                searchResults.add(new SearchResult(docId, text, r.getScore()));
            }
            return searchResults;
        } catch (Exception e) {
            log.error("Milvus search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 搜索结果记录
     *
     * @description 封装Milvus向量搜索返回的单条结果，包含文档ID、文本内容和相似度得分
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public record SearchResult(String id, String text, float score) {
    }
}
