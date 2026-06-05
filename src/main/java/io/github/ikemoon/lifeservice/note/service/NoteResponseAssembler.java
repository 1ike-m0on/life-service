package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.util.ImagePathParser;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.entity.NoteComment;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
import io.github.ikemoon.lifeservice.user.entity.User;
import io.github.ikemoon.lifeservice.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NoteResponseAssembler {

    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;

    public NoteResponseAssembler(MerchantMapper merchantMapper, UserMapper userMapper) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
    }

    public Page<NoteCardResponse> toCardPage(Page<MerchantNote> page) {
        Page<NoteCardResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(toCards(page.getRecords()));
        return result;
    }

    public List<NoteCardResponse> toCards(List<MerchantNote> notes) {
        Map<Long, Merchant> merchants = selectMerchants(notes.stream()
                .map(MerchantNote::getMerchantId)
                .collect(Collectors.toSet()));
        Map<Long, User> users = selectUsers(notes.stream()
                .map(MerchantNote::getUserId)
                .collect(Collectors.toSet()));
        return notes.stream()
                .map(note -> toCard(note, merchants.get(note.getMerchantId()), users.get(note.getUserId())))
                .toList();
    }

    public NoteDetailResponse toDetail(MerchantNote note) {
        Map<Long, Merchant> merchants = selectMerchants(List.of(note.getMerchantId()));
        Map<Long, User> users = selectUsers(List.of(note.getUserId()));
        Merchant merchant = merchants.get(note.getMerchantId());
        User user = users.get(note.getUserId());
        return new NoteDetailResponse(
                note.getId(),
                note.getMerchantId(),
                merchant == null ? null : merchant.getName(),
                note.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                note.getTitle(),
                note.getContent(),
                note.getRating(),
                ImagePathParser.split(note.getImages()),
                zeroIfNull(note.getLikeCount()),
                zeroIfNull(note.getCommentCount()),
                zeroIfNull(note.getFavoriteCount()),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    public Page<NoteCommentResponse> toCommentPage(Page<NoteComment> page) {
        Map<Long, User> users = selectUsers(page.getRecords().stream()
                .map(NoteComment::getUserId)
                .collect(Collectors.toSet()));
        Page<NoteCommentResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(comment -> toComment(comment, users.get(comment.getUserId())))
                .toList());
        return result;
    }

    public NoteCommentResponse toComment(NoteComment comment) {
        User user = selectUsers(List.of(comment.getUserId())).get(comment.getUserId());
        return toComment(comment, user);
    }

    private NoteCardResponse toCard(MerchantNote note, Merchant merchant, User user) {
        return new NoteCardResponse(
                note.getId(),
                note.getMerchantId(),
                merchant == null ? null : merchant.getName(),
                note.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                note.getTitle(),
                note.getContent(),
                note.getRating(),
                ImagePathParser.split(note.getImages()),
                zeroIfNull(note.getLikeCount()),
                zeroIfNull(note.getCommentCount()),
                zeroIfNull(note.getFavoriteCount()),
                note.getCreatedAt());
    }

    private NoteCommentResponse toComment(NoteComment comment, User user) {
        return new NoteCommentResponse(
                comment.getId(),
                comment.getNoteId(),
                comment.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                comment.getParentId(),
                comment.getContent(),
                comment.getCreatedAt());
    }

    private Map<Long, Merchant> selectMerchants(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return merchantMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    private Map<Long, User> selectUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
