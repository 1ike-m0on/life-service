create table if not exists ls_merchant_note (
    id bigint primary key auto_increment,
    user_id bigint not null,
    merchant_id bigint not null,
    order_id bigint null,
    title varchar(120) not null,
    content varchar(1000) not null,
    rating tinyint not null,
    images varchar(1000) null,
    like_count int not null default 0,
    status tinyint not null default 1,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    index idx_note_merchant_created (merchant_id, created_at),
    index idx_note_user_created (user_id, created_at),
    index idx_note_status_created (status, created_at)
);

insert into ls_merchant_note (
    id,
    user_id,
    merchant_id,
    order_id,
    title,
    content,
    rating,
    images,
    like_count,
    status,
    created_at,
    updated_at
)
values
    (1, 2001, 1, null, '午后咖啡和窗边座位都刚刚好', '拿铁奶香很稳，甜度不高，店里光线很适合周末坐一下午。桂花冷萃比预期清爽，适合不想喝太厚重的人。', 5, '/assets/merchants/coffee/moonlight-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg', 262, 1, timestampadd(day, -3, now()), timestampadd(day, -3, now())),
    (2, 2002, 1, null, '这家咖啡店适合短暂停留', '出杯速度快，座位不算多但翻台很快。19.9 的券很划算，适合作为附近办公区的日常补给。', 4, '/assets/merchants/coffee/moonlight-01.jpg,/assets/merchants/coffee/moonlight-cover.jpg', 171, 1, timestampadd(day, -2, now()), timestampadd(day, -2, now())),
    (3, 2003, 2, null, '河边这杯浓缩很有记忆点', 'Espresso 的酸苦平衡不错，甜点偏轻盈。晚上过来人少一些，聊天不吵。', 5, '/assets/merchants/coffee/riverbank-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg', 94, 1, timestampadd(day, -5, now()), timestampadd(day, -5, now())),
    (4, 2004, 3, null, '红汤锅底够香，排队值得', '锅底越煮越香，牛肉和毛肚都很新鲜。套餐券覆盖两个人刚好，建议早点到。', 5, '/assets/merchants/hotpot/red-flame-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg', 344, 1, timestampadd(day, -1, now()), timestampadd(day, -1, now())),
    (5, 2005, 4, null, '牛肉锅很适合聚餐', '汤底比较清爽，牛肉切得厚度合适。店员会主动加汤，服务体验比预想好。', 4, '/assets/merchants/hotpot/shanhai-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg', 138, 1, timestampadd(day, -4, now()), timestampadd(day, -4, now())),
    (6, 2006, 5, null, '早上路过会想再买一次', '可颂外皮很酥，吐司有麦香。适合早餐顺手带走，甜口不会腻。', 5, '/assets/merchants/bakery/morning-wheat-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg', 221, 1, timestampadd(day, -6, now()), timestampadd(day, -6, now())),
    (7, 2007, 6, null, '甜品盒子很适合下午茶', '奶油轻，水果量也足。外带包装稳，回家打开状态还不错。', 4, '/assets/merchants/bakery/sweet-oven-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg', 118, 1, timestampadd(day, -7, now()), timestampadd(day, -7, now())),
    (8, 2008, 7, null, '午市寿司套餐性价比很高', '鱼生新鲜度在线，米饭温度正好。午餐券价格友好，附近上班族可以冲。', 5, '/assets/merchants/japanese/sora-sushi-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg', 196, 1, timestampadd(day, -2, now()), timestampadd(day, -2, now())),
    (9, 2009, 8, null, '便当出餐很快，工作日友好', '鳗鱼饭酱汁偏甜，米饭软硬合适。工作日中午不用等太久。', 4, '/assets/merchants/japanese/kyoto-bento-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg', 87, 1, timestampadd(day, -8, now()), timestampadd(day, -8, now())),
    (10, 2010, 9, null, '体验课比想象中专业', '教练会先问运动基础，再安排训练强度。场地干净，器械也比较新。', 5, '/assets/merchants/lifestyle/urban-fit-cover.jpg,/assets/merchants/lifestyle/urban-fit-01.jpg', 73, 1, timestampadd(day, -9, now()), timestampadd(day, -9, now())),
    (11, 2011, 10, null, '周末看电影座位很舒服', '影厅音效不错，座椅间距也宽。取票很顺，商场吃饭选择也多。', 5, '/assets/merchants/lifestyle/starlight-cinema-cover.jpg,/assets/merchants/lifestyle/starlight-cinema-01.jpg', 159, 1, timestampadd(day, -10, now()), timestampadd(day, -10, now())),
    (12, 2012, 3, null, '下班后吃一顿热锅很治愈', '人多但上菜速度没有拖。辣锅香气足，饮料和小食也能用券，整体很稳。', 5, '/assets/merchants/hotpot/red-flame-01.jpg,/assets/merchants/hotpot/red-flame-cover.jpg', 287, 1, timestampadd(hour, -12, now()), timestampadd(hour, -12, now()))
on duplicate key update
    user_id = values(user_id),
    merchant_id = values(merchant_id),
    title = values(title),
    content = values(content),
    rating = values(rating),
    images = values(images),
    like_count = values(like_count),
    status = values(status),
    created_at = values(created_at),
    updated_at = values(updated_at);
