package run.halo.assistant.dto;

import java.util.List;
import lombok.Data;

/**
 * AI 助手问答请求。
 */
@Data
public class AiChatRequest {

    /**
     * 当前问题（必填）。
     */
    private String question;

    /**
     * 历史对话（可选，多轮对话时由前端回传）。
     */
    private List<AiMessage> history;

    /**
     * 检索相似文档数量（可选，默认 5）。
     */
    private Integer topK;
}

