package com.advisorconnect.chat.adapter.in.web;

import com.advisorconnect.chat.adapter.in.web.dto.AdvisorConversationResponse;
import com.advisorconnect.chat.adapter.in.web.dto.ConversationResponse;
import com.advisorconnect.chat.adapter.in.web.dto.MarkReadResponse;
import com.advisorconnect.chat.adapter.in.web.dto.MessageResponse;
import com.advisorconnect.chat.application.ChatService;
import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.common.security.SecurityHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The REST half of chat: history backfill, inbox lists and mark-as-read. Live traffic goes over the
 * WebSocket ({@code /ws/chat}); this controller exists for everything that is a request rather than
 * a stream.
 *
 * <h2>Why the path variable is {@code counterpartyId}</h2>
 * A conversation has a fixed seeker side and a fixed advisor side — that is the Cassandra partition
 * key {@code (user_id, advisor_id)}, and it is not symmetric. But both parties call the <em>same</em>
 * URL to reach the same conversation, so the id in the path cannot be named for either role: to a
 * seeker it is an advisor id, to an advisor it is a seeker id.
 *
 * <p>It is therefore named for what it always is — the other party — and which side of the
 * partition key it lands on is decided by the caller's verified role via
 * {@link ConversationParticipants#resolve(UUID, String, UUID)}, exactly as
 * {@code ChatWebSocketHandler} does for the socket. Naming it {@code advisorId} would have been
 * accurate for the web client's current usage and a lie for every advisor request.
 *
 * <h2>Identity</h2>
 * Taken only from the gateway-injected {@code X-User-Id}/{@code X-User-Role} headers. A caller
 * cannot name themselves in the path or body, so there is no way to read a conversation you are not
 * a participant in: the partition read is built from your own verified id plus the counterparty you
 * asked for, and a conversation you were never part of is simply empty.
 *
 * <p>{@code SecurityConfig} has already rejected the request if those headers are missing or
 * malformed, so they are safe to require here.
 */
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    /** Matches the web client's default page size. */
    static final int DEFAULT_LIMIT = 50;

    /** Ceiling on one request, so a client cannot ask for an entire conversation history. */
    static final int MAX_LIMIT = 200;

    private final ChatService chatService;

    /**
     * The most recent messages of one conversation, newest first.
     *
     * <p>Used to backfill the history that predates the client's socket connection; live messages
     * arrive over the socket instead.
     */
    @GetMapping("/{counterpartyId}/messages")
    public List<MessageResponse> getMessages(
            @RequestHeader(SecurityHeaders.X_USER_ID) UUID callerId,
            @RequestHeader(SecurityHeaders.X_USER_ROLE) String callerRole,
            @PathVariable UUID counterpartyId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

        ConversationParticipants participants = participants(callerId, callerRole, counterpartyId);

        return chatService.getMessages(participants.userId(), participants.advisorId(), clamp(limit))
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    /**
     * The calling seeker's conversation list, most-recently-active first.
     *
     * <p>Seekers only: this reads {@code conversations_by_user}, whose partition key is a seeker id.
     * An advisor calling it would query that table with their advisor id and get a silent empty
     * list, which reads as "you have no conversations" rather than "wrong endpoint". Advisors have
     * {@link #getAdvisorInbox} instead.
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<ConversationResponse> getMyConversations(
            @RequestHeader(SecurityHeaders.X_USER_ID) UUID callerId) {

        return chatService.getUserConversations(callerId).stream()
                .map(ConversationResponse::from)
                .toList();
    }

    /**
     * The calling advisor's inbox, most-recently-active first — the mirror of
     * {@link #getMyConversations} over {@code conversations_by_advisor}.
     *
     * <p>A literal path segment can sit alongside {@code /{counterpartyId}/messages} without
     * ambiguity because that mapping is two segments deep and this one is a single segment.
     */
    @GetMapping("/advisor-inbox")
    @PreAuthorize("hasRole('ADVISOR')")
    public List<AdvisorConversationResponse> getAdvisorInbox(
            @RequestHeader(SecurityHeaders.X_USER_ID) UUID callerId) {

        return chatService.getAdvisorConversations(callerId).stream()
                .map(AdvisorConversationResponse::from)
                .toList();
    }

    /**
     * Marks a conversation read for the calling participant and clears their unread badge.
     *
     * <p>{@code PUT} rather than {@code POST} because it is idempotent — calling it twice leaves
     * exactly the same state, the second call simply reporting zero messages flipped.
     */
    @PutMapping("/{counterpartyId}/read")
    public MarkReadResponse markConversationRead(
            @RequestHeader(SecurityHeaders.X_USER_ID) UUID callerId,
            @RequestHeader(SecurityHeaders.X_USER_ROLE) String callerRole,
            @PathVariable UUID counterpartyId) {

        int marked = chatService.markConversationRead(participants(callerId, callerRole, counterpartyId));
        return new MarkReadResponse(marked);
    }

    /**
     * Places the authenticated caller on their side of the conversation.
     *
     * <p>{@code resolve} returns null for a role that is not a chat participant — {@code ADMIN}
     * being the real case. Such a caller is authenticated, so 401 would be wrong; they are simply
     * not a party to any conversation, which is 403.
     */
    private static ConversationParticipants participants(UUID callerId, String callerRole, UUID counterpartyId) {
        ConversationParticipants participants =
                ConversationParticipants.resolve(callerId, callerRole, counterpartyId);

        if (participants == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Role '" + callerRole + "' is not a chat participant");
        }
        return participants;
    }

    /**
     * Forces {@code limit} into {@code [1, MAX_LIMIT]}.
     *
     * <p>Clamped rather than rejected with a 400 on both ends. The floor is not cosmetic: Cassandra
     * rejects {@code LIMIT 0} outright ("LIMIT must be strictly positive"), so a client sending
     * {@code ?limit=0} would otherwise get a 500 out of the driver for what is at worst a
     * meaningless request. The ceiling stops one request pulling an unbounded partition into
     * memory.
     */
    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }
}
