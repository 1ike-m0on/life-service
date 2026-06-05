package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.entity.NoteFavorite;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.mapper.NoteFavoriteMapper;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteFavoriteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class NoteFavoriteServiceImplTest {

    @Mock
    private NoteFavoriteMapper favoriteMapper;

    @Mock
    private MerchantNoteMapper noteMapper;

    @Mock
    private NoteResponseAssembler responseAssembler;

    private NoteFavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteFavoriteServiceImpl(favoriteMapper, noteMapper, responseAssembler);
    }

    @Test
    void getCurrentUserFavoriteReturnsActiveState() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(visibleNote(4L));
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFavorite(1L, 10L, 4L));

        NoteFavoriteResponse result = service.getCurrentUserFavorite(10L, 4L);

        assertThat(result.noteId()).isEqualTo(4L);
        assertThat(result.favorited()).isTrue();
    }

    @Test
    void favoriteCurrentUserNoteInsertsNewFavorite() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(visibleNote(4L));
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        NoteFavoriteResponse result = service.favoriteCurrentUserNote(10L, 4L);

        ArgumentCaptor<NoteFavorite> favoriteCaptor = ArgumentCaptor.forClass(NoteFavorite.class);
        verify(favoriteMapper).insert(favoriteCaptor.capture());
        NoteFavorite favorite = favoriteCaptor.getValue();
        assertThat(favorite.getUserId()).isEqualTo(10L);
        assertThat(favorite.getNoteId()).isEqualTo(4L);
        assertThat(favorite.getStatus()).isEqualTo(1);
        assertThat(result.favorited()).isTrue();
    }

    @Test
    void favoriteCurrentUserNoteRejectsMissingNote() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.favoriteCurrentUserNote(10L, 4L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void cancelCurrentUserFavoriteSoftCancelsActiveFavorite() {
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFavorite(1L, 10L, 4L));

        NoteFavoriteResponse result = service.cancelCurrentUserFavorite(10L, 4L);

        ArgumentCaptor<NoteFavorite> favoriteCaptor = ArgumentCaptor.forClass(NoteFavorite.class);
        verify(favoriteMapper).updateById(favoriteCaptor.capture());
        assertThat(favoriteCaptor.getValue().getStatus()).isZero();
        assertThat(result.favorited()).isFalse();
    }

    @Test
    void pageCurrentUserFavoriteNotesKeepsFavoriteOrder() {
        Page<NoteFavorite> favoritePage = new Page<>(1, 10);
        favoritePage.setTotal(2);
        favoritePage.setRecords(List.of(activeFavorite(1L, 10L, 4L), activeFavorite(2L, 10L, 8L)));
        MerchantNote note4 = visibleNote(4L);
        MerchantNote note8 = visibleNote(8L);
        List<NoteCardResponse> cards = List.of(noteCard(4L), noteCard(8L));
        when(favoriteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(favoritePage);
        when(noteMapper.selectBatchIds(anyCollection())).thenReturn(List.of(note8, note4));
        when(responseAssembler.toCards(List.of(note4, note8))).thenReturn(cards);

        Page<NoteCardResponse> result = service.pageCurrentUserFavoriteNotes(10L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).containsExactlyElementsOf(cards);
    }

    private static MerchantNote visibleNote(Long id) {
        MerchantNote note = new MerchantNote();
        note.setId(id);
        note.setMerchantId(1L);
        note.setUserId(2001L);
        note.setTitle("Note " + id);
        note.setStatus(1);
        return note;
    }

    private static NoteFavorite activeFavorite(Long id, Long userId, Long noteId) {
        NoteFavorite favorite = new NoteFavorite();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setNoteId(noteId);
        favorite.setStatus(1);
        favorite.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 0).plusMinutes(id));
        return favorite;
    }

    private static NoteCardResponse noteCard(Long id) {
        return new NoteCardResponse(
                id,
                1L,
                "Moonlight Coffee",
                2001L,
                "Demo User 2001",
                null,
                "Note " + id,
                "Coffee is good",
                5,
                List.of(),
                0,
                0,
                0,
                LocalDateTime.of(2026, 5, 20, 12, 0));
    }
}
