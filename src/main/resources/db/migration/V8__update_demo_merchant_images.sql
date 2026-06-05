update ls_merchant
set images = case id
    when 1 then '/assets/merchants/coffee/moonlight-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg,/assets/merchants/coffee/riverbank-cover.jpg'
    when 2 then '/assets/merchants/coffee/riverbank-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg,/assets/merchants/coffee/moonlight-cover.jpg'
    when 3 then '/assets/merchants/hotpot/red-flame-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg,/assets/merchants/hotpot/shanhai-cover.jpg'
    when 4 then '/assets/merchants/hotpot/shanhai-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg,/assets/merchants/hotpot/red-flame-cover.jpg'
    when 5 then '/assets/merchants/bakery/morning-wheat-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg,/assets/merchants/bakery/sweet-oven-cover.jpg'
    when 6 then '/assets/merchants/bakery/sweet-oven-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg,/assets/merchants/bakery/morning-wheat-cover.jpg'
    when 7 then '/assets/merchants/japanese/sora-sushi-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg,/assets/merchants/japanese/kyoto-bento-cover.jpg'
    when 8 then '/assets/merchants/japanese/kyoto-bento-cover.jpg,/assets/merchants/japanese/sora-sushi-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg'
    when 9 then '/assets/merchants/lifestyle/urban-fit-cover.jpg,/assets/merchants/lifestyle/urban-fit-01.jpg'
    when 10 then '/assets/merchants/lifestyle/starlight-cinema-cover.jpg,/assets/merchants/lifestyle/starlight-cinema-01.jpg'
    else images
end
where id between 1 and 10;
