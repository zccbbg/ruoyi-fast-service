package com.ruoyi.health.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 按账号和成员保存的会话增量摘要。 */
@Data
@TableName("health_chat_summary")
public class HealthChatSummary {
    private Long userId;
    private String member;
    private String conversationId;
    private Long lastMessageId;
    private String content;
}
