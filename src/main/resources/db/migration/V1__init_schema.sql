create table if not exists ls_user (
    id bigint not null auto_increment,
    phone varchar(20) not null,
    password_hash varchar(128) null,
    nickname varchar(64) not null,
    avatar_url varchar(512) null,
    status tinyint not null default 1 comment '0 disabled, 1 active',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_ls_user_phone (phone),
    key idx_ls_user_created_at (created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_merchant_category (
    id bigint not null auto_increment,
    name varchar(64) not null,
    icon_url varchar(512) null,
    sort_order int not null default 0,
    status tinyint not null default 1 comment '0 disabled, 1 enabled',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_ls_merchant_category_sort (status, sort_order)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_merchant (
    id bigint not null auto_increment,
    category_id bigint not null,
    name varchar(128) not null,
    images varchar(2048) null,
    area varchar(128) null,
    address varchar(255) not null,
    longitude decimal(10, 6) null,
    latitude decimal(10, 6) null,
    avg_price_cent bigint null,
    sold_count int not null default 0,
    comment_count int not null default 0,
    score int not null default 0,
    open_hours varchar(64) null,
    status tinyint not null default 1 comment '0 disabled, 1 open, 2 resting',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_ls_merchant_category (category_id, status, id),
    key idx_ls_merchant_name (name),
    key idx_ls_merchant_area (area),
    key idx_ls_merchant_score (score),
    key idx_ls_merchant_updated_at (updated_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_voucher (
    id bigint not null auto_increment,
    merchant_id bigint not null,
    title varchar(255) not null,
    subtitle varchar(255) null,
    rules varchar(2048) null,
    pay_amount_cent bigint not null,
    discount_amount_cent bigint not null,
    type tinyint not null comment '1 normal, 2 flash sale',
    status tinyint not null default 1 comment '1 on sale, 2 off sale, 3 expired',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_ls_voucher_merchant (merchant_id, status, id),
    key idx_ls_voucher_type_status (type, status)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_flash_sale_voucher (
    id bigint not null auto_increment,
    voucher_id bigint not null,
    stock int not null,
    start_time datetime not null,
    end_time datetime not null,
    status tinyint not null default 1 comment '0 disabled, 1 not started, 2 active, 3 ended',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_ls_flash_sale_voucher_voucher (voucher_id),
    key idx_ls_flash_sale_voucher_time (status, start_time, end_time)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_voucher_order (
    id bigint not null auto_increment,
    order_no varchar(64) not null,
    user_id bigint not null,
    voucher_id bigint not null,
    merchant_id bigint not null,
    pay_amount_cent bigint not null,
    status tinyint not null default 1 comment '1 pending payment, 2 paid, 3 closed',
    created_at datetime not null default current_timestamp,
    paid_at datetime null,
    closed_at datetime null,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_ls_voucher_order_order_no (order_no),
    unique key uk_ls_voucher_order_user_voucher (user_id, voucher_id),
    key idx_ls_voucher_order_user_created (user_id, created_at),
    key idx_ls_voucher_order_status_created (status, created_at),
    key idx_ls_voucher_order_merchant_created (merchant_id, created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_cache_delete_task (
    id bigint not null auto_increment,
    cache_key varchar(255) not null,
    reason varchar(255) null,
    retry_count int not null default 0,
    next_retry_at datetime not null,
    status tinyint not null default 1 comment '1 pending, 2 success, 3 failed',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    key idx_ls_cache_delete_task_retry (status, next_retry_at),
    key idx_ls_cache_delete_task_key (cache_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists ls_stock_release_task (
    id bigint not null auto_increment,
    order_id bigint not null,
    order_no varchar(64) not null,
    voucher_id bigint not null,
    user_id bigint not null,
    reason varchar(255) null,
    retry_count int not null default 0,
    next_retry_at datetime not null,
    status tinyint not null default 1 comment '1 pending, 2 success, 3 failed',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_ls_stock_release_task_order (order_id),
    key idx_ls_stock_release_task_retry (status, next_retry_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
