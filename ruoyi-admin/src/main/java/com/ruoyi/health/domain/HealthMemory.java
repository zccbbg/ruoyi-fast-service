package com.ruoyi.health.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 从用户聊天自述提取的跨会话记忆，不作为健康档案证据。 */
@Data
@TableName("health_memory")
public class HealthMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String member;
    private String kind;
    private String content;
    private String sourceConversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
