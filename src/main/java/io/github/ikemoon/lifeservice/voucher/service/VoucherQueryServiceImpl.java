package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoucherQueryServiceImpl implements VoucherQueryService {

    private final VoucherMapper voucherMapper;

    public VoucherQueryServiceImpl(VoucherMapper voucherMapper) {
        this.voucherMapper = voucherMapper;
    }

    @Override
    public List<Voucher> listMerchantVouchers(Long merchantId) {
        return voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getMerchantId, merchantId)
                .eq(Voucher::getStatus, 1)
                .orderByAsc(Voucher::getId));
    }
}
