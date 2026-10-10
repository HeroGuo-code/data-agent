package io.github.qifan777.server.model;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for the model (LLM + Embedding) management page. */
@RestController
@RequestMapping("/model")
public class ModelController {

    private final ModelConfigService service;
    private final ModelRuntime runtime;

    public ModelController(ModelConfigService service, ModelRuntime runtime) {
        this.service = service;
        this.runtime = runtime;
    }

    @GetMapping
    public Map<String, Object> get() {
        return service.snapshot();
    }

    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, ModelProfile> body) {
        ModelProfile llm = body.get("llm");
        ModelProfile emb = body.get("embedding");
        if (llm != null) { llm.setKind("llm"); service.save(llm); }
        if (emb != null) { emb.setKind("embedding"); service.save(emb); }
        runtime.reload();
        return service.snapshot();
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        runtime.reload();
        return service.snapshot();
    }
}
