package com.smart.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service.
 *
 * @description Integrates the vector embedding service and Milvus vector store to provide
 *              text indexing and semantic retrieval capabilities for retrieval-augmented
 *              generation (RAG).
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;

    public RagService(EmbeddingService embeddingService, MilvusVectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Semantic retrieval.
     *
     * @description Performs semantic retrieval based on the query text,
     *              returning relevant document content using the default topK value.
     * @param query query text
     * @return relevant document content with multiple results joined by a delimiter;
     *         empty string if no results
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String retrieve(String query) {
        return retrieve(query, 5);
    }

    /**
     * Semantic retrieval (with specified count).
     *
     * @description Performs semantic retrieval based on the query text, vectorizing the query
     *              and searching Milvus for the most similar documents.
     * @param query query text
     * @param topK maximum number of results to return
     * @return relevant document content with multiple results joined by a delimiter;
     *         empty string if no results
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String retrieve(String query, int topK) {
        List<Float> queryVector = embeddingService.embed(query);
        if (queryVector.isEmpty()) {
            log.warn("Embedding returned empty vector for query: {}", query);
            return "";
        }

        List<MilvusVectorStore.SearchResult> results = vectorStore.search(queryVector, topK, null);
        if (results.isEmpty()) {
            return "";
        }

        return results.stream()
                .map(r -> r.text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Text indexing.
     *
     * @description Vectorizes the text content and stores it in the Milvus vector database
     *              to build a searchable knowledge index.
     * @param id document unique identifier
     * @param text text content to index
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void index(String id, String text) {
        List<Float> vector = embeddingService.embed(text);
        if (vector.isEmpty()) {
            log.warn("Embedding returned empty vector, skip indexing id={}", id);
            return;
        }
        vectorStore.insert(id, text, vector, null);
    }
}
