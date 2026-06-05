package io.github.ikemoon.lifeservice.note.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteUpdateRequest(
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 2000) String content,
        @Min(1) @Max(5) Integer rating,
        @Size(max = 9) List<@NotBlank @Size(max = 255) String> images) {
}
