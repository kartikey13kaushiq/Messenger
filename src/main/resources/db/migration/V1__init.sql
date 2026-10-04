create table users (
    id            uuid primary key,
    username      varchar(32)  not null,
    display_name  varchar(80)  not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null
);
create unique index users_username_uq on users (lower(username));

create table conversations (
    id              uuid primary key,
    type            varchar(10)  not null check (type in ('DIRECT', 'GROUP')),
    title           varchar(100),
    -- "smallerUserId:largerUserId" for direct chats, so each pair has exactly one conversation.
    direct_key      varchar(80)  unique,
    last_seq        bigint       not null default 0,
    last_message_at timestamptz,
    created_at      timestamptz  not null,
    constraint conversations_direct_key_ck check ((type = 'DIRECT') = (direct_key is not null))
);
create index conversations_last_message_idx on conversations (last_message_at desc nulls last);

create table conversation_members (
    id              uuid primary key,
    conversation_id uuid        not null references conversations (id) on delete cascade,
    user_id         uuid        not null references users (id) on delete cascade,
    role            varchar(10) not null check (role in ('OWNER', 'MEMBER')),
    last_read_seq   bigint      not null default 0,
    joined_at       timestamptz not null,
    unique (conversation_id, user_id)
);
create index conversation_members_user_idx on conversation_members (user_id);

create table messages (
    id                uuid primary key,
    conversation_id   uuid          not null references conversations (id) on delete cascade,
    sender_id         uuid          not null references users (id),
    seq               bigint        not null,
    body              varchar(4000),
    client_message_id varchar(64),
    created_at        timestamptz   not null,
    edited_at         timestamptz,
    deleted_at        timestamptz,
    unique (conversation_id, seq)
);
-- Lets clients retry a send safely: the same client id from the same sender is stored once.
create unique index messages_client_message_uq
    on messages (conversation_id, sender_id, client_message_id) where client_message_id is not null;
