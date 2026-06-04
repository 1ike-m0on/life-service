package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;

public interface NoteQueryService {

    Page<NoteCardResponse> pagePublicNotes(long pageNo, long pageSize);

    Page<NoteCardResponse> pageMerchantNotes(Long merchantId, long pageNo, long pageSize);

    Page<NoteCardResponse> pageCurrentUserNotes(Long userId, long pageNo, long pageSize);

    NoteDetailResponse getVisibleNote(Long noteId);
}
