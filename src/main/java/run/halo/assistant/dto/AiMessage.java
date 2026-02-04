package run.halo.assistant.dto;

import lombok.Data;

/**
 * AI 对话消息（兼容 OpenAI role 语义）。
 */
@Data
public class AiMessage {

    /**
     * 角色：system / user / assistant
     */
    private String role;

    /**
     * 消息内容。
     */
    private String content;
}

