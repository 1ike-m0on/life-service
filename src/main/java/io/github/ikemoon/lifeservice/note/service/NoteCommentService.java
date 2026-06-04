package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.note.request.NoteCommentCreateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;

public interface NoteCommentService {

    Page<NoteCommentResponse> pageNoteComments(Long noteId, long pageNo, long pageSize);

    NoteCommentResponse createCurrentUserComment(Long userId, Long noteId, NoteCommentCreateRequest request);

    void deleteCurrentUserComment(Long userId, Long commentId);
}
