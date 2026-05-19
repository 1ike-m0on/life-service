package io.github.ikemoon.lifeservice.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ls_merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String images;
    private String area;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Long avgPriceCent;
    private Integer soldCount;
    private Integer commentCount;
    private Integer score;
    private String openHours;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
