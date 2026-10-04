package com.myproject.app.messenger.chat;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import com.myproject.app.messenger.chat.ChatDtos.ConversationView;
import com.myproject.app.messenger.chat.ChatDtos.CreateGroupRequest;
import com.myproject.app.messenger.chat.ChatDtos.MemberView;
import com.myproject.app.messenger.chat.ChatDtos.MessageView;
import com.myproject.app.messenger.chat.ChatDtos.ReadState;
import com.myproject.app.messenger.error.ApiException;
import com.myproject.app.messenger.realtime.ChatEvent;
import com.myproject.app.messenger.realtime.ChatEvents;
import com.myproject.app.messenger.user.AuthUser;
import com.myproject.app.messenger.user.User;
import com.myproject.app.messenger.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatEvents events;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
            MessageRepository messages, UserRepository users, ChatEvents events, Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    public record Opened(ConversationView conversation, boolean created) {
    }

    /** Returns the one direct conversation between the caller and {@code username}, creating it if needed. */
    @Transactional
    public Opened openDirect(AuthUser me, String username) {
        User other = users.findByUsernameIgnoreCase(username.trim()).orElseThrow(() -> ApiException.notFound("User"));
        if (other.getId().equals(me.id())) {
            throw ApiException.unprocessable("SELF_CONVERSATION", "You cannot start a conversation with yourself");
        }
        String key = directKey(me.id(), other.getId());
        Instant now = clock.instant();
        boolean created = conversations.insertDirectIfAbsent(UUID.randomUUID(), key, now) == 1;
        Conversation conversation = conversations.findByDirectKey(key).orElseThrow();
        members.insertMemberIfAbsent(conversation.getId(), me.id(), now);
        members.insertMemberIfAbsent(conversation.getId(), other.getId(), now);
        if (created) {
            events.publish(List.of(me.username(), other.getUsername()),
                    new ChatEvent("conversation.created", conversation.getId(), null));
        }
        return new Opened(view(me.id(), conversation), created);
    }

    @Transactional
    public ConversationView createGroup(AuthUser me, CreateGroupRequest request) {
        Set<String> wanted = new LinkedHashSet<>();
        request.members().forEach(name -> wanted.add(name.trim().toLowerCase(Locale.ROOT)));
        wanted.remove(me.username().toLowerCase(Locale.ROOT));
        if (wanted.isEmpty()) {
            throw ApiException.unprocessable("NO_MEMBERS", "Add at least one other person to the group");
        }
        List<User> found = users.findAllByLowerUsernameIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new LinkedHashSet<>(wanted);
            found.forEach(u -> missing.remove(u.getUsername().toLowerCase(Locale.ROOT)));
            throw ApiException.notFound("User", Map.of("unknownUsernames", missing));
        }
        Instant now = clock.instant();
        Conversation conversation = conversations.save(Conversation.group(request.title().trim(), now));
        members.save(new ConversationMember(conversation, users.getReferenceById(me.id()), MemberRole.OWNER, 0, now));
        found.forEach(u -> members.save(new ConversationMember(conversation, u, MemberRole.MEMBER, 0, now)));
        members.flush();
        events.publish(members.findUsernames(conversation.getId()),
                new ChatEvent("conversation.created", conversation.getId(), null));
        return view(me.id(), conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationView> list(UUID userId) {
        List<ConversationMember> mine = members.findMembershipsOf(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = mine.stream().map(m -> m.getConversation().getId()).toList();
        Map<UUID, List<ConversationMember>> membersById = members.findAllWithUsers(ids).stream()
                .collect(groupingBy(m -> m.getConversation().getId()));
        Map<UUID, Message> latest = messages.findLatest(ids).stream()
                .collect(toMap(m -> m.getConversation().getId(), Function.identity()));
        return mine.stream()
                .map(m -> toView(userId, m.getConversation(), membersById.get(m.getConversation().getId()),
                        latest.get(m.getConversation().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationView get(UUID userId, UUID conversationId) {
        return view(userId, requireMembership(userId, conversationId).getConversation());
    }

    @Transactional
    public ConversationView rename(AuthUser me, UUID conversationId, String title) {
        ConversationMember membership = requireGroupOwner(me.id(), conversationId);
        Conversation conversation = membership.getConversation();
        conversation.setTitle(title.trim());
        events.publish(members.findUsernames(conversationId), new ChatEvent("conversation.updated", conversationId, null));
        return view(me.id(), conversation);
    }

    @Transactional
    public ConversationView addMember(AuthUser me, UUID conversationId, String username) {
        Conversation conversation = requireGroupOwner(me.id(), conversationId).getConversation();
        User user = users.findByUsernameIgnoreCase(username.trim()).orElseThrow(() -> ApiException.notFound("User"));
        if (members.existsByConversationIdAndUserId(conversationId, user.getId())) {
            throw ApiException.conflict("ALREADY_MEMBER", user.getUsername() + " is already in this conversation");
        }
        if (members.countByConversationId(conversationId) >= ChatDtos.MAX_GROUP_SIZE) {
            throw ApiException.unprocessable("GROUP_FULL", "Groups are limited to " + ChatDtos.MAX_GROUP_SIZE + " members");
        }
        // New members can read the history but start with nothing unread.
        members.saveAndFlush(new ConversationMember(conversation, user, MemberRole.MEMBER, conversation.getLastSeq(),
                clock.instant()));
        events.publish(members.findUsernames(conversationId), new ChatEvent("conversation.updated", conversationId, null));
        return view(me.id(), conversation);
    }

    @Transactional
    public void leave(AuthUser me, UUID conversationId) {
        ConversationMember membership = requireMembership(me.id(), conversationId);
        Conversation conversation = membership.getConversation();
        if (conversation.getType() != ConversationType.GROUP) {
            throw ApiException.unprocessable("NOT_A_GROUP", "You can only leave group conversations");
        }
        members.delete(membership);
        members.flush();
        List<ConversationMember> remaining = members.findAllWithUsers(List.of(conversationId));
        if (remaining.isEmpty()) {
            conversations.delete(conversation);
            return;
        }
        if (remaining.stream().noneMatch(m -> m.getRole() == MemberRole.OWNER)) {
            remaining.stream().min(Comparator.comparing(ConversationMember::getJoinedAt)
                            .thenComparing(m -> m.getUser().getUsername()))
                    .ifPresent(m -> m.setRole(MemberRole.OWNER));
        }
        events.publish(remaining.stream().map(m -> m.getUser().getUsername()).toList(),
                new ChatEvent("member.left", conversationId, Map.of("username", me.username())));
    }

    @Transactional
    public ReadState markRead(AuthUser me, UUID conversationId, long seq) {
        ConversationMember membership = requireMembership(me.id(), conversationId);
        long lastSeq = membership.getConversation().getLastSeq();
        if (membership.markReadUpTo(Math.min(seq, lastSeq))) {
            List<String> others = members.findUsernames(conversationId).stream()
                    .filter(u -> !u.equals(me.username())).toList();
            events.publish(others, new ChatEvent("read", conversationId,
                    Map.of("username", me.username(), "seq", membership.getLastReadSeq())));
        }
        return new ReadState(conversationId, membership.getLastReadSeq(), lastSeq - membership.getLastReadSeq());
    }

    /** Membership of the caller; a conversation they are not in is reported as not found, not forbidden. */
    @Transactional(readOnly = true)
    public ConversationMember requireMembership(UUID userId, UUID conversationId) {
        return members.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
    }

    private ConversationMember requireGroupOwner(UUID userId, UUID conversationId) {
        ConversationMember membership = requireMembership(userId, conversationId);
        if (membership.getConversation().getType() != ConversationType.GROUP) {
            throw ApiException.unprocessable("NOT_A_GROUP", "Direct conversations cannot be changed");
        }
        if (membership.getRole() != MemberRole.OWNER) {
            throw ApiException.forbidden("Only the group owner can do that");
        }
        return membership;
    }

    private ConversationView view(UUID userId, Conversation conversation) {
        UUID id = conversation.getId();
        return toView(userId, conversation, members.findAllWithUsers(List.of(id)),
                messages.findLatest(List.of(id)).stream().findFirst().orElse(null));
    }

    private static ConversationView toView(UUID userId, Conversation c, List<ConversationMember> all, Message latest) {
        List<ConversationMember> memberList = all == null ? List.of() : all;
        long lastRead = memberList.stream().filter(m -> m.getUser().getId().equals(userId)).findFirst()
                .map(ConversationMember::getLastReadSeq).orElse(c.getLastSeq());
        String title = c.getType() == ConversationType.GROUP ? c.getTitle()
                : memberList.stream().map(ConversationMember::getUser).filter(u -> !u.getId().equals(userId))
                        .map(User::getDisplayName).findFirst().orElse("Direct message");
        List<MemberView> views = memberList.stream()
                .map(m -> new MemberView(m.getUser().getId(), m.getUser().getUsername(), m.getUser().getDisplayName(),
                        m.getRole(), m.getLastReadSeq()))
                .toList();
        return new ConversationView(c.getId(), c.getType(), title, views, c.getLastSeq(),
                Math.max(0, c.getLastSeq() - lastRead), latest == null ? null : MessageView.of(latest),
                c.getLastMessageAt(), c.getCreatedAt());
    }

    static String directKey(UUID a, UUID b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) < 0 ? x + ":" + y : y + ":" + x;
    }
}
