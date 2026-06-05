package io.github.ikemoon.lifeservice.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitFailureStrategy;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitType;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter;
import io.github.ikemoon.lifeservice.note.request.NoteCreateRequest;
import io.github.ikemoon.lifeservice.note.request.NoteUpdateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
import io.github.ikemoon.lifeservice.note.service.NoteCommandService;
import io.github.ikemoon.lifeservice.note.service.NoteQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me/notes")
public class MyNoteController {

    private final NoteQueryService noteQueryService;
    private final NoteCommandService noteCommandService;

    public MyNoteController(NoteQueryService noteQueryService, NoteCommandService noteCommandService) {
        this.noteQueryService = noteQueryService;
        this.noteCommandService = noteCommandService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NoteCardResponse>> pageMyNotes(
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        Long userId = UserContext.requiredUserId();
        Page<NoteCardResponse> page = noteQueryService.pageCurrentUserNotes(userId, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }

    @PostMapping
    @RateLimiter(
            key = "life:rate:note:write:",
            window = 60,
            limit = 10,
            message = "Note publishing is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<NoteDetailResponse> createMyNote(@Valid @RequestBody NoteCreateRequest request) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteCommandService.createCurrentUserNote(userId, request));
    }

    @PutMapping("/{noteId}")
    @RateLimiter(
            key = "life:rate:note:write:",
            window = 60,
            limit = 20,
            message = "Note update is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<NoteDetailResponse> updateMyNote(
            @PathVariable @Min(1) Long noteId,
            @Valid @RequestBody NoteUpdateRequest request) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteCommandService.updateCurrentUserNote(userId, noteId, request));
    }

    @DeleteMapping("/{noteId}")
    @RateLimiter(
            key = "life:rate:note:write:",
            window = 60,
            limit = 20,
            message = "Note operation is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<Void> deleteMyNote(@PathVariable @Min(1) Long noteId) {
        Long userId = UserContext.requiredUserId();
        noteCommandService.deleteCurrentUserNote(userId, noteId);
        return ApiResponse.ok();
    }
}
