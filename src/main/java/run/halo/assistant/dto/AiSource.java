package run.halo.assistant.dto;

import lombok.Data;

/**
 * AI 回答时引用的知识片段，用于在前端展示「参考来源」。
 */
@Data
public class AiSource {

    private String id;
    private String title;
    private String snippet;
}

