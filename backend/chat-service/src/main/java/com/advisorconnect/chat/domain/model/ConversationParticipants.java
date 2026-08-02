package com.advisorconnect.chat.domain.model;

import java.util.UUID;

/**
 * The two sides of a conversation, plus which side the authenticated caller occupies.
 *
 * <p>Cassandra's {@code messages} table is partitioned by {@code (user_id, advisor_id)} in that
 * fixed order — it is <em>not</em> a symmetric "participant A / participant B" key. Every read and
 * write therefore has to know which of the two ids is the seeker and which is the advisor, and
 * that cannot be derived from the ids themselves. It is derived from the caller's verified role:
 *
 * <ul>
 *   <li>{@link #ROLE_USER} → the caller is the {@code user_id}; the counterparty is the advisor.</li>
 *   <li>{@link #ROLE_ADVISOR} → the caller is the {@code advisor_id}; the counterparty is the user.</li>
 * </ul>
 *
 * <p>This rule is security-relevant (it decides whose partition is read) and is needed by both
 * entry points — the WebSocket handler and the REST controller — so it lives here once rather than
 * being re-derived in each adapter, where the two copies could drift.
 *
 * @param userId     the seeker's id — always the first component of the partition key
 * @param advisorId  the advisor's id — always the second component of the partition key
 * @param senderType {@code "user"} or {@code "advisor"}, as persisted on {@link Message}
 */
public record ConversationParticipants(UUID userId, UUID advisorId, String senderType) {

    /** Role claim identifying the seeker side. Matches {@code UserRole.USER.name()}. */
    public static final String ROLE_USER = "USER";

    /** Role claim identifying the advisor side. Matches {@code UserRole.ADVISOR.name()}. */
    public static final String ROLE_ADVISOR = "ADVISOR";

    public static final String SENDER_TYPE_USER = "user";
    public static final String SENDER_TYPE_ADVISOR = "advisor";

    /**
     * Places the caller on the correct side of the conversation.
     *
     * <p>Role matching is case-sensitive on purpose: auth-service mints {@code UserRole.name()},
     * so only the uppercase constants are roles this system actually issues. Anything else
     * (notably {@code ADMIN}) is not a chat participant.
     *
     * @return {@code null} when the caller cannot be placed — missing ids, or a role that is not
     *         a chat participant. Callers decide what that means for them (the socket closes,
     *         the controller returns 403).
     */
    public static ConversationParticipants resolve(UUID callerId, String role, UUID counterpartyId) {
        if (callerId == null || counterpartyId == null || role == null) {
            return null;
        }
        if (ROLE_USER.equals(role)) {
            return new ConversationParticipants(callerId, counterpartyId, SENDER_TYPE_USER);
        }
        if (ROLE_ADVISOR.equals(role)) {
            return new ConversationParticipants(counterpartyId, callerId, SENDER_TYPE_ADVISOR);
        }
        return null;
    }

    /**
     * A stable, synthetic identifier for one conversation.
     *
     * <p>Cassandra stores no such column: a conversation <em>is</em> its {@code (user_id,
     * advisor_id)} partition key, and both summary tables are keyed on the two ids separately. The
     * REST clients nonetheless want one opaque handle they can use to correlate a message with the
     * conversation it belongs to, so one is derived here.
     *
     * <p>Written in partition-key order — seeker first, advisor second — never sorted. The order
     * is already unambiguous because {@link #resolve} fixes it from the caller's role, and sorting
     * would throw away the information about which id is which.
     */
    public static String conversationId(UUID userId, UUID advisorId) {
        return userId + ":" + advisorId;
    }

    /** This conversation's synthetic id. */
    public String conversationId() {
        return conversationId(userId, advisorId);
    }

    /** Whether the caller is the seeker rather than the advisor. */
    public boolean callerIsUser() {
        return SENDER_TYPE_USER.equals(senderType);
    }

    /** The authenticated caller's own id — whichever side the role placed them on. */
    public UUID callerId() {
        return callerIsUser() ? userId : advisorId;
    }

    /** The other party's id. */
    public UUID counterpartyId() {
        return callerIsUser() ? advisorId : userId;
    }
}
