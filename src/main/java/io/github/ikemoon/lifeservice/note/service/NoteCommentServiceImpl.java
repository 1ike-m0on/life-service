package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.entity.NoteComment;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.mapper.NoteCommentMapper;
import io.github.ikemoon.lifeservice.note.request.NoteCommentCreateRequest;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class NoteCommentServiceImpl implements NoteCommentService {

    private static final int STATUS_DELETED = 0;
    private static final int STATUS_VISIBLE = 1;

    private final NoteCommentMapper commentMapper;
    private final MerchantNoteMapper noteMapper;
    private final NoteResponseAssembler responseAssembler;

    public NoteCommentServiceImpl(
            NoteCommentMapper commentMapper,
            MerchantNoteMapper noteMapper,
            NoteResponseAssembler responseAssembler) {
        this.commentMapper = commentMapper;
        this.noteMapper = noteMapper;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public Page<NoteCommentResponse> pageNoteComments(Long noteId, long pageNo, long pageSize) {
        assertVisibleNote(noteId);
        Page<NoteComment> page = commentMapper.selectPage(new Page<>(pageNo, pageSize), visibleCommentWrapper()
                .eq(NoteComment::getNoteId, noteId)
                .orderByAsc(NoteComment::getCreatedAt)
                .orderByAsc(NoteComment::getId));
        return responseAssembler.toCommentPage(page);
    }

    @Override
    @Transactional
    public NoteCommentResponse createCurrentUserComment(Long userId, Long noteId, NoteCommentCreateRequest request) {
        assertVisibleNote(noteId);
        Long parentId = request.parentId();
        if (parentId != null) {
            assertVisibleParentComment(noteId, parentId);
        }

        LocalDateTime now = LocalDateTime.now();
        NoteComment comment = new NoteComment();
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(requiredContent(request.content()));
        comment.setStatus(STATUS_VISIBLE);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        commentMapper.insert(comment);
        incrementCommentCount(noteId);
        return responseAssembler.toComment(comment);
    }

    @Override
    @Transactional
    public void deleteCurrentUserComment(Long userId, Long commentId) {
        NoteComment comment = commentMapper.selectOne(visibleCommentWrapper()
                .eq(NoteComment::getId, commentId)
                .eq(NoteComment::getUserId, userId));
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Comment not found");
        }
        comment.setStatus(STATUS_DELETED);
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        decrementCommentCount(comment.getNoteId());
    }

    private void incrementCommentCount(Long noteId) {
        noteMapper.update(null, new LambdaUpdateWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .setSql("comment_count = comment_count + 1"));
    }

    private void decrementCommentCount(Long noteId) {
        noteMapper.update(null, new LambdaUpdateWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .setSql("comment_count = greatest(comment_count - 1, 0)"));
    }

    private void assertVisibleNote(Long noteId) {
        MerchantNote note = noteMapper.selectOne(new LambdaQueryWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .eq(MerchantNote::getStatus, STATUS_VISIBLE));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
    }

    private void assertVisibleParentComment(Long noteId, Long parentId) {
        NoteComment parent = commentMapper.selectOne(visibleCommentWrapper()
                .eq(NoteComment::getId, parentId)
                .eq(NoteComment::getNoteId, noteId));
        if (parent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Parent comment not found");
        }
    }

    private LambdaQueryWrapper<NoteComment> visibleCommentWrapper() {
        return new LambdaQueryWrapper<NoteComment>()
                .eq(NoteComment::getStatus, STATUS_VISIBLE);
    }

    private String requiredContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Comment content is required");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Comment content is too long");
        }
        return trimmed;
    }
}
