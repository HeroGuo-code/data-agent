package io.github.qifan777.server.agentconfig;

import java.util.ArrayList;
import java.util.List;

/** 智能助手配置：开场白、提示词设计(人设/系统提示词)、预设问题。 */
public class AgentConfig {
    private String openingMessage = "你好，我是数据智能助手小鑫。我能理解你的自然语言问题，自动生成SQL查询电商经营数据，并生成可视化分析报告。试试下面的问题，或直接输入你想了解的数据吧！";
    private String personaPrompt = "你是数据智能助手「小鑫」，负责帮助用户查询和分析电商经营数据。请用自然、友好、简洁的语气回复用户，不要编造数据。";
    private List<String> presets = new ArrayList<>(List.of(
        "本季度销售额最高的三个店铺",
        "统计6月各商品类别的销售件数与GMV",
        "近30天退款率最高的商品TOP5",
        "最近3个月月度GMV变化趋势",
        "消费金额最高的10位买家及偏好品类"));

    public String getOpeningMessage() { return openingMessage; }
    public void setOpeningMessage(String v) { this.openingMessage = v == null ? "" : v; }
    public String getPersonaPrompt() { return personaPrompt; }
    public void setPersonaPrompt(String v) { this.personaPrompt = v == null ? "" : v; }
    public List<String> getPresets() { return presets; }
    public void setPresets(List<String> v) { this.presets = v == null ? new ArrayList<>() : v; }
}
