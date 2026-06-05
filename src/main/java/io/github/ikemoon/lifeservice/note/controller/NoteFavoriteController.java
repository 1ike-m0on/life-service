package io.github.ikemoon.lifeservice.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitFailureStrategy;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitType;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteFavoriteResponse;
import io.github.ikemoon.lifeservice.note.service.NoteFavoriteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me")
public class NoteFavoriteController {

    private final NoteFavoriteService noteFavoriteService;

    public NoteFavoriteController(NoteFavoriteService noteFavoriteService) {
        this.noteFavoriteService = noteFavoriteService;
    }

    @GetMapping("/favorite-notes")
    public ApiResponse<PageResponse<NoteCardResponse>> pageMyFavoriteNotes(
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        Long userId = UserContext.requiredUserId();
        Page<NoteCardResponse> page = noteFavoriteService.pageCurrentUserFavoriteNotes(userId, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }

    @PostMapping("/notes/{noteId}/favorite")
    @RateLimiter(
            key = "life:rate:note:favorite:",
            window = 10,
            limit = 20,
            message = "Favorite operation is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<NoteFavoriteResponse> favoriteMyNote(@PathVariable @Min(1) Long noteId) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteFavoriteService.favoriteCurrentUserNote(userId, noteId));
    }

    @GetMapping("/notes/{noteId}/favorite")
    public ApiResponse<NoteFavoriteResponse> getMyNoteFavorite(@PathVariable @Min(1) Long noteId) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteFavoriteService.getCurrentUserFavorite(userId, noteId));
    }

    @DeleteMapping("/notes/{noteId}/favorite")
    @RateLimiter(
            key = "life:rate:note:favorite:",
            window = 10,
            limit = 20,
            message = "Favorite operation is too frequent, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<NoteFavoriteResponse> cancelMyNoteFavorite(@PathVariable @Min(1) Long noteId) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(noteFavoriteService.cancelCurrentUserFavorite(userId, noteId));
    }
}
