set @load_merchant_id_min := coalesce(@load_merchant_id_min, 100000);
set @load_note_id_min := coalesce(@load_note_id_min, 200000);

select 'imported_merchants' as metric, count(*) as value
from ls_merchant
where id >= @load_merchant_id_min;

select 'imported_notes' as metric, count(*) as value
from ls_merchant_note
where id >= @load_note_id_min;

select 'orphan_notes' as metric, count(*) as value
from ls_merchant_note note
left join ls_merchant merchant on merchant.id = note.merchant_id
where note.id >= @load_note_id_min
  and merchant.id is null;

select 'notes_with_missing_seed_user' as metric, count(*) as value
from ls_merchant_note note
left join ls_user user on user.id = note.user_id
where note.id >= @load_note_id_min
  and user.id is null;

select 'visible_imported_notes' as metric, count(*) as value
from ls_merchant_note
where id >= @load_note_id_min
  and status = 1;

select merchant_id, count(*) as note_count
from ls_merchant_note
where id >= @load_note_id_min
group by merchant_id
order by note_count desc
limit 10;

explain
select id, category_id, name, images, area, address, score, sold_count
from ls_merchant
where status = 1
order by score desc, sold_count desc, id asc
limit 20;

explain
select id, user_id, merchant_id, title, rating, like_count, created_at
from ls_merchant_note
where status = 1
order by created_at desc, id desc
limit 20;

explain
select id, user_id, merchant_id, title, rating, like_count, created_at
from ls_merchant_note
where merchant_id = (
    select id
    from ls_merchant
    where id >= @load_merchant_id_min
    order by comment_count desc, id asc
    limit 1
)
  and status = 1
order by created_at desc, id desc
limit 20;

explain
select id, order_no, voucher_id, merchant_id, status, created_at
from ls_voucher_order
where user_id = 2001
order by created_at desc, id desc
limit 10;

explain
select id, note_id, created_at
from ls_note_favorite
where user_id = 2001
  and status = 1
order by created_at desc, id desc
limit 10;
