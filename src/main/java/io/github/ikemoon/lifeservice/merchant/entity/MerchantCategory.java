package io.github.ikemoon.lifeservice.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_merchant_category")
public class MerchantCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String iconUrl;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
