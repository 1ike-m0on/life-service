package io.github.ikemoon.lifeservice.order.service.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderDetailResponse;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderSummaryResponse;

public interface VoucherOrderQueryService {

    Page<VoucherOrderSummaryResponse> pageCurrentUserOrders(Long userId, Integer status, long pageNo, long pageSize);

    VoucherOrderDetailResponse getCurrentUserOrder(String orderNo, Long userId);
}
