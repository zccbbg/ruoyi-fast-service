package com.ruoyi.health.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 数据库中固定编号的问答系统提示词。 */
@Data
@TableName("health_prompt")
public class HealthPrompt {
    @TableId(type = IdType.INPUT)
    private Integer id;
    private String content;
}
