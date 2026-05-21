local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'life:flash:voucher:stock:' .. voucherId
local orderKey = 'life:flash:voucher:users:' .. voucherId

if (redis.call('SREM', orderKey, userId) == 1) then
    redis.call('INCRBY', stockKey, 1)
    return 1
end

return 0
