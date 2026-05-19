package io.github.ikemoon.lifeservice.voucher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_flash_sale_voucher")
public class FlashSaleVoucher {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long voucherId;
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
