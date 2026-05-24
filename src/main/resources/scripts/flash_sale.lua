local metadataKey = KEYS[1]
local stockKey = KEYS[2]
local orderKey = KEYS[3]

local userId = ARGV[1]
local now = tonumber(ARGV[2])

local stock = redis.call('GET', stockKey)
if (stock == false or redis.call('EXISTS', orderKey) == 0 or redis.call('EXISTS', metadataKey) == 0) then
    return 3
end

if (redis.call('TYPE', metadataKey)['ok'] ~= 'hash' or redis.call('TYPE', orderKey)['ok'] ~= 'set') then
    return 3
end

local status = tonumber(redis.call('HGET', metadataKey, 'status'))
local startTime = tonumber(redis.call('HGET', metadataKey, 'startTime'))
local endTime = tonumber(redis.call('HGET', metadataKey, 'endTime'))

if (not status or not now) then
    return 3
end

if (status ~= 2) then
    return 4
end

if (startTime and startTime > 0 and now < startTime) then
    return 4
end

if (endTime and endTime > 0 and now > endTime) then
    return 5
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
