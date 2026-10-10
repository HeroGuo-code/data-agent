package io.github.qifan777.server.model;

/** Persistent profile for one AI model (llm | embedding). */
public class ModelProfile {
    private String kind;      // llm | embedding
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer dimensions; // embedding only
    private boolean enabled;

    public ModelProfile() {}

    public ModelProfile(String kind, String name, String provider, String baseUrl,
                        String apiKey, String modelName, Integer dimensions, boolean enabled) {
        this.kind = kind; this.name = name; this.provider = provider; this.baseUrl = baseUrl;
        this.apiKey = apiKey; this.modelName = modelName; this.dimensions = dimensions; this.enabled = enabled;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
