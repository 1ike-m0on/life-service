package io.github.ikemoon.lifeservice.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ls_note_favorite")
public class NoteFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long noteId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
