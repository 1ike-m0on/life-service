package io.github.ikemoon.lifeservice.note.response;

import java.time.LocalDateTime;

public record NoteCommentResponse(
        Long id,
        Long noteId,
        Long userId,
        String nickname,
        String avatarUrl,
        Long parentId,
        String content,
        LocalDateTime createdAt) {
}
