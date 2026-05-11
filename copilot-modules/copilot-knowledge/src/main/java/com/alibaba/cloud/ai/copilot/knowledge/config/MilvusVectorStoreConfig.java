package com.alibaba.cloud.ai.copilot.knowledge.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Milvus VectorStore 配置
 * Milvus 连接失败时自动降级为 NoOpVectorStore，应用正常启动。
 *
 * @author RobustH
 */
@Slf4j
@Configuration
public class MilvusVectorStoreConfig {

    @Value("${spring.ai.vectorstore.milvus.client.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port:19530}")
    private Integer port;

    @Value("${spring.ai.vectorstore.milvus.database-name:default}")
    private String databaseName;

    @Value("${spring.ai.vectorstore.milvus.collection-name:copilot_knowledge}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension:1024}")
    private Integer embeddingDimension;

    @Value("${spring.ai.vectorstore.milvus.initialize-schema:true}")
    private Boolean initializeSchema;

    /**
     * 创建 Milvus VectorStore。
     * 若 Milvus 不可用（连接超时/拒绝），返回 NoOpVectorStore 避免启动失败。
     */
    @Bean
    public VectorStore vectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabaseName(databaseName)
                    .build();
            MilvusServiceClient milvusClient = new MilvusServiceClient(connectParam);
            log.info("Milvus 客户端已初始化: 主机={}, 端口={}, 数据库={}", host, port, databaseName);

            MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusClient, embeddingModel)
                    .collectionName(collectionName)
                    .databaseName(databaseName)
                    .metricType(MetricType.COSINE)
                    .embeddingDimension(embeddingDimension)
                    .initializeSchema(initializeSchema)
                    .build();

            log.info("Milvus 向量存储已初始化: 集合={}, 维度={}", collectionName, embeddingDimension);
            return vectorStore;

        } catch (Throwable e) {
            log.warn("Milvus 不可用，知识库功能已禁用（应用继续正常启动）: {}", e.getMessage());
            return new NoOpVectorStore();
        }
    }

    /**
     * Milvus 不可用时的空操作 VectorStore，所有操作静默忽略。
     */
    public static class NoOpVectorStore implements VectorStore {
        @Override
        public void add(List<Document> documents) {
            log.warn("NoOpVectorStore: Milvus 不可用，忽略 add 操作");
        }

        @Override
        public void delete(List<String> idList) {
            // Milvus 不可用，忽略
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // Milvus 不可用，忽略
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }
}
