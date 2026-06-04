package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteFavoriteResponse;

public interface NoteFavoriteService {

    NoteFavoriteResponse getCurrentUserFavorite(Long userId, Long noteId);

    NoteFavoriteResponse favoriteCurrentUserNote(Long userId, Long noteId);

    NoteFavoriteResponse cancelCurrentUserFavorite(Long userId, Long noteId);

    Page<NoteCardResponse> pageCurrentUserFavoriteNotes(Long userId, long pageNo, long pageSize);
}
