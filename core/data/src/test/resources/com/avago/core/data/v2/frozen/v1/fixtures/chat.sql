-- Frozen v1 'chat' store fixture (plan 6.4; L43): rows a v1 client may hold, loaded into mobile/v1/chat.sql
-- with foreign_keys=ON. Covers every durable local table, every table v2 drops and every table v2
-- rebuilds (including values in columns whose FK target v2 retires). Each released version has its own
-- fixture; the generator names any table a step needs rows in.

INSERT INTO database_identity (singleton, user_id, account_id, contract_version, created_at) VALUES (1, '00000001-0000-4000-8000-4e3eb86cf041', '00000002-0000-4000-8000-4e3eb86cf041', 1, 1700000000000);
INSERT INTO threads (id, created_at, updated_at, thread_type, last_activity_at) VALUES ('00000005-0000-4000-8000-c91e11a1f2a2', 1700000000000, 1700000000000, 'team', 1700000000000);
INSERT INTO thread_members (created_at, updated_at, thread_id, user_id) VALUES (1700000000000, 1700000000000, '00000005-0000-4000-8000-c91e11a1f2a2', '0000000b-0000-4000-8000-d137c0869d56');
INSERT INTO messages (id, created_at, updated_at, thread_id, body_md) VALUES ('0000000c-0000-4000-8000-17f3467db103', 1700000000000, 1700000000000, '00000005-0000-4000-8000-c91e11a1f2a2', 'fixture 15');
INSERT INTO reactions (id, created_at, updated_at, message_id, user_id, emoji) VALUES ('00000010-0000-4000-8000-d7f57747ab99', 1700000000000, 1700000000000, '0000000c-0000-4000-8000-17f3467db103', '00000013-0000-4000-8000-d7f57747ab99', 'fixture 20');
INSERT INTO thread_last_read (created_at, updated_at, thread_id, user_id, last_read_at) VALUES (1700000000000, 1700000000000, '00000005-0000-4000-8000-c91e11a1f2a2', '00000017-0000-4000-8000-055356ff9131', 1700000000000);
