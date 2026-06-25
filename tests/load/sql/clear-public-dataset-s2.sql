set @load_merchant_id_min := coalesce(@load_merchant_id_min, 100000);
set @load_note_id_min := coalesce(@load_note_id_min, 200000);

delete from ls_note_favorite
where note_id >= @load_note_id_min;

delete from ls_note_comment
where note_id >= @load_note_id_min;

delete from ls_merchant_note
where id >= @load_note_id_min;

delete from ls_merchant
where id >= @load_merchant_id_min;
