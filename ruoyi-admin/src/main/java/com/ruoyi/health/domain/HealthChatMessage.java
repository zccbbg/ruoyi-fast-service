package com.ruoyi.health.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据库中保存的一条健康资料问答消息。 */
@Data
@TableName("health_chat_message")
public class HealthChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String member;
    private String role;
    private String content;
    private String sources;
    private LocalDateTime createdAt;
}
