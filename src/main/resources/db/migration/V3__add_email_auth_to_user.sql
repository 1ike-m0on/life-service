set @email_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'ls_user'
      and column_name = 'email'
);

set @add_email_column_sql = if(
    @email_column_count = 0,
    'alter table ls_user add column email varchar(255) null after id',
    'select 1'
);

prepare add_email_column_stmt from @add_email_column_sql;
execute add_email_column_stmt;
deallocate prepare add_email_column_stmt;

set @phone_not_null_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'ls_user'
      and column_name = 'phone'
      and is_nullable = 'NO'
);

set @modify_phone_column_sql = if(
    @phone_not_null_count = 1,
    'alter table ls_user modify phone varchar(20) null',
    'select 1'
);

prepare modify_phone_column_stmt from @modify_phone_column_sql;
execute modify_phone_column_stmt;
deallocate prepare modify_phone_column_stmt;

set @email_index_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'ls_user'
      and index_name = 'uk_ls_user_email'
);

set @add_email_index_sql = if(
    @email_index_count = 0,
    'create unique index uk_ls_user_email on ls_user (email)',
    'select 1'
);

prepare add_email_index_stmt from @add_email_index_sql;
execute add_email_index_stmt;
deallocate prepare add_email_index_stmt;

update ls_user
set email = concat('demo', id, '@life.local')
where id between 2001 and 2020
  and email is null;
