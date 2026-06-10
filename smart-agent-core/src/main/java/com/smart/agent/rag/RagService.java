package com.smart.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG检索增强生成服务
 *
 * @description 整合向量嵌入服务和Milvus向量存储，提供文本索引和语义检索功能，实现检索增强生成（RAG）能力
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
     * 语义检索
     *
     * @description 根据查询文本进行语义检索，使用默认topK值返回相关文档内容
     * @param query 查询文本
     * @return 检索到的相关文档内容，多条结果以分隔符连接，无结果时返回空字符串
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String retrieve(String query) {
        return retrieve(query, 5);
    }

    /**
     * 语义检索（指定数量）
     *
     * @description 根据查询文本进行语义检索，将查询文本向量化后在Milvus中搜索最相似的文档
     * @param query 查询文本
     * @param topK 返回结果的最大数量
     * @return 检索到的相关文档内容，多条结果以分隔符连接，无结果时返回空字符串
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
     * 文本索引
     *
     * @description 将文本内容向量化并存入Milvus向量数据库，建立可检索的知识索引
     * @param id 文档唯一标识
     * @param text 待索引的文本内容
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
