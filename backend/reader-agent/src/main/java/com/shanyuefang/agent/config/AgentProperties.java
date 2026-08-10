package com.shanyuefang.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {
    private String encryptionKey;
    private String internalToken;
    /** Secret injected by the gateway on browser-facing Agent requests. */
    private String gatewayToken;
    /** Disabled until each configured OpenAI-compatible provider is verified for native function calls. */
    private boolean nativeToolCallingEnabled = true;
    private String platformProvider = "deepseek";
    private String platformModel = "deepseek-chat";
    private String platformFastModel;
    private String platformStrongModel;
    private String platformApiKey;
    private String platformBaseUrl = "https://api.deepseek.com";
    /** Comma-separated HTTPS hosts allowed for user-managed OpenAI-compatible endpoints. */
    private String byokAllowedHosts = "";
    private int starterCredits = 3;
    private int maxInputChars = 4000;
    private int maxOutputTokens = 1200;
    /** Maximum estimated input tokens assembled from history, LightRAG, tools, and citations. */
    private int maxContextTokens = 3600;
    /** Bump this when replacing the embedding implementation or provider to re-project unchanged content. */
    private String embeddingModelVersion = "hash-embedding-v1";
    /** `hash` keeps local development self-contained; `openai-compatible` enables a real semantic provider. */
    private String embeddingProvider = "hash";
    private String embeddingBaseUrl;
    private String embeddingPath = "/v1/embeddings";
    private String embeddingApiKey;
    private String embeddingModel = "text-embedding-3-small";
    /** Provider dimension must match the versioned Milvus collection. */
    private int embeddingDimensions = 256;
    private int embeddingOperationTimeoutMillis = 3000;
    private int embeddingFailureCooldownSeconds = 30;
    private boolean milvusEnabled;
    /** Optional lexical evidence projection used only for chapter-bounded LightRAG evidence recall. */
    private boolean elasticsearchEnabled;
    private String elasticsearchIndex = "reader_agent_chunks";
    private String milvusHost = "localhost";
    private int milvusPort = 19530;
    /** Bound optional Milvus calls so a dead vector service never blocks a reader answer. */
    private int milvusOperationTimeoutMillis = 1500;
    /** Cooldown after a timeout/connection failure before probing Milvus again. */
    private int milvusFailureCooldownSeconds = 30;
    private String milvusCollection = "reader_agent_chunks";
    private String bookProfileCollection = "book_profile_vectors";
    private String characterProfileCollection = "character_vectors";
    private String eventProfileCollection = "event_vectors";
    private String communityProfileCollection = "lightrag_community_vectors";
    private String userPreferenceProfileCollection = "user_preference_vectors";
    private boolean neo4jEnabled;
    private String neo4jUri = "bolt://localhost:7687";
    private String neo4jUsername = "neo4j";
    private String neo4jPassword;
    /** Bound optional Neo4j calls so a network partition cannot block a reader answer. */
    private int neo4jOperationTimeoutMillis = 1500;
    /** Cooldown after a Neo4j timeout/connection failure before probing it again. */
    private int neo4jFailureCooldownSeconds = 30;
    private boolean graphLlmEnabled;
    private int graphLlmMaxChars = 8000;
    /** Below this confidence, graph claims remain stored for review but never reach a reader-facing context. */
    private double minGraphConfidence = 0.60D;
    private int maxRequestsPerMinute = 20;
    /** Independent coarse limit for a gateway-observed client address. */
    private int maxRequestsPerIpPerMinute = 60;
    /** Only one expensive generation may run for a persisted conversation at a time. */
    private int maxConcurrentRequestsPerSession = 1;
    /** Optional evidence reranker after LightRAG-aware multi-recall. */
    private boolean rerankerEnabled;
    private String rerankerBaseUrl;
    private String rerankerPath = "/v1/rerank";
    private String rerankerApiKey;
    private String rerankerModel = "rerank-v3.5";
    /** Bound optional provider calls so a slow Reranker cannot delay the answer indefinitely. */
    private int rerankerOperationTimeoutMillis = 3000;
    /** Cooldown after a provider timeout/error before trying the same endpoint again. */
    private int rerankerFailureCooldownSeconds = 30;
    private String adminUserIds = "";
    private int globalDailyPlatformRequests = 300;
    private int globalDailyPlatformTokens = 200000;
    private int platformCircuitFailureThreshold = 5;
    private int platformCircuitOpenSeconds = 60;
    /** Set to zero to retain conversations indefinitely; positive values are cleaned nightly. */
    private int conversationRetentionDays = 90;
    /** Platform-owned model price in micros of CNY per 1,000 tokens; BYOK is always recorded as zero platform cost. */
    private long platformInputCostMicrosPerThousand;
    private long platformOutputCostMicrosPerThousand;
}
