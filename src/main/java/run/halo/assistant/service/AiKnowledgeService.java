package run.halo.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import run.halo.assistant.dto.AiSource;

/**
 * 简单的知识库服务：将知识条目存储在插件数据目录下的 JSON 文件中，并提供基于关键词的检索。
 *
 * <p>存储路径默认：~/.halo/plugins/ai-assistant/knowledge.json，可通过环境变量 AI_ASSISTANT_KB_PATH 覆盖。</p>
 */
@Slf4j
@Service
public class AiKnowledgeService {

    @Data
    public static class KnowledgeItem {
        private String id;
        private String title;
        private String content;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile List<KnowledgeItem> items = new ArrayList<>();
    private Path knowledgePath;

    @jakarta.annotation.PostConstruct
    public void init() {
        resolvePath();
        load();
    }

    private void resolvePath() {
        String env = System.getenv("AI_ASSISTANT_KB_PATH");
        if (env != null && !env.isBlank()) {
            knowledgePath = Paths.get(env.trim());
        } else {
            knowledgePath = Paths.get(System.getProperty("user.home", "."), ".halo", "plugins", "ai-assistant", "knowledge.json");
        }
    }

    private synchronized void load() {
        if (knowledgePath == null || !Files.isRegularFile(knowledgePath)) {
            items = new ArrayList<>();
            return;
        }
        try {
            String json = Files.readString(knowledgePath, StandardCharsets.UTF_8);
            KnowledgeItem[] arr = objectMapper.readValue(json, KnowledgeItem[].class);
            List<KnowledgeItem> list = new ArrayList<>();
            if (arr != null) {
                for (KnowledgeItem it : arr) {
                    if (it != null && it.getId() != null) {
                        list.add(it);
                    }
                }
            }
            items = list;
        } catch (Exception e) {
            log.warn("加载知识库失败，将使用空列表: {}", knowledgePath, e);
            items = new ArrayList<>();
        }
    }

    private synchronized void save() {
        if (knowledgePath == null) {
            return;
        }
        try {
            Path parent = knowledgePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(items);
            Files.writeString(knowledgePath, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("保存知识库失败: {}", knowledgePath, e);
            throw new RuntimeException("知识库保存失败: " + e.getMessage());
        }
    }

    public List<KnowledgeItem> listAll() {
        return List.copyOf(items);
    }

    public synchronized KnowledgeItem create(String title, String content) {
        KnowledgeItem item = new KnowledgeItem();
        item.setId("kb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        item.setTitle(title != null ? title : "");
        item.setContent(content != null ? content : "");
        items.add(item);
        save();
        return item;
    }

    public synchronized KnowledgeItem update(String id, String title, String content) {
        for (int i = 0; i < items.size(); i++) {
            KnowledgeItem it = items.get(i);
            if (it != null && id.equals(it.getId())) {
                it.setTitle(title != null ? title : "");
                it.setContent(content != null ? content : "");
                save();
                return it;
            }
        }
        throw new IllegalArgumentException("知识条目不存在: " + id);
    }

    public synchronized void delete(String id) {
        if (id == null || id.isBlank()) return;
        items.removeIf(it -> id.equals(it.getId()));
        save();
    }

    /**
     * 基于关键词的简单检索，返回前 topK 条，并转换为 AiSource。
     */
    public List<AiSource> search(String question, int topK) {
        if (question == null || question.isBlank() || items.isEmpty()) {
            return List.of();
        }
        String q = question.toLowerCase(Locale.ROOT);
        String[] tokens = q.split("\\s+");
        return items.stream()
            .map(it -> new Object() {
                final KnowledgeItem item = it;
                final int score = score(it.getTitle(), it.getContent(), tokens);
            })
            .filter(x -> x.score > 0)
            .sorted(Comparator.comparingInt((Object o) -> ((int) getField(o, "score"))).reversed())
            .limit(topK)
            .map(o -> (KnowledgeItem) getField(o, "item"))
            .map(this::toSource)
            .collect(Collectors.toList());
    }

    private int score(String title, String content, String[] tokens) {
        String base = ((title != null ? title : "") + "\n" + (content != null ? content : ""))
            .toLowerCase(Locale.ROOT);
        int s = 0;
        for (String t : tokens) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String token = t.trim();
            if (token.length() < 2) {
                continue;
            }
            int idx = base.indexOf(token);
            while (idx >= 0) {
                s++;
                idx = base.indexOf(token, idx + token.length());
            }
        }
        return s;
    }

    private Object getField(Object obj, String fieldName) {
        try {
            return obj.getClass().getDeclaredField(fieldName).get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private AiSource toSource(KnowledgeItem it) {
        AiSource s = new AiSource();
        s.setId(it.getId());
        s.setTitle(it.getTitle());
        String content = it.getContent() != null ? it.getContent() : "";
        s.setSnippet(content.length() > 220 ? content.substring(0, 220) + "..." : content);
        return s;
    }
}

