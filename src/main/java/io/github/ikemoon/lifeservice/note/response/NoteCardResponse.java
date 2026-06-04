package io.github.ikemoon.lifeservice.note.response;

import java.time.LocalDateTime;
import java.util.List;

public record NoteCardResponse(
        Long id,
        Long merchantId,
        String merchantName,
        Long userId,
        String nickname,
        String avatarUrl,
        String title,
        String content,
        Integer rating,
        List<String> images,
        Integer likeCount,
        Integer commentCount,
        Integer favoriteCount,
        LocalDateTime createdAt) {
}
