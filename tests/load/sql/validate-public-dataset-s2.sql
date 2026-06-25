set @load_merchant_id_min := coalesce(@load_merchant_id_min, 100000);
set @load_note_id_min := coalesce(@load_note_id_min, 200000);
set @load_favorite_id_min := coalesce(@load_favorite_id_min, 300000);
set @expected_s2_merchants := coalesce(@expected_s2_merchants, 50000);
set @expected_s2_notes := coalesce(@expected_s2_notes, 300000);
set @expected_s2_favorites := coalesce(@expected_s2_favorites, 120000);

select
    's2_imported_merchants' as metric,
    @expected_s2_merchants as expected_value,
    count(*) as actual_value,
    count(*) = @expected_s2_merchants as pass
from ls_merchant
where id >= @load_merchant_id_min;

select
    's2_imported_notes' as metric,
    @expected_s2_notes as expected_value,
    count(*) as actual_value,
    count(*) = @expected_s2_notes as pass
from ls_merchant_note
where id >= @load_note_id_min;

select
    's2_imported_favorites' as metric,
    @expected_s2_favorites as expected_value,
    count(*) as actual_value,
    count(*) = @expected_s2_favorites as pass
from ls_note_favorite
where note_id >= @load_note_id_min;

select
    's2_orphan_notes' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from ls_merchant_note note
left join ls_merchant merchant on merchant.id = note.merchant_id
where note.id >= @load_note_id_min
  and merchant.id is null;

select
    's2_orphan_favorites' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from ls_note_favorite favorite
left join ls_merchant_note note on note.id = favorite.note_id
where favorite.note_id >= @load_note_id_min
  and note.id is null;

select
    's2_notes_with_missing_seed_user' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from ls_merchant_note note
left join ls_user user on user.id = note.user_id
where note.id >= @load_note_id_min
  and user.id is null;

select
    's2_favorites_with_missing_seed_user' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from ls_note_favorite favorite
left join ls_user user on user.id = favorite.user_id
where favorite.note_id >= @load_note_id_min
  and user.id is null;

select
    's2_merchants_with_images' as metric,
    @expected_s2_merchants as expected_value,
    count(*) as actual_value,
    count(*) = @expected_s2_merchants as pass
from ls_merchant
where id >= @load_merchant_id_min
  and nullif(images, '') is not null;

select
    's2_notes_with_images' as metric,
    concat('>= ', floor(@expected_s2_notes * 0.75)) as expected_value,
    count(*) as actual_value,
    count(*) >= floor(@expected_s2_notes * 0.75) as pass
from ls_merchant_note
where id >= @load_note_id_min
  and nullif(images, '') is not null;

select
    's2_note_favorite_count_mismatch' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from (
    select
        note.id,
        note.favorite_count,
        count(favorite.id) as actual_favorite_count
    from ls_merchant_note note
    left join ls_note_favorite favorite
        on favorite.note_id = note.id
       and favorite.status = 1
    where note.id >= @load_note_id_min
    group by note.id, note.favorite_count
) counted
where counted.favorite_count <> counted.actual_favorite_count;

select
    's2_merchant_comment_count_mismatch' as metric,
    0 as expected_value,
    count(*) as actual_value,
    count(*) = 0 as pass
from (
    select
        merchant.id,
        merchant.comment_count,
        count(note.id) as actual_note_count
    from ls_merchant merchant
    left join ls_merchant_note note
        on note.merchant_id = merchant.id
       and note.id >= @load_note_id_min
    where merchant.id >= @load_merchant_id_min
    group by merchant.id, merchant.comment_count
) counted
where counted.comment_count <> counted.actual_note_count;

select merchant_id, count(*) as note_count
from ls_merchant_note
where id >= @load_note_id_min
group by merchant_id
order by note_count desc
limit 10;

select user_id, count(*) as favorite_count
from ls_note_favorite
where note_id >= @load_note_id_min
  and status = 1
group by user_id
order by favorite_count desc, user_id asc
limit 10;

set @s2_hot_merchant_id := (
    select merchant_id
    from ls_merchant_note
    where id >= @load_note_id_min
    group by merchant_id
    order by count(*) desc, merchant_id asc
    limit 1
);

set @s2_favorite_user_id := (
    select user_id
    from ls_note_favorite
    where note_id >= @load_note_id_min
      and status = 1
    group by user_id
    order by count(*) desc, user_id asc
    limit 1
);

explain
select id, category_id, name, images, area, address, score, sold_count
from ls_merchant
where status = 1
order by score desc, sold_count desc, id asc
limit 20;

explain
select id, category_id, name, images, area, address, score, sold_count
from ls_merchant
where category_id = (
    select category_id
    from ls_merchant
    where id >= @load_merchant_id_min
    order by comment_count desc, id asc
    limit 1
)
  and status = 1
order by score desc, sold_count desc, id asc
limit 20;

explain
select id, user_id, merchant_id, title, rating, like_count, favorite_count, created_at
from ls_merchant_note
where status = 1
order by created_at desc, id desc
limit 20;

explain
select id, user_id, merchant_id, title, rating, like_count, favorite_count, created_at
from ls_merchant_note
where status = 1
order by created_at desc, id desc
limit 20000, 20;

explain
select id, user_id, merchant_id, title, rating, like_count, favorite_count, created_at
from ls_merchant_note
where merchant_id = @s2_hot_merchant_id
  and status = 1
order by created_at desc, id desc
limit 20;

explain
select id, note_id, created_at
from ls_note_favorite
where user_id = @s2_favorite_user_id
  and status = 1
order by created_at desc, id desc
limit 10;
