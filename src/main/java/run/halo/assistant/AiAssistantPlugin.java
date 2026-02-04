package run.halo.assistant;

import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * AI 助手插件：提供基于 RAG 的智能问答能力。
 */
public class AiAssistantPlugin extends BasePlugin {

    public AiAssistantPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        // 插件启动时的逻辑（目前无需特殊处理）
    }

    @Override
    public void stop() {
        // 插件停止时的清理逻辑（目前无需特殊处理）
    }
}

