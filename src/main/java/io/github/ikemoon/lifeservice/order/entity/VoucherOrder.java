package io.github.ikemoon.lifeservice.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_voucher_order")
public class VoucherOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long voucherId;
    private Long merchantId;
    private Long payAmountCent;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private LocalDateTime updatedAt;
}
