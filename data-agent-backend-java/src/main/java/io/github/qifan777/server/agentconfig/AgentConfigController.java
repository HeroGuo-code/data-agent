package io.github.qifan777.server.agentconfig;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 智能助手配置接口：开场白 / 提示词设计 / 预设问题。 */
@RestController
@RequestMapping("/agent-config")
public class AgentConfigController {
    private final AgentConfigService service;

    public AgentConfigController(AgentConfigService service) {
        this.service = service;
    }

    @GetMapping
    public AgentConfig get() {
        return service.getConfig();
    }

    @PutMapping
    public AgentConfig save(@RequestBody AgentConfig cfg) {
        return service.saveConfig(cfg);
    }
}
