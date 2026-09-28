package com.ruoyi.health;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.core.io.ByteArrayResource;

@Service
public class HealthModels {
    private final JdbcTemplate jdbc;
    private final String encryptionKey;
    private final SecureRandom random = new SecureRandom();

    /** 用途：创建模型配置服务；参数：数据库访问器、服务器加密密钥；返回值：无。 */
    public HealthModels(JdbcTemplate jdbc, @Value("${health.encryption-key:}") String encryptionKey) {
        this.jdbc = jdbc;
        this.encryptionKey = encryptionKey;
    }

    /** 用途：列出可用模型且不返回密钥；参数：无；返回值：模型配置列表。 */
    public List<ModelView> list() {
        return jdbc.query("select id,name,provider,purpose,model_id,is_default from health_model order by purpose,is_default desc,created_at",
            (rs, row) -> view(rs));
    }

    /** 用途：保存问答或报告模型配置；参数：模型表单；返回值：模型编号。 */
    @Transactional
    public String save(ModelInput input) {
        if (input.name() == null || input.name().isBlank() || input.modelId() == null || input.modelId().isBlank()
            || input.apiKey() == null || input.apiKey().isBlank()) {
            throw new IllegalArgumentException("模型名称、编号和密钥不能为空");
        }
        if (!List.of("OPENAI", "DEEPSEEK").contains(input.provider())
            || !List.of("CHAT", "REPORT").contains(input.purpose())) {
            throw new IllegalArgumentException("不支持的服务商或用途");
        }
        String id = UUID.randomUUID().toString();
        String encrypted = encrypt(input.apiKey().trim());
        if (input.makeDefault()) {
            jdbc.update("update health_model set is_default=0 where purpose=?", input.purpose());
        }
        jdbc.update("insert into health_model(id,name,provider,purpose,model_id,encrypted_key,is_default) values(?,?,?,?,?,?,?)",
            id, input.name().trim(), input.provider(), input.purpose(), input.modelId().trim(),
            encrypted, input.makeDefault() ? 1 : 0);
        return id;
    }

    /** 用途：删除模型配置；参数：模型编号；返回值：无。 */
    public void delete(String id) {
        jdbc.update("delete from health_model where id=?", id);
    }

    /** 用途：设定同一用途下的默认模型；参数：模型编号；返回值：无。 */
    @Transactional
    public void setDefault(String id) {
        List<String> purposes = jdbc.query("select purpose from health_model where id=?",
            (rs, row) -> rs.getString(1), id);
        if (purposes.isEmpty()) throw new IllegalArgumentException("模型不存在");
        jdbc.update("update health_model set is_default=0 where purpose=?", purposes.get(0));
        jdbc.update("update health_model set is_default=1 where id=?", id);
    }

    /** 用途：读取指定用途的模型，未指定时选择默认项；参数：模型编号和用途；返回值：内部模型配置。 */
    public ModelConfig select(String id, String purpose) {
        List<ModelConfig> found = id == null || id.isBlank()
            ? jdbc.query("select * from health_model where purpose=? order by is_default desc,created_at limit 1",
                (rs, row) -> config(rs), purpose)
            : jdbc.query("select * from health_model where id=? and purpose=?", (rs, row) -> config(rs), id, purpose);
        if (found.isEmpty()) {
            throw new IllegalStateException("请先配置" + ("CHAT".equals(purpose) ? "问答" : "报告识别") + "模型");
        }
        return found.get(0);
    }

    /** 用途：按服务商接口调用文字模型；参数：模型配置和提示词；返回值：模型回答。 */
    public String ask(ModelConfig config, String prompt) {
        return client(config).prompt().user(prompt).call().content();
    }

    /** 用途：将报告图片交给具备视觉能力的模型识别；参数：模型配置、图片字节、MIME 类型和提示词；返回值：识别文本。 */
    public String readImage(ModelConfig config, byte[] bytes, MimeType mimeType, String prompt) {
        return client(config).prompt().user(user -> user.text(prompt).media(mimeType, new ByteArrayResource(bytes)))
            .call().content();
    }

    /** 用途：创建动态模型客户端；参数：模型配置；返回值：Spring AI 客户端。 */
    private ChatClient client(ModelConfig config) {
        String url = "DEEPSEEK".equals(config.provider()) ? "https://api.deepseek.com" : "https://api.openai.com";
        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(url).apiKey(config.apiKey())
            .model(config.modelId()).build();
        return ChatClient.builder(OpenAiChatModel.builder().options(options).build()).build();
    }

    /** 用途：将数据库行转换成无密钥视图；参数：查询结果；返回值：模型视图。 */
    private ModelView view(ResultSet rs) throws java.sql.SQLException {
        return new ModelView(rs.getString("id"), rs.getString("name"), rs.getString("provider"),
            rs.getString("purpose"), rs.getString("model_id"), rs.getBoolean("is_default"));
    }

    /** 用途：将数据库行转换成内部模型配置；参数：查询结果；返回值：含解密密钥的配置。 */
    private ModelConfig config(ResultSet rs) throws java.sql.SQLException {
        return new ModelConfig(rs.getString("provider"), rs.getString("model_id"), decrypt(rs.getString("encrypted_key")));
    }

    /** 用途：加密模型密钥后存储；参数：原始密钥；返回值：带随机向量的密文。 */
    private String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret(), new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + ":"
                + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法加密模型密钥", ex);
        }
    }

    /** 用途：解密服务端调用模型所需的密钥；参数：密文；返回值：原始密钥。 */
    private String decrypt(String value) {
        try {
            String[] parts = value.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(),
                new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("无法解密模型密钥，请检查 HEALTH_CONFIG_KEY", ex);
        }
    }

    /** 用途：读取服务器提供的 AES 主密钥；参数：无；返回值：256 位密钥。 */
    private SecretKeySpec secret() {
        byte[] key = Base64.getDecoder().decode(encryptionKey);
        if (key.length != 32) {
            throw new IllegalStateException("HEALTH_CONFIG_KEY 必须是 32 字节密钥的 Base64 值");
        }
        return new SecretKeySpec(key, "AES");
    }

    public record ModelInput(String name, String provider, String purpose, String modelId,
                             String apiKey, boolean makeDefault) {}
    public record ModelView(String id, String name, String provider, String purpose,
                            String modelId, boolean isDefault) {}
    public record ModelConfig(String provider, String modelId, String apiKey) {}
}
