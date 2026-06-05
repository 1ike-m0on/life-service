package io.github.ikemoon.lifeservice.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.service.NoteQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/notes")
public class MerchantNoteController {

    private final NoteQueryService noteQueryService;

    public MerchantNoteController(NoteQueryService noteQueryService) {
        this.noteQueryService = noteQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NoteCardResponse>> pageMerchantNotes(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        Page<NoteCardResponse> page = noteQueryService.pageMerchantNotes(merchantId, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }
}
