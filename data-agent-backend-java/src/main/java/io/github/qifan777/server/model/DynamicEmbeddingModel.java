package io.github.qifan777.server.model;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Delegates EmbeddingModel calls to the current runtime embedding client so config changes apply live. */
public class DynamicEmbeddingModel implements EmbeddingModel {
    private final ModelRuntime runtime;
    public DynamicEmbeddingModel(ModelRuntime runtime) { this.runtime = runtime; }
    @Override public EmbeddingResponse call(EmbeddingRequest request) { return runtime.embed().call(request); }
    @Override public float[] embed(Document document) { return runtime.embed().embed(document); }
    @Override public float[] embed(String text) { return runtime.embed().embed(text); }
    @Override public List<float[]> embed(List<String> texts) { return runtime.embed().embed(texts); }
    @Override public int dimensions() { return runtime.embeddingDimensions(); }
}
