local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'life:flash:voucher:stock:' .. voucherId
local orderKey = 'life:flash:voucher:users:' .. voucherId

local stock = redis.call('GET', stockKey)
if (stock == false or redis.call('EXISTS', orderKey) == 0) then
    return 3
end

if (tonumber(stock) <= 0) then
    return 1
end

if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    return 2
end

redis.call('INCRBY', stockKey, -1)
redis.call('SADD', orderKey, userId)

return 0
