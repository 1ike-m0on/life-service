package io.github.ikemoon.lifeservice.note.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteCommentCreateRequest(
        @Min(1) Long parentId,
        @NotBlank @Size(max = 500) String content) {
}
