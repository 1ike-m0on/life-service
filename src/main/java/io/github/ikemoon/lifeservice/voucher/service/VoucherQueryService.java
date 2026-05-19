package io.github.ikemoon.lifeservice.voucher.service;

import io.github.ikemoon.lifeservice.voucher.entity.Voucher;

import java.util.List;

public interface VoucherQueryService {

    List<Voucher> listMerchantVouchers(Long merchantId);
}
