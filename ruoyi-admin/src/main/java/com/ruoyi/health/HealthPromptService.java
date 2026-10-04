package com.ruoyi.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthPromptService {
    private final JdbcTemplate jdbc;

    /** 用途：创建系统提示词服务；参数：数据库访问模板；返回值：无。 */
    public HealthPromptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 用途：读取数据库中的问答系统提示词；参数：无；返回值：系统提示词。 */
    public String get() {
        return jdbc.queryForObject("SELECT content FROM health_prompt WHERE id = 1", String.class);
    }

    /** 用途：保存问答系统提示词；参数：新的提示词内容；返回值：无。 */
    public void save(String content) {
        if (content == null || content.isBlank() || content.length() > 10000) {
            throw new IllegalArgumentException("系统提示词不能为空且不能超过 10000 字");
        }
        jdbc.update("INSERT INTO health_prompt (id, content) VALUES (1, ?) "
            + "ON DUPLICATE KEY UPDATE content = VALUES(content)", content.trim());
    }
}
