package com.ruoyi.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.health.domain.HealthPrompt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 系统提示词的 MyBatis-Plus 数据访问接口。 */
public interface HealthPromptMapper extends BaseMapper<HealthPrompt> {

    /** 用途：插入或更新固定编号的系统提示词；参数：提示词内容；返回值：写入行数。 */
    @Insert("insert into health_prompt (id, content) values (1, #{content}) "
        + "on duplicate key update content = values(content)")
    int save(@Param("content") String content);
}
