package io.github.ikemoon.lifeservice.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitFailureStrategy;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitType;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter;
import io.github.ikemoon.lifeservice.note.request.NoteCommentCreateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;
import io.github.ikemoon.lifeservice.note.service.NoteCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class NoteCommentController {

    private final NoteCommentService noteCommentService;

    public NoteCommentController(NoteCommentService noteCommentService) {
        this.noteCommentService = noteCommentService;
    }

    @GetMapping("/api/v1/notes/{noteId}/comments")
    public ApiResponse<PageResponse<NoteCommentResponse>> pageNoteComments(
            @PathVariable @Min(1) Long noteId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        Page<NoteCommentResponse> page = noteCommentService.pageNoteComments(noteId, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }

    @PostMapping("/api/v1/users/me/notes/{noteId}/comments")
    @RateLimiter(
            key = "life:rate:note:comment:",
            window = 60,
            limit = 20,
            message = "Commenting is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<NoteCommentResponse> createMyNoteComment(
            @PathVariable @Min(1) Long noteId,
            @Valid @RequestBody NoteCommentCreateRequest request) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteCommentService.createCurrentUserComment(userId, noteId, request));
    }

    @DeleteMapping("/api/v1/users/me/note-comments/{commentId}")
    @RateLimiter(
            key = "life:rate:note:comment:",
            window = 60,
            limit = 30,
            message = "Comment operation is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<Void> deleteMyNoteComment(@PathVariable @Min(1) Long commentId) {
        Long userId = UserContext.requiredUserId();
        noteCommentService.deleteCurrentUserComment(userId, commentId);
        return ApiResponse.ok();
    }
}
