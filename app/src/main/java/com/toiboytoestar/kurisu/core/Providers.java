package com.toiboytoestar.kurisu.core;

/** Preset AI back-ends. Anything OpenAI-compatible works, including local servers. */
public final class Providers {
    private Providers() {}

    public static final class P {
        public final String id;
        public final String label;
        public final boolean anthropic;
        public final String baseUrl;
        public final String model;
        public final boolean keyRequired;
        public final String hint;

        P(String id, String label, boolean anthropic, String baseUrl, String model, boolean keyRequired, String hint) {
            this.id = id;
            this.label = label;
            this.anthropic = anthropic;
            this.baseUrl = baseUrl;
            this.model = model;
            this.keyRequired = keyRequired;
            this.hint = hint;
        }
    }

    public static final P[] ALL = {
            new P("gemini", "Google Gemini (FREE tier)", false, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", true,
                    "FREE, no credit card: get a key at aistudio.google.com/apikey. Recommended to start."),
            new P("groq", "Groq (FREE tier)", false, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true,
                    "FREE, no credit card: key from console.groq.com/keys. Very fast."),
            new P("openrouter", "OpenRouter (free models)", false, "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", true,
                    "Key from openrouter.ai/keys. Models ending in \":free\" cost nothing; if this one is gone, pick another \":free\" model."),
            new P("ollama", "Local server (Ollama etc.)", false, "http://192.168.1.10:11434/v1", "llama3.2", false,
                    "FREE and private. No key needed. Use your computer's LAN address; both devices must be on the same network."),
            new P("anthropic", "Anthropic (Claude) - paid", true, "https://api.anthropic.com", "claude-haiku-5-5", true,
                    "Needs credits on console.anthropic.com. Haiku is the fastest, which feels best for live conversation."),
            new P("openai", "OpenAI - paid", false, "https://api.openai.com/v1", "gpt-4o-mini", true,
                    "Needs billing credit on platform.openai.com."),
            new P("custom", "Custom OpenAI-compatible", false, "", "", false,
                    "Any server that implements /chat/completions with streaming."),
    };

    public static P byId(String id) {
        for (P p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return 0;
    }

    public static boolean isConfigured(String providerId, String baseUrl, String apiKey, String model) {
        P p = byId(providerId);
        if (model == null || model.trim().length() == 0) return false;
        if (p.keyRequired) return apiKey != null && apiKey.trim().length() > 0;
        return baseUrl != null && baseUrl.trim().length() > 0;
    }

    public static LlmClient create(String providerId, String baseUrl, String apiKey, String model) {
        P p = byId(providerId);
        String url = baseUrl == null || baseUrl.trim().length() == 0 ? p.baseUrl : baseUrl.trim();
        String key = apiKey == null ? "" : apiKey.trim();
        String m = model.trim();
        if (p.anthropic) return new AnthropicClient(url, key, m, 1024);
        return new OpenAiClient(url, key, m);
    }
}
