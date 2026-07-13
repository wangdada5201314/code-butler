package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.annotation.QuotaCheck;
import com.agent.codebutler.dto.CodeChatRequest;
import com.agent.codebutler.dto.GeneralChatRequest;
import com.agent.codebutler.service.ChatService;
import com.agent.codebutler.service.GeneralChatService;
import com.agent.codebutler.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 智能问答 Controller（SSE 流式）
 */
@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "智能问答", description = "代码问答与通用聊天接口")
public class ChatController {

    private final ChatService chatService;
    private final GeneralChatService generalChatService;

    public ChatController(ChatService chatService,
                          GeneralChatService generalChatService) {
        this.chatService = chatService;
        this.generalChatService = generalChatService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "CHAT")
    @Operation(summary = "流式问答", description = "基于 SSE 的流式代码问答，实时返回 AI 分析结果")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody CodeChatRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = UserContext.getUserId(httpRequest);
        return chatService.streamChat(request, userId);
    }

    @PostMapping(value = "/chat/general/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "CHAT")
    @Operation(summary = "通用聊天", description = "不依赖代码仓库的自由 AI 对话，基于 SSE 流式返回")
    public Flux<ServerSentEvent<String>> generalChatStream(@Valid @RequestBody GeneralChatRequest request,
                                                            HttpServletRequest httpRequest) {
        Long userId = UserContext.getUserId(httpRequest);
        return generalChatService.streamChat(request, userId);
    }
}
