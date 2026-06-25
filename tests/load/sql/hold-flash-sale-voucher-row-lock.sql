-- Callers may set @lock_voucher_id and @lock_hold_seconds before sourcing this script.
set @lock_voucher_id := coalesce(@lock_voucher_id, 1001);
set @lock_hold_seconds := coalesce(@lock_hold_seconds, 60);

start transaction;

select
    'LOCK_ACQUIRED' as event,
    @lock_voucher_id as voucher_id,
    @lock_hold_seconds as hold_seconds,
    id,
    stock,
    status
from ls_flash_sale_voucher
where voucher_id = @lock_voucher_id
for update;

do sleep(@lock_hold_seconds);

rollback;

select
    'LOCK_RELEASED' as event,
    @lock_voucher_id as voucher_id;

