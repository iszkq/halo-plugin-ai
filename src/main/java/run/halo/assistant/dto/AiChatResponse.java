package run.halo.assistant.dto;

import java.util.List;
import lombok.Data;

/**
 * AI 助手问答响应。
 */
@Data
public class AiChatResponse {

    /**
     * 大模型生成的回答。
     */
    private String answer;

    /**
     * 用于回答的参考文档片段。
     */
    private List<AiSource> sources;
}

