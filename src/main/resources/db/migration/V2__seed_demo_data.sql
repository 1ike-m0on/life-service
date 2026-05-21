insert into ls_user (id, phone, password_hash, nickname, avatar_url, status)
values
    (2001, '18800002001', null, 'Demo User 2001', null, 1),
    (2002, '18800002002', null, 'Demo User 2002', null, 1),
    (2003, '18800002003', null, 'Demo User 2003', null, 1),
    (2004, '18800002004', null, 'Demo User 2004', null, 1),
    (2005, '18800002005', null, 'Demo User 2005', null, 1),
    (2006, '18800002006', null, 'Demo User 2006', null, 1),
    (2007, '18800002007', null, 'Demo User 2007', null, 1),
    (2008, '18800002008', null, 'Demo User 2008', null, 1),
    (2009, '18800002009', null, 'Demo User 2009', null, 1),
    (2010, '18800002010', null, 'Demo User 2010', null, 1),
    (2011, '18800002011', null, 'Demo User 2011', null, 1),
    (2012, '18800002012', null, 'Demo User 2012', null, 1),
    (2013, '18800002013', null, 'Demo User 2013', null, 1),
    (2014, '18800002014', null, 'Demo User 2014', null, 1),
    (2015, '18800002015', null, 'Demo User 2015', null, 1),
    (2016, '18800002016', null, 'Demo User 2016', null, 1),
    (2017, '18800002017', null, 'Demo User 2017', null, 1),
    (2018, '18800002018', null, 'Demo User 2018', null, 1),
    (2019, '18800002019', null, 'Demo User 2019', null, 1),
    (2020, '18800002020', null, 'Demo User 2020', null, 1)
on duplicate key update
    nickname = values(nickname),
    status = values(status);

insert into ls_merchant_category (id, name, icon_url, sort_order, status)
values
    (1, 'Coffee', null, 10, 1),
    (2, 'Hotpot', null, 20, 1),
    (3, 'Bakery', null, 30, 1),
    (4, 'Japanese Food', null, 40, 1),
    (5, 'Fitness', null, 50, 1),
    (6, 'Cinema', null, 60, 1)
on duplicate key update
    name = values(name),
    sort_order = values(sort_order),
    status = values(status);

insert into ls_merchant (
    id,
    category_id,
    name,
    images,
    area,
    address,
    longitude,
    latitude,
    avg_price_cent,
    sold_count,
    comment_count,
    score,
    open_hours,
    status
)
values
    (1, 1, 'Moonlight Coffee', null, 'Chaoyang', '88 Lake Road, Chaoyang', 116.487213, 39.921952, 3600, 1280, 342, 47, '08:00-22:30', 1),
    (2, 1, 'Riverbank Espresso', null, 'Haidian', '19 Garden Street, Haidian', 116.320101, 39.984723, 4200, 930, 218, 46, '07:30-21:30', 1),
    (3, 2, 'Red Flame Hotpot', null, 'Chaoyang', '6 North Star Avenue, Chaoyang', 116.445617, 39.966118, 12800, 3980, 826, 48, '10:30-02:00', 1),
    (4, 2, 'Shanhai Beef Hotpot', null, 'Xicheng', '21 West Bridge Road, Xicheng', 116.372405, 39.914228, 11800, 2510, 511, 45, '11:00-23:30', 1),
    (5, 3, 'Morning Wheat Bakery', null, 'Dongcheng', '13 Bell Tower Lane, Dongcheng', 116.410301, 39.936802, 2800, 1860, 419, 49, '07:00-20:30', 1),
    (6, 3, 'Sweet Oven', null, 'Haidian', '52 College Road, Haidian', 116.352977, 39.983501, 3300, 1520, 306, 46, '08:00-21:00', 1),
    (7, 4, 'Sora Sushi', null, 'Chaoyang', '9 Riverside Plaza, Chaoyang', 116.476890, 39.908377, 9800, 1180, 289, 47, '11:00-22:00', 1),
    (8, 4, 'Kyoto Bento Lab', null, 'Haidian', '66 Silicon Valley Street, Haidian', 116.301882, 39.976210, 5600, 2240, 533, 44, '10:00-21:00', 1),
    (9, 5, 'Urban Fit Studio', null, 'Chaoyang', '3 Sports Center Road, Chaoyang', 116.462301, 39.939188, 8800, 760, 157, 45, '06:30-22:30', 1),
    (10, 6, 'Starlight Cinema', null, 'Xicheng', '101 City Mall, Xicheng', 116.384520, 39.903712, 5900, 5810, 1042, 48, '10:00-01:00', 1)
on duplicate key update
    category_id = values(category_id),
    name = values(name),
    area = values(area),
    address = values(address),
    longitude = values(longitude),
    latitude = values(latitude),
    avg_price_cent = values(avg_price_cent),
    sold_count = values(sold_count),
    comment_count = values(comment_count),
    score = values(score),
    open_hours = values(open_hours),
    status = values(status);

insert into ls_voucher (
    id,
    merchant_id,
    title,
    subtitle,
    rules,
    pay_amount_cent,
    discount_amount_cent,
    type,
    status
)
values
    (1001, 1, 'Coffee Flash Sale 19.9', 'Pay 19.9, save 20', 'Valid for coffee and tea drinks. Weekday only.', 1990, 2000, 2, 1),
    (1002, 3, 'Hotpot Flash Sale 99', 'Pay 99, save 80', 'Valid after 17:00. Not combinable with other coupons.', 9900, 8000, 2, 1),
    (1003, 5, 'Bakery Flash Sale 29.9', 'Pay 29.9, save 25', 'Valid for selected bread and cake products.', 2990, 2500, 2, 1),
    (1004, 7, 'Sushi Flash Sale 79', 'Pay 79, save 60', 'Lunch set coupon. Valid before 14:00.', 7900, 6000, 2, 1),
    (1101, 1, 'Coffee 50-10', 'Save 10 when spending 50', 'Valid for all drinks.', 4000, 1000, 1, 1),
    (1102, 2, 'Espresso Set 35', 'Coffee and dessert set', 'Valid for dine-in orders.', 3500, 1500, 1, 1),
    (1103, 3, 'Hotpot 200-40', 'Save 40 when spending 200', 'Valid for dine-in hotpot orders.', 16000, 4000, 1, 1),
    (1104, 4, 'Beef Hotpot 188', 'Two-person set', 'Reservation recommended.', 18800, 7000, 1, 1),
    (1105, 5, 'Bakery 60-15', 'Save 15 when spending 60', 'Valid for cakes and pastry.', 4500, 1500, 1, 1),
    (1106, 6, 'Sweet Oven 39', 'Dessert box coupon', 'Valid for take-away orders.', 3900, 1800, 1, 1),
    (1107, 7, 'Sushi 120-30', 'Save 30 when spending 120', 'Valid for dinner orders.', 9000, 3000, 1, 1),
    (1108, 8, 'Bento Lunch 28', 'Selected bento coupon', 'Weekday lunch only.', 2800, 1200, 1, 1),
    (1109, 9, 'Fitness Trial 19.9', 'One trial class', 'Appointment required.', 1990, 8000, 1, 1),
    (1110, 10, 'Cinema Ticket 39.9', 'Selected movie ticket', 'Seat upgrade not included.', 3990, 2000, 1, 1)
on duplicate key update
    merchant_id = values(merchant_id),
    title = values(title),
    subtitle = values(subtitle),
    rules = values(rules),
    pay_amount_cent = values(pay_amount_cent),
    discount_amount_cent = values(discount_amount_cent),
    type = values(type),
    status = values(status);

insert into ls_flash_sale_voucher (id, voucher_id, stock, start_time, end_time, status)
values
    (1, 1001, 12000, timestampadd(hour, -1, now()), timestampadd(day, 7, now()), 2),
    (2, 1002, 8000, timestampadd(hour, -1, now()), timestampadd(day, 5, now()), 2),
    (3, 1003, 5000, timestampadd(hour, -1, now()), timestampadd(day, 3, now()), 2),
    (4, 1004, 3000, timestampadd(hour, -1, now()), timestampadd(day, 2, now()), 2)
on duplicate key update
    stock = values(stock),
    start_time = values(start_time),
    end_time = values(end_time),
    status = values(status);
