-- Callers may set @check_voucher_id before sourcing this script.
set @check_voucher_id := coalesce(@check_voucher_id, 1001);

select
    @check_voucher_id as voucher_id,
    (
        select stock
        from ls_flash_sale_voucher
        where voucher_id = @check_voucher_id
    ) as mysql_stock,
    (
        select count(*)
        from ls_voucher_order
        where voucher_id = @check_voucher_id
    ) as mysql_order_count,
    (
        select count(*)
        from (
            select user_id
            from ls_voucher_order
            where voucher_id = @check_voucher_id
            group by user_id
            having count(*) > 1
        ) duplicate_users
    ) as duplicate_user_count;

select
    status,
    case status
        when 1 then 'PENDING_PAYMENT'
        when 2 then 'PAY_SUCCESS'
        when 3 then 'CLOSED'
        else 'UNKNOWN'
    end as status_name,
    count(*) as order_count
from ls_voucher_order
where voucher_id = @check_voucher_id
group by status
order by status;

select
    user_id,
    count(*) as order_count
from ls_voucher_order
where voucher_id = @check_voucher_id
group by user_id
having count(*) > 1
order by order_count desc, user_id
limit 20;
