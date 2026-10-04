package com.ruoyi.health;

import com.ruoyi.health.domain.HealthPrompt;
import com.ruoyi.health.mapper.HealthPromptMapper;
import org.springframework.stereotype.Service;

@Service
public class HealthPromptService {
    private final HealthPromptMapper mapper;

    /** 用途：创建系统提示词服务；参数：提示词数据访问接口；返回值：无。 */
    public HealthPromptService(HealthPromptMapper mapper) {
        this.mapper = mapper;
    }

    /** 用途：读取数据库中的问答系统提示词；参数：无；返回值：系统提示词。 */
    public String get() {
        HealthPrompt prompt = mapper.selectById(1);
        if (prompt == null) throw new IllegalStateException("系统提示词不存在");
        return prompt.getContent();
    }

    /** 用途：保存问答系统提示词；参数：新的提示词内容；返回值：无。 */
    public void save(String content) {
        if (content == null || content.isBlank() || content.length() > 10000) {
            throw new IllegalArgumentException("系统提示词不能为空且不能超过 10000 字");
        }
        mapper.save(content.trim());
    }
}
