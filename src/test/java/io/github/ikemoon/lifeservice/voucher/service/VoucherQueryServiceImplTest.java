package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherQueryServiceImplTest {

    @Mock
    private VoucherMapper voucherMapper;

    @Test
    void listMerchantVouchersReturnsEnabledVoucherListFromMapper() {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setMerchantId(100L);
        voucher.setTitle("Flash Sale Voucher");
        voucher.setStatus(1);
        when(voucherMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(voucher));

        VoucherQueryServiceImpl service = new VoucherQueryServiceImpl(voucherMapper);

        assertThat(service.listMerchantVouchers(100L)).containsExactly(voucher);
    }
}
