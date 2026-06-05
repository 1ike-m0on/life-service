package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.entity.NoteFavorite;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.mapper.NoteFavoriteMapper;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteFavoriteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteFavoriteServiceImpl implements NoteFavoriteService {

    private static final int STATUS_CANCELED = 0;
    private static final int STATUS_VISIBLE = 1;

    private final NoteFavoriteMapper favoriteMapper;
    private final MerchantNoteMapper noteMapper;
    private final NoteResponseAssembler responseAssembler;

    public NoteFavoriteServiceImpl(
            NoteFavoriteMapper favoriteMapper,
            MerchantNoteMapper noteMapper,
            NoteResponseAssembler responseAssembler) {
        this.favoriteMapper = favoriteMapper;
        this.noteMapper = noteMapper;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public NoteFavoriteResponse getCurrentUserFavorite(Long userId, Long noteId) {
        assertVisibleNote(noteId);
        NoteFavorite favorite = selectUserFavorite(userId, noteId);
        boolean favorited = favorite != null && Integer.valueOf(STATUS_VISIBLE).equals(favorite.getStatus());
        return new NoteFavoriteResponse(noteId, favorited);
    }

    @Override
    @Transactional
    public NoteFavoriteResponse favoriteCurrentUserNote(Long userId, Long noteId) {
        assertVisibleNote(noteId);
        NoteFavorite favorite = selectUserFavorite(userId, noteId);
        LocalDateTime now = LocalDateTime.now();
        boolean activated = false;
        if (favorite == null) {
            favorite = new NoteFavorite();
            favorite.setUserId(userId);
            favorite.setNoteId(noteId);
            favorite.setStatus(STATUS_VISIBLE);
            favorite.setCreatedAt(now);
            favorite.setUpdatedAt(now);
            favoriteMapper.insert(favorite);
            activated = true;
        } else if (!Integer.valueOf(STATUS_VISIBLE).equals(favorite.getStatus())) {
            favorite.setStatus(STATUS_VISIBLE);
            favorite.setUpdatedAt(now);
            favoriteMapper.updateById(favorite);
            activated = true;
        }
        if (activated) {
            incrementFavoriteCount(noteId);
        }
        return new NoteFavoriteResponse(noteId, true);
    }

    @Override
    @Transactional
    public NoteFavoriteResponse cancelCurrentUserFavorite(Long userId, Long noteId) {
        NoteFavorite favorite = selectUserFavorite(userId, noteId);
        if (favorite != null && Integer.valueOf(STATUS_VISIBLE).equals(favorite.getStatus())) {
            favorite.setStatus(STATUS_CANCELED);
            favorite.setUpdatedAt(LocalDateTime.now());
            favoriteMapper.updateById(favorite);
            decrementFavoriteCount(noteId);
        }
        return new NoteFavoriteResponse(noteId, false);
    }

    @Override
    public Page<NoteCardResponse> pageCurrentUserFavoriteNotes(Long userId, long pageNo, long pageSize) {
        Page<NoteFavorite> favoritePage = favoriteMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<NoteFavorite>()
                        .eq(NoteFavorite::getUserId, userId)
                        .eq(NoteFavorite::getStatus, STATUS_VISIBLE)
                        .orderByDesc(NoteFavorite::getCreatedAt)
                        .orderByDesc(NoteFavorite::getId));
        List<Long> noteIds = favoritePage.getRecords().stream()
                .map(NoteFavorite::getNoteId)
                .toList();
        Page<NoteCardResponse> result = new Page<>(pageNo, pageSize, favoritePage.getTotal());
        if (noteIds.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        Map<Long, MerchantNote> notes = noteMapper.selectBatchIds(noteIds).stream()
                .filter(note -> Integer.valueOf(STATUS_VISIBLE).equals(note.getStatus()))
                .collect(Collectors.toMap(MerchantNote::getId, Function.identity()));
        List<MerchantNote> orderedNotes = noteIds.stream()
                .map(notes::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        result.setRecords(responseAssembler.toCards(orderedNotes));
        return result;
    }

    private NoteFavorite selectUserFavorite(Long userId, Long noteId) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<NoteFavorite>()
                .eq(NoteFavorite::getUserId, userId)
                .eq(NoteFavorite::getNoteId, noteId));
    }

    private void incrementFavoriteCount(Long noteId) {
        noteMapper.update(null, new LambdaUpdateWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .setSql("favorite_count = favorite_count + 1"));
    }

    private void decrementFavoriteCount(Long noteId) {
        noteMapper.update(null, new LambdaUpdateWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .setSql("favorite_count = greatest(favorite_count - 1, 0)"));
    }

    private void assertVisibleNote(Long noteId) {
        MerchantNote note = noteMapper.selectOne(new LambdaQueryWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .eq(MerchantNote::getStatus, STATUS_VISIBLE));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
    }
}
