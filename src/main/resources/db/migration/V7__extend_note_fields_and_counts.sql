alter table ls_merchant_note
    modify column title varchar(128) not null,
    modify column content varchar(2000) not null,
    modify column rating tinyint null,
    modify column images varchar(2048) null,
    add column comment_count int not null default 0 after like_count,
    add column favorite_count int not null default 0 after comment_count;

update ls_merchant_note note
set comment_count = (
        select count(1)
        from ls_note_comment comment
        where comment.note_id = note.id
          and comment.status = 1
    ),
    favorite_count = (
        select count(1)
        from ls_note_favorite favorite
        where favorite.note_id = note.id
          and favorite.status = 1
    );
