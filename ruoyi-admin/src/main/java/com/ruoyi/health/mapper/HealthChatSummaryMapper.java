package com.ruoyi.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.health.domain.HealthChatSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 会话摘要的数据访问接口。 */
public interface HealthChatSummaryMapper extends BaseMapper<HealthChatSummary> {

    /** 用途：保存摘要及其覆盖的最后一条消息编号；参数：账号、成员、会话、消息编号和摘要；返回值：写入行数。 */
    @Insert("insert into health_chat_summary(user_id,member,conversation_id,last_message_id,content) "
        + "values(#{userId},#{member},#{conversation},#{lastMessageId},#{content}) "
        + "on duplicate key update last_message_id=values(last_message_id),content=values(content)")
    int save(@Param("userId") Long userId, @Param("member") String member,
             @Param("conversation") String conversation, @Param("lastMessageId") Long lastMessageId,
             @Param("content") String content);
}
