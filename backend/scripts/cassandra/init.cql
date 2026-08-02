-- AdvisorConnect Cassandra schema initialisation
-- Run once on first startup

CREATE KEYSPACE IF NOT EXISTS chat_service
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
  AND durable_writes = true;

USE chat_service;

-- Messages partitioned by (user_id, advisor_id) so all messages
-- for a conversation are co-located; clustered by created_at DESC
-- for efficient "latest N messages" queries.
-- Column names here are the source of truth for Message.java's @PrimaryKeyColumn /
-- @Column mappings: message_id (not `id`) and the `read` flag must both be present,
-- otherwise reads/writes through Spring Data Cassandra fail on unknown columns.
CREATE TABLE IF NOT EXISTS messages (
  user_id       uuid,
  advisor_id    uuid,
  created_at    timestamp,
  message_id    uuid,
  sender_id     uuid,
  sender_type   text,
  text          text,
  read          boolean,
  PRIMARY KEY ((user_id, advisor_id), created_at, message_id)
) WITH CLUSTERING ORDER BY (created_at DESC, message_id ASC)
  AND default_time_to_live = 0
  AND gc_grace_seconds = 864000;

-- Conversation summary (denormalised for inbox queries)
CREATE TABLE IF NOT EXISTS conversations_by_user (
  user_id           uuid,
  advisor_id        uuid,
  advisor_username  text,
  advisor_color     text,
  last_message      text,
  last_message_at   timestamp,
  unread_count      int,
  is_advisor_online boolean,
  PRIMARY KEY (user_id, last_message_at, advisor_id)
) WITH CLUSTERING ORDER BY (last_message_at DESC, advisor_id ASC);

-- Reverse lookup: advisor's inbox
CREATE TABLE IF NOT EXISTS conversations_by_advisor (
  advisor_id      uuid,
  user_id         uuid,
  last_message    text,
  last_message_at timestamp,
  unread_count    int,
  PRIMARY KEY (advisor_id, last_message_at, user_id)
) WITH CLUSTERING ORDER BY (last_message_at DESC, user_id ASC);
