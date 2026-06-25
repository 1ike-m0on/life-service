package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class NoteQueryServiceImplTest {

    @Mock
    private MerchantNoteMapper noteMapper;

    @Mock
    private NoteResponseAssembler responseAssembler;

    private NoteQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteQueryServiceImpl(noteMapper, responseAssembler);
    }

    @Test
    void pagePublicNotesReturnsAssembledFeedPage() {
        Page<MerchantNote> rawPage = notePage(3, 7, visibleNote(1L, 100L, 200L));
        Page<NoteCardResponse> responsePage = cardPage(3, 7, noteCard(1L));
        when(noteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);
        when(responseAssembler.toCardPage(rawPage)).thenReturn(responsePage);

        Page<NoteCardResponse> result = service.pagePublicNotes(3, 7);

        assertThat(result).isSameAs(responsePage);
        assertPageRequest(3, 7);
        verify(responseAssembler).toCardPage(rawPage);
    }

    @Test
    void pageMerchantNotesReturnsAssembledMerchantPage() {
        Page<MerchantNote> rawPage = notePage(2, 5, visibleNote(2L, 101L, 300L));
        Page<NoteCardResponse> responsePage = cardPage(2, 5, noteCard(2L));
        when(noteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);
        when(responseAssembler.toCardPage(rawPage)).thenReturn(responsePage);

        Page<NoteCardResponse> result = service.pageMerchantNotes(101L, 2, 5);

        assertThat(result).isSameAs(responsePage);
        assertPageRequest(2, 5);
        verify(responseAssembler).toCardPage(rawPage);
    }

    @Test
    void pageCurrentUserNotesReturnsAssembledUserPage() {
        Page<MerchantNote> rawPage = notePage(1, 12, visibleNote(3L, 102L, 400L));
        Page<NoteCardResponse> responsePage = cardPage(1, 12, noteCard(3L));
        when(noteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);
        when(responseAssembler.toCardPage(rawPage)).thenReturn(responsePage);

        Page<NoteCardResponse> result = service.pageCurrentUserNotes(400L, 1, 12);

        assertThat(result).isSameAs(responsePage);
        assertPageRequest(1, 12);
        verify(responseAssembler).toCardPage(rawPage);
    }

    @Test
    void getVisibleNoteReturnsAssembledDetail() {
        MerchantNote note = visibleNote(9L, 103L, 500L);
        NoteDetailResponse detail = noteDetail(9L);
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(note);
        when(responseAssembler.toDetail(note)).thenReturn(detail);

        NoteDetailResponse result = service.getVisibleNote(9L);

        assertThat(result).isSameAs(detail);
        verify(responseAssembler).toDetail(note);
    }

    @Test
    void getVisibleNoteRejectsMissingVisibleNote() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getVisibleNote(404L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(responseAssembler);
    }

    private void assertPageRequest(long current, long size) {
        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(noteMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(current);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(size);
    }

    private static Page<MerchantNote> notePage(long current, long size, MerchantNote note) {
        Page<MerchantNote> page = new Page<>(current, size);
        page.setTotal(1);
        page.setRecords(List.of(note));
        return page;
    }

    private static Page<NoteCardResponse> cardPage(long current, long size, NoteCardResponse card) {
        Page<NoteCardResponse> page = new Page<>(current, size);
        page.setTotal(1);
        page.setRecords(List.of(card));
        return page;
    }

    private static MerchantNote visibleNote(Long id, Long merchantId, Long userId) {
        MerchantNote note = new MerchantNote();
        note.setId(id);
        note.setMerchantId(merchantId);
        note.setUserId(userId);
        note.setTitle("Note " + id);
        note.setStatus(1);
        note.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 0));
        note.setUpdatedAt(LocalDateTime.of(2026, 5, 20, 12, 30));
        return note;
    }

    private static NoteCardResponse noteCard(Long id) {
        return new NoteCardResponse(
                id,
                101L,
                "Moonlight Coffee",
                400L,
                "demo",
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

    private static NoteDetailResponse noteDetail(Long id) {
        return new NoteDetailResponse(
                id,
                103L,
                "Moonlight Coffee",
                500L,
                "demo",
                null,
                "Note " + id,
                "Coffee is good",
                5,
                List.of(),
                0,
                0,
                0,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                LocalDateTime.of(2026, 5, 20, 12, 30));
    }
}
