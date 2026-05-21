package io.github.ikemoon.lifeservice.order.messaging;

public class FlashSaleOrderCommand {

    private String orderNo;
    private Long voucherId;
    private Long userId;

    public FlashSaleOrderCommand() {
    }

    public FlashSaleOrderCommand(String orderNo, Long voucherId, Long userId) {
        this.orderNo = orderNo;
        this.voucherId = voucherId;
        this.userId = userId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(Long voucherId) {
        this.voucherId = voucherId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String orderNo() {
        return orderNo;
    }

    public Long voucherId() {
        return voucherId;
    }

    public Long userId() {
        return userId;
    }
}
