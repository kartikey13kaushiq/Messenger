package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.chat.ChatDtos.EditMessageRequest;
import com.myproject.app.messenger.chat.ChatDtos.MessagePage;
import com.myproject.app.messenger.chat.ChatDtos.MessageView;
import com.myproject.app.messenger.chat.ChatDtos.SendMessageRequest;
import com.myproject.app.messenger.user.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@Tag(name = "Messages")
class MessageController {

    private final MessageService service;

    MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "A page of messages older than 'before' (newest page when omitted), oldest first")
    MessagePage page(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        return service.page(me.id(), conversationId, before, limit);
    }

    @PostMapping
    @Operation(summary = "Send a message (201; 200 with the original when clientMessageId was already used)")
    ResponseEntity<MessageView> send(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        var sent = service.send(me, conversationId, request);
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(sent.message());
    }

    @PatchMapping("/{messageId}")
    MessageView edit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
            @PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        return service.edit(me, conversationId, messageId, request.body());
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId, @PathVariable UUID messageId) {
        service.delete(me, conversationId, messageId);
    }
}
