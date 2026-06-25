-- Callers may set these session variables before sourcing this script.
set @load_voucher_id := coalesce(@load_voucher_id, 1001);
set @load_stock := coalesce(@load_stock, 12000);

delete from ls_stock_release_task
where voucher_id = @load_voucher_id;

delete from ls_voucher_order
where voucher_id = @load_voucher_id;

update ls_flash_sale_voucher
set stock = @load_stock,
    start_time = timestampadd(hour, -1, now()),
    end_time = timestampadd(day, 7, now()),
    status = 2
where voucher_id = @load_voucher_id;
