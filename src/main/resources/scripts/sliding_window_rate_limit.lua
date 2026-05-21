local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member = ARGV[4]

if (not key or not window or not limit or not now or not member or window <= 0 or limit <= 0) then
    return redis.error_reply("Invalid rate limit input")
end

local windowMillis = window * 1000
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMillis)

local current = redis.call('ZCARD', key)
if (current < limit) then
    redis.call('ZADD', key, now, member)
    redis.call('PEXPIRE', key, windowMillis)
    return current + 1
end

redis.call('PEXPIRE', key, windowMillis)
return 0
