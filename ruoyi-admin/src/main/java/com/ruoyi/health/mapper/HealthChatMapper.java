package com.ruoyi.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.health.domain.HealthChatMessage;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 健康资料会话的数据访问接口。 */
public interface HealthChatMapper extends BaseMapper<HealthChatMessage> {

    /** 用途：一次写入用户问题和助手回答；参数：会话、成员、问题、回答和来源；返回值：写入行数。 */
    @Insert("insert into health_chat_message(conversation_id,member,role,content,sources) values "
        + "(#{conversation},#{member},'user',#{question},null),"
        + "(#{conversation},#{member},'assistant',#{answer},#{sources})")
    int insertExchange(@Param("conversation") String conversation, @Param("member") String member,
                       @Param("question") String question, @Param("answer") String answer,
                       @Param("sources") String sources);

    /** 用途：查询成员最近会话的首条问题；参数：成员；返回值：按最近活动排序的消息列表。 */
    @Select("select m.* from health_chat_message m join "
        + "(select conversation_id,min(id) first_id,max(id) last_id from health_chat_message "
        + "where member=#{member} and role='user' group by conversation_id) c "
        + "on m.id=c.first_id order by c.last_id desc limit 30")
    List<HealthChatMessage> recentConversationTitles(@Param("member") String member);
}
