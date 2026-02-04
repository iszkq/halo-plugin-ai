package run.halo.assistant.controller;

import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import run.halo.app.plugin.ApiVersion;
import run.halo.assistant.dto.AiChatRequest;
import run.halo.assistant.dto.AiChatResponse;
import run.halo.assistant.service.AiAssistantService;
import run.halo.assistant.service.AiKnowledgeService;

/**
 * AI 助手 API：包含知识库增删改查与 RAG 问答接口。
 *
 * <p>最终访问路径示例：</p>
 * <pre>
 *   /apis/api.plugin.halo.run/v1alpha1/plugins/ai-assistant/assistant/...
 * </pre>
 */
@ApiVersion("v1alpha1")
@RestController
@RequestMapping("assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiKnowledgeService knowledgeService;

    // === 知识库 CRUD ===

    @Data
    public static class KnowledgeItemDto {
        private String id;
        private String title;
        private String content;
    }

    @GetMapping("/knowledge")
    public List<KnowledgeItemDto> listKnowledge() {
        return knowledgeService.listAll().stream().map(it -> {
            KnowledgeItemDto dto = new KnowledgeItemDto();
            dto.setId(it.getId());
            dto.setTitle(it.getTitle());
            dto.setContent(it.getContent());
            return dto;
        }).toList();
    }

    @PostMapping("/knowledge")
    public ResponseEntity<KnowledgeItemDto> createKnowledge(@RequestBody KnowledgeItemDto dto) {
        var it = knowledgeService.create(dto.getTitle(), dto.getContent());
        KnowledgeItemDto out = new KnowledgeItemDto();
        out.setId(it.getId());
        out.setTitle(it.getTitle());
        out.setContent(it.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @PutMapping("/knowledge/{id}")
    public ResponseEntity<KnowledgeItemDto> updateKnowledge(@PathVariable String id, @RequestBody KnowledgeItemDto dto) {
        try {
            var it = knowledgeService.update(id, dto.getTitle(), dto.getContent());
            KnowledgeItemDto out = new KnowledgeItemDto();
            out.setId(it.getId());
            out.setTitle(it.getTitle());
            out.setContent(it.getContent());
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/knowledge/{id}")
    public ResponseEntity<Void> deleteKnowledge(@PathVariable String id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // === RAG 问答 ===

    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest request) {
        return aiAssistantService.chat(request);
    }
}

