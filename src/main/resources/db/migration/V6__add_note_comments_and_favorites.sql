create table if not exists ls_note_comment (
    id bigint primary key auto_increment,
    note_id bigint not null,
    user_id bigint not null,
    parent_id bigint null,
    content varchar(500) not null,
    status tinyint not null default 1 comment '0 deleted, 1 visible',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    index idx_note_comment_note_created (note_id, status, created_at),
    index idx_note_comment_user_created (user_id, created_at),
    index idx_note_comment_parent (parent_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_note_favorite (
    id bigint primary key auto_increment,
    user_id bigint not null,
    note_id bigint not null,
    status tinyint not null default 1 comment '0 canceled, 1 favorited',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    unique key uk_note_favorite_user_note (user_id, note_id),
    index idx_note_favorite_user_created (user_id, status, created_at),
    index idx_note_favorite_note (note_id, status)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into ls_note_comment (
    id,
    note_id,
    user_id,
    parent_id,
    content,
    status,
    created_at,
    updated_at
)
values
    (1, 1, 2002, null, '这家咖啡我也去过，下午人少的时候更舒服。', 1, timestampadd(day, -2, now()), timestampadd(day, -2, now())),
    (2, 1, 2003, 1, '同感，靠窗位置很适合办公。', 1, timestampadd(day, -1, now()), timestampadd(day, -1, now())),
    (3, 4, 2001, null, '红汤锅底确实香，晚一点去排队会短一些。', 1, timestampadd(hour, -8, now()), timestampadd(hour, -8, now()))
on duplicate key update
    note_id = values(note_id),
    user_id = values(user_id),
    parent_id = values(parent_id),
    content = values(content),
    status = values(status),
    created_at = values(created_at),
    updated_at = values(updated_at);

insert into ls_note_favorite (
    id,
    user_id,
    note_id,
    status,
    created_at,
    updated_at
)
values
    (1, 2001, 4, 1, timestampadd(hour, -6, now()), timestampadd(hour, -6, now())),
    (2, 2001, 8, 1, timestampadd(hour, -5, now()), timestampadd(hour, -5, now())),
    (3, 2002, 1, 1, timestampadd(hour, -4, now()), timestampadd(hour, -4, now()))
on duplicate key update
    status = values(status),
    created_at = values(created_at),
    updated_at = values(updated_at);
