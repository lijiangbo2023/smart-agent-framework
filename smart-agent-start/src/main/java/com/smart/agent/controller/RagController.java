package com.smart.agent.controller;

import com.smart.agent.model.ServiceResponse;
import com.smart.agent.rag.MilvusVectorStore;
import com.smart.agent.rag.RagService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG knowledge base management API.
 *
 * @description Provides endpoints to index documents, search the knowledge base,
 *              and seed sample data for testing semantic retrieval.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final MilvusVectorStore vectorStore;

    public RagController(RagService ragService, MilvusVectorStore vectorStore) {
        this.ragService = ragService;
        this.vectorStore = vectorStore;
    }

    /**
     * Index a document into the knowledge base.
     */
    @PostMapping("/index")
    public ServiceResponse<Map<String, Object>> index(@RequestBody IndexRequest request) {
        String id = request.getId() != null ? request.getId() : UUID.randomUUID().toString();
        ragService.index(id, request.getText());
        log.info("Document indexed: id={}, text length={}", id, request.getText().length());
        return ServiceResponse.success(Map.of(
                "id", id,
                "status", "indexed",
                "length", request.getText().length()
        ));
    }

    /**
     * Search the knowledge base.
     */
    @GetMapping("/search")
    public ServiceResponse<Map<String, Object>> search(@RequestParam String query,
                                                        @RequestParam(defaultValue = "5") int topK) {
        long start = System.currentTimeMillis();
        String result = ragService.retrieve(query, topK);
        long elapsed = System.currentTimeMillis() - start;
        boolean hasResults = result != null && !result.isEmpty();
        return ServiceResponse.success(Map.of(
                "query", query,
                "results", hasResults ? result : "No results found",
                "hasResults", hasResults,
                "elapsedMs", elapsed
        ));
    }

    /**
     * Batch index preset knowledge for testing.
     */
    @PostMapping("/seed")
    public ServiceResponse<Map<String, Object>> seed() {
        String[][] docs = {
                {"smart-agent-intro", "Smart Agent Framework是一个基于AgentScope的AI智能体框架，采用Supervisor/SubAgent模式编排多个AI Agent。支持RAG检索增强生成、MCP协议、钉钉机器人集成、会话管理等功能。"},
                {"rag-explained", "RAG（检索增强生成）是一种结合信息检索和文本生成的技术。它先从知识库中检索相关文档，然后将文档作为上下文提供给大语言模型，从而提高回答的准确性和可靠性。"},
                {"milvus-intro", "Milvus是一个开源的向量数据库，专为AI应用设计。它支持十亿级别的向量相似度搜索，提供毫秒级查询延迟。Milvus使用etcd进行元数据管理，MinIO作为对象存储。"},
                {"supervisor-pattern", "Supervisor模式是一种多Agent编排架构。Supervisor Agent接收用户请求，分析意图后将其路由到最合适的Sub-Agent。Sub-Agent完成任务后将结果返回给Supervisor汇总。"},
                {"dingtalk-bot", "钉钉机器人支持Stream模式长连接。通过配置appKey、appSecret和robotCode，可以实现钉钉群聊中的@机器人智能对话，支持AI互动卡片等高级功能。"}
        };
        for (String[] doc : docs) {
            ragService.index(doc[0], doc[1]);
        }
        log.info("Seeded {} documents into knowledge base", docs.length);
        return ServiceResponse.success(Map.of("seeded", docs.length, "status", "done"));
    }

    @Data
    public static class IndexRequest {
        private String id;
        private String text;
    }
}
