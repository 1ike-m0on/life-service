package io.github.ikemoon.lifeservice.note.service;

import io.github.ikemoon.lifeservice.note.request.NoteCreateRequest;
import io.github.ikemoon.lifeservice.note.request.NoteUpdateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;

public interface NoteCommandService {

    NoteDetailResponse createCurrentUserNote(Long userId, NoteCreateRequest request);

    NoteDetailResponse updateCurrentUserNote(Long userId, Long noteId, NoteUpdateRequest request);

    void deleteCurrentUserNote(Long userId, Long noteId);
}
