package io.github.ikemoon.lifeservice.infrastructure.cache.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_cache_delete_task")
public class CacheDeleteTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String cacheKey;
    private String reason;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
