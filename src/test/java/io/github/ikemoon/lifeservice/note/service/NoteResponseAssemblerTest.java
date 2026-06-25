package io.github.ikemoon.lifeservice.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import io.github.ikemoon.lifeservice.note.entity.MerchantNote;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
import io.github.ikemoon.lifeservice.user.entity.User;
import io.github.ikemoon.lifeservice.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class NoteResponseAssemblerTest {

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private UserMapper userMapper;

    private NoteResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new NoteResponseAssembler(merchantMapper, userMapper);
    }

    @Test
    void toCardPageMapsPageMetadataRelatedFieldsCountsAndImages() {
        MerchantNote richNote = note(1L, 10L, 20L);
        richNote.setImages(" /assets/notes/one.jpg, ,/assets/notes/two.jpg ");
        richNote.setLikeCount(null);
        richNote.setCommentCount(null);
        richNote.setFavoriteCount(null);
        MerchantNote missingLookupNote = note(2L, 11L, 21L);
        missingLookupNote.setImages(null);
        missingLookupNote.setLikeCount(3);
        missingLookupNote.setCommentCount(4);
        missingLookupNote.setFavoriteCount(5);
        Page<MerchantNote> page = new Page<>(2, 6);
        page.setTotal(12);
        page.setRecords(List.of(richNote, missingLookupNote));
        when(merchantMapper.selectBatchIds(anyCollection())).thenReturn(List.of(merchant(10L)));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user(20L)));

        Page<NoteCardResponse> result = assembler.toCardPage(page);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(6);
        assertThat(result.getTotal()).isEqualTo(12);
        assertThat(result.getRecords()).hasSize(2);
        NoteCardResponse first = result.getRecords().getFirst();
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.merchantName()).isEqualTo("Merchant 10");
        assertThat(first.nickname()).isEqualTo("User 20");
        assertThat(first.avatarUrl()).isEqualTo("/assets/avatars/20.png");
        assertThat(first.images()).containsExactly("/assets/notes/one.jpg", "/assets/notes/two.jpg");
        assertThat(first.likeCount()).isZero();
        assertThat(first.commentCount()).isZero();
        assertThat(first.favoriteCount()).isZero();
        NoteCardResponse second = result.getRecords().get(1);
        assertThat(second.merchantName()).isNull();
        assertThat(second.nickname()).isNull();
        assertThat(second.avatarUrl()).isNull();
        assertThat(second.images()).isEmpty();
        assertThat(second.likeCount()).isEqualTo(3);
        assertThat(second.commentCount()).isEqualTo(4);
        assertThat(second.favoriteCount()).isEqualTo(5);
        assertBatchIds(merchantMapper, 10L, 11L);
        assertBatchIds(userMapper, 20L, 21L);
    }

    @Test
    void toCardPageKeepsEmptyPageWithoutRelatedLookups() {
        Page<MerchantNote> page = new Page<>(4, 9);
        page.setTotal(0);
        page.setRecords(List.of());

        Page<NoteCardResponse> result = assembler.toCardPage(page);

        assertThat(result.getCurrent()).isEqualTo(4);
        assertThat(result.getSize()).isEqualTo(9);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(merchantMapper, never()).selectBatchIds(anyCollection());
        verify(userMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    void toDetailMapsRelatedFieldsCountsImagesAndTimestamps() {
        MerchantNote note = note(9L, 30L, 40L);
        note.setImages("/assets/notes/detail-one.jpg,/assets/notes/detail-two.jpg");
        note.setLikeCount(6);
        note.setCommentCount(7);
        note.setFavoriteCount(8);
        when(merchantMapper.selectBatchIds(anyCollection())).thenReturn(List.of(merchant(30L)));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user(40L)));

        NoteDetailResponse result = assembler.toDetail(note);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.merchantId()).isEqualTo(30L);
        assertThat(result.merchantName()).isEqualTo("Merchant 30");
        assertThat(result.userId()).isEqualTo(40L);
        assertThat(result.nickname()).isEqualTo("User 40");
        assertThat(result.avatarUrl()).isEqualTo("/assets/avatars/40.png");
        assertThat(result.title()).isEqualTo("Note 9");
        assertThat(result.content()).isEqualTo("Content 9");
        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.images()).containsExactly("/assets/notes/detail-one.jpg", "/assets/notes/detail-two.jpg");
        assertThat(result.likeCount()).isEqualTo(6);
        assertThat(result.commentCount()).isEqualTo(7);
        assertThat(result.favoriteCount()).isEqualTo(8);
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 12, 9));
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 13, 9));
    }

    @Test
    void toDetailUsesNullRelatedFieldsZeroCountsAndEmptyImagesWhenDataIsMissing() {
        MerchantNote note = note(10L, 31L, 41L);
        note.setImages("  ,  ");
        note.setLikeCount(null);
        note.setCommentCount(null);
        note.setFavoriteCount(null);
        when(merchantMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        NoteDetailResponse result = assembler.toDetail(note);

        assertThat(result.merchantName()).isNull();
        assertThat(result.nickname()).isNull();
        assertThat(result.avatarUrl()).isNull();
        assertThat(result.images()).isEmpty();
        assertThat(result.likeCount()).isZero();
        assertThat(result.commentCount()).isZero();
        assertThat(result.favoriteCount()).isZero();
    }

    private static void assertBatchIds(MerchantMapper mapper, Long... ids) {
        ArgumentCaptor<Collection> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).selectBatchIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(List.of(ids));
    }

    private static void assertBatchIds(UserMapper mapper, Long... ids) {
        ArgumentCaptor<Collection> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).selectBatchIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(List.of(ids));
    }

    private static MerchantNote note(Long id, Long merchantId, Long userId) {
        MerchantNote note = new MerchantNote();
        note.setId(id);
        note.setMerchantId(merchantId);
        note.setUserId(userId);
        note.setTitle("Note " + id);
        note.setContent("Content " + id);
        note.setRating(5);
        note.setStatus(1);
        note.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, id.intValue()));
        note.setUpdatedAt(LocalDateTime.of(2026, 5, 20, 13, id.intValue()));
        return note;
    }

    private static Merchant merchant(Long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName("Merchant " + id);
        return merchant;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setNickname("User " + id);
        user.setAvatarUrl("/assets/avatars/" + id + ".png");
        return user;
    }
}
