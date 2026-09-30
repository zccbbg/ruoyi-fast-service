package com.ruoyi.health.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据库中加密保存的模型配置。 */
@Data
@TableName("health_model")
public class HealthModel {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String provider;
    private String purpose;
    private String modelId;
    private String encryptedKey;
    @TableField("is_default")
    private Boolean defaultFlag;
    private LocalDateTime createdAt;
}
