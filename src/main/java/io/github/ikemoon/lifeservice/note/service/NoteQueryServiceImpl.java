package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.mapper.MerchantNoteMapper;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
import org.springframework.stereotype.Service;

@Service
public class NoteQueryServiceImpl implements NoteQueryService {

    private static final int STATUS_VISIBLE = 1;

    private final MerchantNoteMapper noteMapper;
    private final NoteResponseAssembler responseAssembler;

    public NoteQueryServiceImpl(
            MerchantNoteMapper noteMapper,
            NoteResponseAssembler responseAssembler) {
        this.noteMapper = noteMapper;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public Page<NoteCardResponse> pagePublicNotes(long pageNo, long pageSize) {
        LambdaQueryWrapper<MerchantNote> wrapper = visibleWrapper()
                .orderByDesc(MerchantNote::getCreatedAt)
                .orderByDesc(MerchantNote::getId);
        return responseAssembler.toCardPage(noteMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    @Override
    public Page<NoteCardResponse> pageMerchantNotes(Long merchantId, long pageNo, long pageSize) {
        LambdaQueryWrapper<MerchantNote> wrapper = visibleWrapper()
                .eq(MerchantNote::getMerchantId, merchantId)
                .orderByDesc(MerchantNote::getCreatedAt)
                .orderByDesc(MerchantNote::getId);
        return responseAssembler.toCardPage(noteMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    @Override
    public Page<NoteCardResponse> pageCurrentUserNotes(Long userId, long pageNo, long pageSize) {
        LambdaQueryWrapper<MerchantNote> wrapper = visibleWrapper()
                .eq(MerchantNote::getUserId, userId)
                .orderByDesc(MerchantNote::getCreatedAt)
                .orderByDesc(MerchantNote::getId);
        return responseAssembler.toCardPage(noteMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    @Override
    public NoteDetailResponse getVisibleNote(Long noteId) {
        MerchantNote note = noteMapper.selectOne(visibleWrapper().eq(MerchantNote::getId, noteId));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
        return responseAssembler.toDetail(note);
    }

    private LambdaQueryWrapper<MerchantNote> visibleWrapper() {
        return new LambdaQueryWrapper<MerchantNote>()
                .eq(MerchantNote::getStatus, STATUS_VISIBLE);
    }

}
