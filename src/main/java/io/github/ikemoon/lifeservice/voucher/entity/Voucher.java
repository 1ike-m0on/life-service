package io.github.ikemoon.lifeservice.voucher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_voucher")
public class Voucher {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String title;
    private String subtitle;
    private String rules;
    private Long payAmountCent;
    private Long discountAmountCent;
    private Integer type;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
