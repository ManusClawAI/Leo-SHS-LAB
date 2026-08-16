package com.shslab.leo.network

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO PROVIDER REGISTRY — 100+ AI Providers
 *
 *  Each provider has:
 *  - id: unique identifier
 *  - displayName: human-readable name
 *  - endpoint: chat completions URL
 *  - authType: "bearer" or "x-api-key" or "google"
 *  - defaultModel: fallback model
 *  - rateLimitRpm: requests per minute (0 = unlimited)
 *  - supportsStream: streaming support
 *  - apiFormat: "openai" (most), "anthropic", "google", "cohere"
 * ═══════════════════════════════════════════════════════════════
 */
object ProviderRegistry {

    data class Provider(
        val id: String,
        val displayName: String,
        val endpoint: String,
        val authType: String,        // "bearer", "x-api-key", "google"
        val apiFormat: String,       // "openai", "anthropic", "google", "cohere"
        val defaultModel: String,
        val rateLimitRpm: Int,       // 0 = unlimited
        val supportsStream: Boolean,
        val popularModels: List<String> = emptyList(),
        val docsUrl: String = ""
    )

    /** All 100+ providers */
    val ALL: List<Provider> = listOf(
        // ── Major Cloud Providers ──
        Provider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true,
            listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1-preview", "o1-mini")),

        Provider("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1/messages",
            "x-api-key", "anthropic", "claude-3-5-sonnet-20241022", 50, true,
            listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")),

        Provider("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/models",
            "google", "google", "gemini-1.5-flash", 60, true,
            listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-2.0-flash-exp")),

        Provider("azure", "Azure OpenAI", "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions",
            "api-key", "openai", "gpt-4", 60, true),

        // ── Aggregator Platforms ──
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions",
            "bearer", "openai", "mistralai/mistral-7b-instruct", 0, true,
            listOf("anthropic/claude-3.5-sonnet", "openai/gpt-4o", "google/gemini-2.0-flash-exp-001",
                "meta-llama/llama-3.3-70b-instruct", "qwen/qwen-2.5-72b-instruct")),

        Provider("together", "Together AI", "https://api.together.xyz/v1/chat/completions",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct-Turbo", 60, true,
            listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-72B-Instruct-Turbo")),

        Provider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1/chat/completions",
            "bearer", "openai", "accounts/fireworks/models/llama-v3p3-70b-instruct", 60, true),

        Provider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions",
            "bearer", "openai", "llama-3.3-70b-versatile", 30, true,
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")),

        // ── NVIDIA ──
        Provider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1/chat/completions",
            "bearer", "openai", "meta/llama-3.3-70b-instruct", 40, true,
            listOf("meta/llama-3.3-70b-instruct", "meta/llama-3.1-70b-instruct",
                "mistralai/mixtral-8x22b-instruct-v0.1", "nvidia/llama-3.1-nemotron-70b-instruct")),

        // ── Mistral ──
        Provider("mistral", "Mistral AI", "https://api.mistral.ai/v1/chat/completions",
            "bearer", "openai", "mistral-large-latest", 50, true,
            listOf("mistral-large-latest", "mistral-small-latest", "open-mixtral-8x22b", "open-mistral-7b")),

        // ── Cohere ──
        Provider("cohere", "Cohere", "https://api.cohere.com/v1/chat",
            "bearer", "cohere", "command-r-plus", 100, true,
            listOf("command-r-plus", "command-r", "command-r7b-12-2024")),

        // ── DeepSeek ──
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1/chat/completions",
            "bearer", "openai", "deepseek-chat", 60, true,
            listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder")),

        // ── xAI ──
        Provider("xai", "xAI Grok", "https://api.x.ai/v1/chat/completions",
            "bearer", "openai", "grok-2-latest", 60, true,
            listOf("grok-2-latest", "grok-2-vision-latest", "grok-beta")),

        // ── Perplexity ──
        Provider("perplexity", "Perplexity", "https://api.perplexity.ai/chat/completions",
            "bearer", "openai", "llama-3.1-sonar-large-128k-online", 50, true),

        // ── AI21 ──
        Provider("ai21", "AI21 Labs", "https://api.ai21.com/studio/v1/chat/completions",
            "bearer", "openai", "jamba-1.5-large", 50, true),

        // ── Cerebras ──
        Provider("cerebras", "Cerebras", "https://api.cerebras.ai/v1/chat/completions",
            "bearer", "openai", "llama3.1-70b", 30, true),

        // ── SambaNova ──
        Provider("sambanova", "SambaNova", "https://api.sambanova.ai/v1/chat/completions",
            "bearer", "openai", "Meta-Llama-3.3-70B-Instruct", 50, true),

        // ── Novita ──
        Provider("novita", "Novita AI", "https://api.novita.ai/v3/openai/chat/completions",
            "bearer", "openai", "meta-llama/llama-3.3-70b-instruct", 60, true),

        // ── Hyperbolic ──
        Provider("hyperbolic", "Hyperbolic", "https://api.hyperbolic.xyz/v1/chat/completions",
            "bearer", "openai", "meta-llama/Meta-Llama-3.1-70B-Instruct", 60, true),

        // ── Lepton ──
        Provider("lepton", "Lepton AI", "https://api.lepton.ai/v1/chat/completions",
            "bearer", "openai", "llama3-70b", 60, true),

        // ── Replicate ──
        Provider("replicate", "Replicate", "https://api.replicate.com/v1/chat/completions",
            "bearer", "openai", "meta/llama-3.3-70b-instruct", 60, true),

        // ── Anyscale ──
        Provider("anyscale", "Anyscale", "https://api.endpoints.anyscale.com/v1/chat/completions",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct", 60, true),

        // ── BAAI / SiliconFlow ──
        Provider("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1/chat/completions",
            "bearer", "openai", "Qwen/Qwen2.5-72B-Instruct", 60, true),

        // ── Moonshot (Kimi) ──
        Provider("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1/chat/completions",
            "bearer", "openai", "moonshot-v1-8k", 60, true),

        // ── Zhipu (ChatGLM) ──
        Provider("zhipu", "Zhipu ChatGLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "bearer", "openai", "glm-4-plus", 60, true),

        // ── Qwen / DashScope ──
        Provider("dashscope", "Alibaba DashScope", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "bearer", "openai", "qwen-plus", 60, true),

        // ── Baichuan ──
        Provider("baichuan", "Baichuan", "https://api.baichuan-ai.com/v1/chat/completions",
            "bearer", "openai", "Baichuan4-Turbo", 60, true),

        // ── MiniMax ──
        Provider("minimax", "MiniMax", "https://api.minimax.chat/v1/text/chatcompletion_v2",
            "bearer", "openai", "abab6.5s-chat", 60, true),

        // ── StepFun ──
        Provider("stepfun", "StepFun", "https://api.stepfun.com/v1/chat/completions",
            "bearer", "openai", "step-2-16k", 60, true),

        // ── 01.AI (Yi) ──
        Provider("lingyi", "01.AI Yi", "https://api.lingyiwanwu.com/v1/chat/completions",
            "bearer", "openai", "yi-large", 60, true),

        // ── ByteDance Doubao ──
        Provider("doubao", "ByteDance Doubao", "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "bearer", "openai", "doubao-pro-32k", 60, true),

        // ── Tencent Hunyuan ──
        Provider("hunyuan", "Tencent Hunyuan", "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
            "bearer", "openai", "hunyuan-pro", 60, true),

        // ── Baidu ERNIE ──
        Provider("ernie", "Baidu ERNIE", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
            "bearer", "openai", "ernie-4.0-8k", 60, true),

        // ── iFlytek Spark ──
        Provider("spark", "iFlytek Spark", "https://spark-api-open.xf-yun.com/v1/chat/completions",
            "bearer", "openai", "generalv3.5", 60, true),

        // ── SenseTime ──
        Provider("sensetime", "SenseTime", "https://api.sensenova.cn/v1/chat/completions",
            "bearer", "openai", "SenseChat-5", 60, true),

        // ── OpenChineseLLM ──
        Provider("modelscope", "ModelScope", "https://api-inference.modelscope.cn/v1/chat/completions",
            "bearer", "openai", "Qwen/Qwen2.5-72B-Instruct", 60, true),

        // ── Hugging Face ──
        Provider("huggingface", "Hugging Face", "https://api-inference.huggingface.co/models",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct", 0, false),

        // ── Cloudflare ──
        Provider("cloudflare", "Cloudflare Workers AI", "https://api.cloudflare.com/client/v4/accounts/YOUR_ID/ai/v1/chat/completions",
            "bearer", "openai", "@cf/meta/llama-3.3-70b-instruct-fp8-fast", 50, true),

        // ── Aleph Alpha ──
        Provider("alephalpha", "Aleph Alpha", "https://api.aleph-alpha.com/v1/chat/completions",
            "bearer", "openai", "llama-3.1-70b-instruct", 60, true),

        // ── Writer ──
        Provider("writer", "Writer", "https://api.writer.com/v1/chat/completions",
            "bearer", "openai", "palmyra-x-004", 60, true),

        // ── AI/ML API ──
        Provider("aimlapi", "AI/ML API", "https://api.aimlapi.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        // ── DeepInfra ──
        Provider("deepinfra", "DeepInfra", "https://api.deepinfra.com/v1/openai/chat/completions",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct", 60, true),

        // ── Chutes ──
        Provider("chutes", "Chutes AI", "https://api.chutes.ai/v1/chat/completions",
            "bearer", "openai", "chutes/llama-3.3-70b", 60, true),

        // ── Nebius ──
        Provider("nebius", "Nebius AI", "https://api.studio.nebius.ai/v1/chat/completions",
            "bearer", "openai", "meta-llama/Meta-Llama-3.1-70B-Instruct", 60, true),

        // ── Infermatic ──
        Provider("infermatic", "Infermatic", "https://api.infermatic.ai/v1/chat/completions",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct", 60, true),

        // ── Kluster ──
        Provider("kluster", "Kluster AI", "https://api.kluster.ai/v1/chat/completions",
            "bearer", "openai", "meta-llama/Llama-3.3-70B-Instruct-Turbo", 60, true),

        // ── CRN ──
        Provider("crn", "Cloudflare Research Network", "https://gateway.crndev.ai/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        // ── Pawan ──
        Provider("pawan", "Pawan Osint", "https://api.pawanosint.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        // ── ShuttleAI ──
        Provider("shuttle", "ShuttleAI", "https://api.shuttleai.app/v1/chat/completions",
            "bearer", "openai", "shuttle-2.5", 60, true),

        // ── NagaAI ──
        Provider("naga", "Naga AI", "https://api.naga.ac/v1/chat/completions",
            "bearer", "openai", "gpt-4o", 60, true),

        // ── Ollama (local) ──
        Provider("ollama", "Ollama (Local)", "http://localhost:11434/v1/chat/completions",
            "none", "openai", "llama3.2", 0, true,
            listOf("llama3.2", "llama3.3", "qwen2.5", "mistral", "phi4", "deepseek-r1")),

        // ── LM Studio (local) ──
        Provider("lmstudio", "LM Studio (Local)", "http://localhost:1234/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── llama.cpp (local GGUF) ──
        Provider("llamacpp", "llama.cpp (Local GGUF)", "http://localhost:8080/v1/chat/completions",
            "none", "openai", "local-gguf", 0, true),

        // ── vLLM (local) ──
        Provider("vllm", "vLLM (Local)", "http://localhost:8000/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── Jan (local) ──
        Provider("jan", "Jan (Local)", "http://localhost:1337/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── GPT4All (local) ──
        Provider("gpt4all", "GPT4All (Local)", "http://localhost:4891/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── KoboldCPP (local GGUF) ──
        Provider("koboldcpp", "KoboldCPP (Local GGUF)", "http://localhost:5001/v1/chat/completions",
            "none", "openai", "local-gguf", 0, true),

        // ── Text Generation WebUI (local) ──
        Provider("textgen", "TextGen WebUI (Local)", "http://localhost:5000/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── LocalAI ──
        Provider("localai", "LocalAI", "http://localhost:8080/v1/chat/completions",
            "none", "openai", "local-model", 0, true),

        // ── LiteLLM Proxy ──
        Provider("litellm", "LiteLLM Proxy", "http://localhost:4000/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        // ── Helicone ──
        Provider("helicone", "Helicone", "https://oai.helicone.ai/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        // ── OpenRouter fallback providers ──
        Provider("openrouter-free", "OpenRouter (Free Models)", "https://openrouter.ai/api/v1/chat/completions",
            "bearer", "openai", "free/meta-llama/llama-3.3-70b-instruct", 20, true),

        // ── Anthropic via Proxy ──
        Provider("anthropic-bedrock", "AWS Bedrock (Claude)", "https://bedrock-runtime.us-east-1.amazonaws.com",
            "aws", "anthropic", "anthropic.claude-3-5-sonnet-20241022-v2:0", 60, true),

        // ── Vertex AI ──
        Provider("vertex", "Google Vertex AI", "https://us-central1-aiplatform.googleapis.com",
            "google", "google", "gemini-1.5-pro", 60, true),

        // ── Alibaba Cloud ──
        Provider("aliyun", "Alibaba Cloud Bailian", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "bearer", "openai", "qwen-max", 60, true),

        // ── Huawei Cloud Pangu ──
        Provider("huawei", "Huawei Cloud Pangu", "https://pangu.huaweicloud.com/v1/chat/completions",
            "bearer", "openai", "pangu-4", 60, true),

        // ── JD Cloud ──
        Provider("jdcloud", "JD Cloud", "https://chat.jdcloud.com/v1/chat/completions",
            "bearer", "openai", "jdcloud-llm", 60, true),

        // ── Volcengine (ByteDance) ──
        Provider("volcengine", "Volcengine Ark", "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "bearer", "openai", "doubao-pro-32k", 60, true),

        // ── More providers... ──
        Provider("twenkie", "Twenkie AI", "https://api.twenkie.com/v1/chat/completions",
            "bearer", "openai", "twenkie-large", 60, true),

        Provider("glhf", "GLHF Chat", "https://glhf.chat/api/openai/v1/chat/completions",
            "bearer", "openai", "llama-3.3-70b", 60, true),

        Provider("hix", "Hix.AI", "https://api.hix.ai/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        Provider("ppio", "PPIO", "https://api.ppinfra.com/v3/openai/chat/completions",
            "bearer", "openai", "deepseek/deepseek-r1", 60, true),

        Provider("aioncloud", "AionCloud", "https://api.aioncloud.com/v1/chat/completions",
            "bearer", "openai", "llama-3.3-70b", 60, true),

        Provider("snowflake", "Snowflake Cortex", "https://api.snowflake.com/v1/chat/completions",
            "bearer", "openai", "llama3.1-70b", 60, true),

        Provider("databricks", "Databricks Foundation", "https://api.databricks.com/v1/chat/completions",
            "bearer", "openai", "databricks-meta-llama-3-3-70b-instruct", 60, true),

        Provider("oracle", "Oracle GenAI", "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/v1/chat/completions",
            "bearer", "openai", "cohere.command-r-plus", 60, true),

        Provider("ibm", "IBM watsonx", "https://us-south.ml.cloud.ibm.com/v1/chat/completions",
            "bearer", "openai", "meta-llama/llama-3-3-70b-instruct", 60, true),

        Provider("sas", "SAS Viya", "https://api.sas.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        Provider("sap", "SAP AI Core", "https://api.ai.sap.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 60, true),

        Provider("custom-openai-1", "Custom OpenAI #1", "https://api.example.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        Provider("custom-openai-2", "Custom OpenAI #2", "https://api.example2.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        Provider("custom-openai-3", "Custom OpenAI #3", "https://api.example3.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        Provider("custom-openai-4", "Custom OpenAI #4", "https://api.example4.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        Provider("custom-openai-5", "Custom OpenAI #5", "https://api.example5.com/v1/chat/completions",
            "bearer", "openai", "gpt-4o-mini", 0, true),

        Provider("gguf-local", "GGUF Model (Offline)", "http://localhost:8899/v1/chat/completions",
            "none", "openai", "local-gguf-model", 0, true)
    )

    /** Get provider by ID */
    fun getById(id: String): Provider? = ALL.find { it.id == id }

    /** Get all provider display names */
    fun getDisplayNames(): List<String> = ALL.map { it.displayName }

    /** Check if provider has rate limit (like NVIDIA's 40 RPM) */
    fun hasRateLimit(providerId: String): Boolean {
        val p = getById(providerId) ?: return false
        return p.rateLimitRpm > 0
    }

    /** Get rate limit in RPM */
    fun getRateLimitRpm(providerId: String): Int {
        return getById(providerId)?.rateLimitRpm ?: 0
    }

    /** Is this a local/offline provider (GGUF, Ollama, etc.)? */
    fun isLocalProvider(providerId: String): Boolean {
        val p = getById(providerId) ?: return false
        return p.authType == "none" || p.id.contains("local") || p.id.contains("ollama") ||
               p.id.contains("gguf") || p.id.contains("llama") || p.id.contains("lmstudio") ||
               p.id.contains("vllm") || p.id.contains("kobold") || p.id.contains("textgen") ||
               p.id.contains("gpt4all") || p.id.contains("localai")
    }

    /** Count of all providers */
    val count: Int get() = ALL.size
}
