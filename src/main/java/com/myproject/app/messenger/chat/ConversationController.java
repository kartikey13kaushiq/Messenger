package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.chat.ChatDtos.AddMemberRequest;
import com.myproject.app.messenger.chat.ChatDtos.ConversationView;
import com.myproject.app.messenger.chat.ChatDtos.CreateDirectRequest;
import com.myproject.app.messenger.chat.ChatDtos.CreateGroupRequest;
import com.myproject.app.messenger.chat.ChatDtos.ReadRequest;
import com.myproject.app.messenger.chat.ChatDtos.ReadState;
import com.myproject.app.messenger.chat.ChatDtos.RenameRequest;
import com.myproject.app.messenger.user.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations")
class ConversationController {

    private final ConversationService service;

    ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Your conversations, most recently active first, with unread counts")
    List<ConversationView> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @PostMapping("/direct")
    @Operation(summary = "Open the direct conversation with a user (201 when created, 200 when it already existed)")
    ResponseEntity<ConversationView> direct(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody CreateDirectRequest request) {
        var opened = service.openDirect(me, request.username());
        return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK).body(opened.conversation());
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    ConversationView group(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateGroupRequest request) {
        return service.createGroup(me, request);
    }

    @GetMapping("/{id}")
    ConversationView get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me.id(), id);
    }

    @PatchMapping("/{id}")
    ConversationView rename(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody RenameRequest request) {
        return service.rename(me, id, request.title());
    }

    @PostMapping("/{id}/members")
    ConversationView addMember(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request) {
        return service.addMember(me, id, request.username());
    }

    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.leave(me, id);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Move your read marker forward to a message sequence number")
    ReadState read(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ReadRequest request) {
        return service.markRead(me, id, request.seq());
    }
}
