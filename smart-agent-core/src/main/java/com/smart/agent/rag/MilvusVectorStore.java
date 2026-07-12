package com.smart.agent.rag;

import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
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
 * Milvus vector database storage service.
 *
 * @description Encapsulates Milvus vector database connection management, data insertion,
 *              and similarity search operations, providing vector storage for RAG retrieval.
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
     * Initialize Milvus client connection.
     *
     * @description Automatically connects to the Milvus service after Bean initialization.
     *              Disables RAG functionality on connection failure.
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

            // Auto-create collection if it doesn't exist
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            if (!client.hasCollection(hasReq)) {
                CreateCollectionReq.CollectionSchema schema = client.createSchema();
                schema.addField(AddFieldReq.builder()
                        .fieldName("id").dataType(DataType.VarChar).maxLength(128).isPrimaryKey(true).build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("text").dataType(DataType.VarChar).maxLength(65535).build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("vector").dataType(DataType.FloatVector).dimension(dimension).build());
                CreateCollectionReq createReq = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(schema)
                        .build();
                client.createCollection(createReq);
                // Create index on vector field
                IndexParam indexParam = IndexParam.builder()
                        .fieldName("vector")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE)
                        .build();
                client.createIndex(io.milvus.v2.service.index.request.CreateIndexReq.builder()
                        .collectionName(collectionName)
                        .indexParams(java.util.Collections.singletonList(indexParam))
                        .build());
                // Load collection into memory
                client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                        .collectionName(collectionName).build());
                log.info("Milvus collection '{}' created, indexed and loaded, dimension={}", collectionName, dimension);
            } else {
                // Ensure existing collection is loaded
                client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                        .collectionName(collectionName).build());
            }
            log.info("MilvusVectorStore initialized, uri={}, collection={}", milvusUri, collectionName);
        } catch (Exception e) {
            log.warn("MilvusVectorStore init failed: {}. RAG disabled.", e.getMessage());
        }
    }

    /**
     * Close Milvus client connection.
     *
     * @description Releases Milvus client resources before Bean destruction.
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
     * Insert vector data.
     *
     * @description Inserts text and its corresponding vector data into the Milvus collection,
     *              with optional metadata.
     * @param id document unique identifier
     * @param text original text content
     * @param vector embedding vector for the text
     * @param metadata additional metadata key-value pairs; may be null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void insert(String id, String text, List<Float> vector, Map<String, Object> metadata) {
        if (client == null) return;
        try {
            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            data.addProperty("text", text);
            com.google.gson.JsonArray vectorArray = new com.google.gson.JsonArray();
            for (Float f : vector) {
                vectorArray.add(f);
            }
            data.add("vector", vectorArray);
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
     * Vector similarity search.
     *
     * @description Performs vector similarity search in the Milvus collection
     *              using the default topK value.
     * @param queryVector query vector
     * @return similarity-sorted list of search results
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<SearchResult> search(List<Float> queryVector) {
        return search(queryVector, topK, null);
    }

    /**
     * Vector similarity search (with parameters).
     *
     * @description Performs vector similarity search in the Milvus collection
     *              with custom topK and filter expression support.
     * @param queryVector query vector
     * @param topK maximum number of results to return
     * @param filter Milvus filter expression; may be null
     * @return similarity-sorted list of search results
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
     * Search result record.
     *
     * @description Encapsulates a single result from Milvus vector search,
     *              including document ID, text content, and similarity score.
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public record SearchResult(String id, String text, float score) {
    }
}
