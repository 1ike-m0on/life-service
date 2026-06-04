package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.request.NoteCreateRequest;
import io.github.ikemoon.lifeservice.note.request.NoteUpdateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class NoteCommandServiceImplTest {

    @Mock
    private MerchantNoteMapper noteMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private NoteQueryService noteQueryService;

    private NoteCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteCommandServiceImpl(noteMapper, merchantMapper, noteQueryService);
    }

    @Test
    void createCurrentUserNoteInsertsVisibleNote() {
        when(merchantMapper.selectById(1L)).thenReturn(enabledMerchant(1L));
        when(noteMapper.insert(any(MerchantNote.class))).thenAnswer(invocation -> {
            MerchantNote note = invocation.getArgument(0);
            note.setId(99L);
            return 1;
        });
        when(noteQueryService.getVisibleNote(99L)).thenReturn(noteDetail(99L, "New note"));

        NoteDetailResponse result = service.createCurrentUserNote(10L, new NoteCreateRequest(
                1L,
                "  New note  ",
                "  Coffee is good  ",
                5,
                List.of("  /assets/notes/new.jpg  ")));

        ArgumentCaptor<MerchantNote> noteCaptor = ArgumentCaptor.forClass(MerchantNote.class);
        verify(noteMapper).insert(noteCaptor.capture());
        MerchantNote inserted = noteCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(10L);
        assertThat(inserted.getMerchantId()).isEqualTo(1L);
        assertThat(inserted.getTitle()).isEqualTo("New note");
        assertThat(inserted.getContent()).isEqualTo("Coffee is good");
        assertThat(inserted.getRating()).isEqualTo(5);
        assertThat(inserted.getImages()).isEqualTo("/assets/notes/new.jpg");
        assertThat(inserted.getLikeCount()).isZero();
        assertThat(inserted.getCommentCount()).isZero();
        assertThat(inserted.getFavoriteCount()).isZero();
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(99L);
    }

    @Test
    void createCurrentUserNoteRejectsMissingMerchant() {
        when(merchantMapper.selectById(1L)).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.createCurrentUserNote(10L, new NoteCreateRequest(
                        1L,
                        "New note",
                        "Coffee is good",
                        5,
                        List.of())))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void updateCurrentUserNoteUpdatesOwnedVisibleNote() {
        MerchantNote existing = visibleNote(99L, 10L);
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(noteQueryService.getVisibleNote(99L)).thenReturn(noteDetail(99L, "Updated note"));

        NoteDetailResponse result = service.updateCurrentUserNote(10L, 99L, new NoteUpdateRequest(
                "Updated note",
                "Coffee is still good",
                4,
                List.of("/assets/notes/updated.jpg")));

        ArgumentCaptor<MerchantNote> noteCaptor = ArgumentCaptor.forClass(MerchantNote.class);
        verify(noteMapper).updateById(noteCaptor.capture());
        MerchantNote updated = noteCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(99L);
        assertThat(updated.getTitle()).isEqualTo("Updated note");
        assertThat(updated.getContent()).isEqualTo("Coffee is still good");
        assertThat(updated.getRating()).isEqualTo(4);
        assertThat(updated.getImages()).isEqualTo("/assets/notes/updated.jpg");
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(result.title()).isEqualTo("Updated note");
    }

    @Test
    void updateCurrentUserNoteRejectsOtherUsersNote() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.updateCurrentUserNote(10L, 99L, new NoteUpdateRequest(
                        "Updated note",
                        "Coffee is still good",
                        4,
                        List.of())))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void deleteCurrentUserNoteSoftDeletesOwnedVisibleNote() {
        MerchantNote existing = visibleNote(99L, 10L);
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.deleteCurrentUserNote(10L, 99L);

        ArgumentCaptor<MerchantNote> noteCaptor = ArgumentCaptor.forClass(MerchantNote.class);
        verify(noteMapper).updateById(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getStatus()).isZero();
    }

    private static Merchant enabledMerchant(Long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setStatus(1);
        return merchant;
    }

    private static MerchantNote visibleNote(Long id, Long userId) {
        MerchantNote note = new MerchantNote();
        note.setId(id);
        note.setUserId(userId);
        note.setMerchantId(1L);
        note.setTitle("Old note");
        note.setContent("Old content");
        note.setRating(5);
        note.setStatus(1);
        return note;
    }

    private static NoteDetailResponse noteDetail(Long id, String title) {
        return new NoteDetailResponse(
                id,
                1L,
                "Moonlight Coffee",
                10L,
                "demo",
                null,
                title,
                "Coffee is good",
                5,
                List.of("/assets/notes/new.jpg"),
                0,
                0,
                0,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                LocalDateTime.of(2026, 5, 20, 12, 0));
    }
}
