package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.entity.NoteComment;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.mapper.NoteCommentMapper;
import io.github.ikemoon.lifeservice.note.request.NoteCommentCreateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;
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
class NoteCommentServiceImplTest {

    @Mock
    private NoteCommentMapper commentMapper;

    @Mock
    private MerchantNoteMapper noteMapper;

    @Mock
    private NoteResponseAssembler responseAssembler;

    private NoteCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteCommentServiceImpl(commentMapper, noteMapper, responseAssembler);
    }

    @Test
    void pageNoteCommentsReturnsVisibleCommentPage() {
        Page<NoteComment> rawPage = new Page<>(1, 10);
        rawPage.setTotal(1);
        rawPage.setRecords(List.of(comment(7L, 10L)));
        Page<NoteCommentResponse> responsePage = new Page<>(1, 10);
        responsePage.setTotal(1);
        responsePage.setRecords(List.of(commentResponse(7L)));
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(visibleNote(1L));
        when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);
        when(responseAssembler.toCommentPage(rawPage)).thenReturn(responsePage);

        Page<NoteCommentResponse> result = service.pageNoteComments(1L, 1, 10);

        assertThat(result.getRecords()).containsExactly(commentResponse(7L));
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void createCurrentUserCommentInsertsTrimmedComment() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(visibleNote(1L));
        when(commentMapper.insert(any(NoteComment.class))).thenAnswer(invocation -> {
            NoteComment comment = invocation.getArgument(0);
            comment.setId(7L);
            return 1;
        });
        when(responseAssembler.toComment(any(NoteComment.class))).thenReturn(commentResponse(7L));

        NoteCommentResponse result = service.createCurrentUserComment(10L, 1L,
                new NoteCommentCreateRequest(null, "  Same here  "));

        ArgumentCaptor<NoteComment> commentCaptor = ArgumentCaptor.forClass(NoteComment.class);
        verify(commentMapper).insert(commentCaptor.capture());
        NoteComment inserted = commentCaptor.getValue();
        assertThat(inserted.getNoteId()).isEqualTo(1L);
        assertThat(inserted.getUserId()).isEqualTo(10L);
        assertThat(inserted.getContent()).isEqualTo("Same here");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(7L);
    }

    @Test
    void createCurrentUserCommentRejectsMissingNote() {
        when(noteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.createCurrentUserComment(10L, 1L,
                        new NoteCommentCreateRequest(null, "Same here")))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void deleteCurrentUserCommentSoftDeletesOwnComment() {
        when(commentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(comment(7L, 10L));

        service.deleteCurrentUserComment(10L, 7L);

        ArgumentCaptor<NoteComment> commentCaptor = ArgumentCaptor.forClass(NoteComment.class);
        verify(commentMapper).updateById(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getStatus()).isZero();
    }

    private static MerchantNote visibleNote(Long id) {
        MerchantNote note = new MerchantNote();
        note.setId(id);
        note.setStatus(1);
        return note;
    }

    private static NoteComment comment(Long id, Long userId) {
        NoteComment comment = new NoteComment();
        comment.setId(id);
        comment.setNoteId(1L);
        comment.setUserId(userId);
        comment.setContent("Same here");
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 30));
        return comment;
    }

    private static NoteCommentResponse commentResponse(Long id) {
        return new NoteCommentResponse(
                id,
                1L,
                10L,
                "demo",
                null,
                null,
                "Same here",
                LocalDateTime.of(2026, 5, 20, 12, 30));
    }
}
