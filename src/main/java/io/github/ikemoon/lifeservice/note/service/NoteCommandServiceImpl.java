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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NoteCommandServiceImpl implements NoteCommandService {

    private static final int STATUS_DELETED = 0;
    private static final int STATUS_VISIBLE = 1;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_IMAGE_COUNT = 9;
    private static final int MAX_IMAGE_PATH_LENGTH = 255;
    private static final int MAX_IMAGES_VALUE_LENGTH = 2048;

    private final MerchantNoteMapper noteMapper;
    private final MerchantMapper merchantMapper;
    private final NoteQueryService noteQueryService;

    public NoteCommandServiceImpl(
            MerchantNoteMapper noteMapper,
            MerchantMapper merchantMapper,
            NoteQueryService noteQueryService) {
        this.noteMapper = noteMapper;
        this.merchantMapper = merchantMapper;
        this.noteQueryService = noteQueryService;
    }

    @Override
    @Transactional
    public NoteDetailResponse createCurrentUserNote(Long userId, NoteCreateRequest request) {
        assertEnabledMerchant(request.merchantId());

        LocalDateTime now = LocalDateTime.now();
        MerchantNote note = new MerchantNote();
        note.setUserId(userId);
        note.setMerchantId(request.merchantId());
        note.setTitle(requiredText(request.title(), MAX_TITLE_LENGTH, "Title"));
        note.setContent(requiredText(request.content(), MAX_CONTENT_LENGTH, "Content"));
        note.setRating(optionalRating(request.rating()));
        note.setImages(normalizeImages(request.images()));
        note.setLikeCount(0);
        note.setCommentCount(0);
        note.setFavoriteCount(0);
        note.setStatus(STATUS_VISIBLE);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);

        noteMapper.insert(note);
        return noteQueryService.getVisibleNote(note.getId());
    }

    @Override
    @Transactional
    public NoteDetailResponse updateCurrentUserNote(Long userId, Long noteId, NoteUpdateRequest request) {
        MerchantNote note = selectVisibleOwnedNote(userId, noteId);
        note.setTitle(requiredText(request.title(), MAX_TITLE_LENGTH, "Title"));
        note.setContent(requiredText(request.content(), MAX_CONTENT_LENGTH, "Content"));
        note.setRating(optionalRating(request.rating()));
        note.setImages(normalizeImages(request.images()));
        note.setUpdatedAt(LocalDateTime.now());

        noteMapper.updateById(note);
        return noteQueryService.getVisibleNote(noteId);
    }

    @Override
    @Transactional
    public void deleteCurrentUserNote(Long userId, Long noteId) {
        MerchantNote note = selectVisibleOwnedNote(userId, noteId);
        note.setStatus(STATUS_DELETED);
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
    }

    private void assertEnabledMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Merchant not found");
        }
    }

    private MerchantNote selectVisibleOwnedNote(Long userId, Long noteId) {
        MerchantNote note = noteMapper.selectOne(new LambdaQueryWrapper<MerchantNote>()
                .eq(MerchantNote::getId, noteId)
                .eq(MerchantNote::getUserId, userId)
                .eq(MerchantNote::getStatus, STATUS_VISIBLE));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Note not found");
        }
        return note;
    }

    private String requiredText(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " is too long");
        }
        return trimmed;
    }

    private Integer optionalRating(Integer rating) {
        if (rating == null) {
            return null;
        }
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        return rating;
    }

    private String normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Too many images");
        }

        List<String> normalized = new ArrayList<>(images.size());
        for (String image : images) {
            if (!StringUtils.hasText(image)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Image path is required");
            }
            String trimmed = image.trim();
            if (trimmed.length() > MAX_IMAGE_PATH_LENGTH) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Image path is too long");
            }
            normalized.add(trimmed);
        }

        String joined = String.join(",", normalized);
        if (joined.length() > MAX_IMAGES_VALUE_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Images are too long");
        }
        return joined;
    }
}
