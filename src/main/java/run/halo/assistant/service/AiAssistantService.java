package run.halo.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import run.halo.assistant.dto.AiChatRequest;
import run.halo.assistant.dto.AiChatResponse;
import run.halo.assistant.dto.AiMessage;
import run.halo.assistant.dto.AiSource;

/**
 * 通用 AI 助手服务：从知识库检索相关条目，并调用 OpenAI 兼容接口生成回答。
 *
 * <p>环境变量配置：</p>
 * <ul>
 *   <li>AI_ASSISTANT_BASE_URL：可选，默认 https://api.openai.com</li>
 *   <li>AI_ASSISTANT_API_KEY：必填，大模型 API Key</li>
 *   <li>AI_ASSISTANT_MODEL：可选，默认 gpt-4.1-mini</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AiKnowledgeService knowledgeService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AiChatResponse chat(AiChatRequest request) {
        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            AiChatResponse resp = new AiChatResponse();
            resp.setAnswer("问题不能为空。");
            resp.setSources(List.of());
            return resp;
        }
        int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 5;

        // 1. 检索知识库
        List<AiSource> sources = knowledgeService.search(question, topK);

        // 2. 调用大模型生成回答
        String answer = callChatModel(question, request.getHistory(), sources);

        AiChatResponse resp = new AiChatResponse();
        resp.setAnswer(answer);
        resp.setSources(sources);
        return resp;
    }

    private String callChatModel(String question, List<AiMessage> history, List<AiSource> sources) {
        String apiKey = System.getenv("AI_ASSISTANT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI_ASSISTANT_API_KEY 未配置，将只返回基于检索的提示信息。");
            return buildFallbackAnswer(sources);
        }
        String baseUrl = System.getenv().getOrDefault("AI_ASSISTANT_BASE_URL", "https://api.openai.com");
        String model = System.getenv().getOrDefault("AI_ASSISTANT_MODEL", "gpt-4.1-mini");

        try {
            List<Object> messages = new ArrayList<>();

            // system prompt
            messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", "你是一个网站 AI 助手，会参考提供的知识片段，用简体中文回答问题，并在需要时引用来源标题。"));

            if (history != null) {
                for (AiMessage m : history) {
                    if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                        continue;
                    }
                    String role = m.getRole();
                    if (!Objects.equals(role, "user") && !Objects.equals(role, "assistant") && !Objects.equals(role, "system")) {
                        role = "user";
                    }
                    messages.add(objectMapper.createObjectNode()
                        .put("role", role)
                        .put("content", m.getContent()));
                }
            }

            // 上下文知识 + 当前问题
            StringBuilder ctx = new StringBuilder();
            if (sources != null && !sources.isEmpty()) {
                ctx.append("以下是与问题相关的知识片段：\n");
                for (AiSource s : sources) {
                    ctx.append("- 标题：")
                        .append(s.getTitle() == null ? "" : s.getTitle())
                        .append("\n  摘要：")
                        .append(s.getSnippet() == null ? "" : s.getSnippet())
                        .append("\n");
                }
                ctx.append("\n请结合上述内容，用简体中文回答用户的问题，并在需要时提到对应的知识条目标题。");
            } else {
                ctx.append("没有检索到相关知识，请根据你已有的知识回答问题。");
            }
            ctx.append("\n\n用户问题：").append(question);

            messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", ctx.toString()));

            var body = objectMapper.createObjectNode();
            body.put("model", model);
            body.set("messages", objectMapper.valueToTree(messages));
            body.put("temperature", 0.2);

            byte[] jsonBytes = objectMapper.writeValueAsBytes(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("调用大模型接口失败，status={} body={}", response.statusCode(), response.body());
                return buildFallbackAnswer(sources);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return buildFallbackAnswer(sources);
            }
            JsonNode first = choices.get(0);
            JsonNode msg = first.get("message");
            if (msg == null || msg.get("content") == null) {
                return buildFallbackAnswer(sources);
            }
            String content = msg.get("content").asText("");
            if (content == null || content.isBlank()) {
                return buildFallbackAnswer(sources);
            }
            return content;
        } catch (Exception e) {
            log.error("调用大模型接口异常", e);
            return buildFallbackAnswer(sources);
        }
    }

    private String buildFallbackAnswer(List<AiSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "暂时无法连接到大模型服务，但已经为你检索了相关知识条目。请检查环境变量 AI_ASSISTANT_API_KEY / AI_ASSISTANT_BASE_URL / AI_ASSISTANT_MODEL 是否配置正确。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("暂时无法连接到大模型服务，但已经为你检索到以下相关知识条目，可供参考：\n");
        for (AiSource s : sources) {
            sb.append("- 标题：")
                .append(s.getTitle() == null ? "" : s.getTitle())
                .append("；摘要：")
                .append(s.getSnippet() == null ? "" : s.getSnippet())
                .append("\n");
        }
        sb.append("\n请根据这些内容自行理解和整理答案。");
        return sb.toString();
    }
}

