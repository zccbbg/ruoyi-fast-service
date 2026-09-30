package com.ruoyi.health;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.health.domain.HealthModel;
import com.ruoyi.health.mapper.HealthModelMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.core.io.ByteArrayResource;

@Service
public class HealthModels {
    private final HealthModelMapper mapper;
    private final String encryptionKey;
    private final SecureRandom random = new SecureRandom();

    /** 用途：创建模型配置服务；参数：MyBatis-Plus Mapper、服务器加密密钥；返回值：无。 */
    public HealthModels(HealthModelMapper mapper, @Value("${health.encryption-key:}") String encryptionKey) {
        this.mapper = mapper;
        this.encryptionKey = encryptionKey;
    }

    /** 用途：列出可用模型且不返回密钥；参数：无；返回值：模型配置列表。 */
    public List<ModelView> list() {
        return mapper.selectList(new LambdaQueryWrapper<HealthModel>()
                .orderByAsc(HealthModel::getPurpose)
                .orderByDesc(HealthModel::getDefaultFlag)
                .orderByAsc(HealthModel::getCreatedAt))
            .stream().map(this::view).toList();
    }

    /** 用途：保存问答或报告模型配置；参数：模型表单；返回值：模型编号。 */
    @Transactional
    public String save(ModelInput input) {
        if (input == null || input.name() == null || input.name().isBlank()
            || input.modelId() == null || input.modelId().isBlank()
            || input.apiKey() == null || input.apiKey().isBlank()) {
            throw new IllegalArgumentException("模型名称、编号和密钥不能为空");
        }
        if (!List.of("OPENAI", "DEEPSEEK", "QWEN").contains(input.provider())
            || !List.of("CHAT", "REPORT").contains(input.purpose())) {
            throw new IllegalArgumentException("不支持的服务商或用途");
        }
        HealthModel model = new HealthModel();
        model.setId(UUID.randomUUID().toString());
        model.setName(input.name().trim());
        model.setProvider(input.provider());
        model.setPurpose(input.purpose());
        model.setModelId(input.modelId().trim());
        model.setEncryptedKey(encrypt(input.apiKey().trim()));
        model.setDefaultFlag(input.makeDefault());
        if (input.makeDefault()) {
            mapper.update(null, new LambdaUpdateWrapper<HealthModel>()
                .eq(HealthModel::getPurpose, input.purpose()).set(HealthModel::getDefaultFlag, false));
        }
        mapper.insert(model);
        return model.getId();
    }

    /** 用途：删除模型配置；参数：模型编号；返回值：无。 */
    public void delete(String id) {
        mapper.deleteById(id);
    }

    /** 用途：设定同一用途下的默认模型；参数：模型编号；返回值：无。 */
    @Transactional
    public void setDefault(String id) {
        HealthModel model = mapper.selectById(id);
        if (model == null) throw new IllegalArgumentException("模型不存在");
        mapper.update(null, new LambdaUpdateWrapper<HealthModel>()
            .eq(HealthModel::getPurpose, model.getPurpose()).set(HealthModel::getDefaultFlag, false));
        mapper.update(null, new LambdaUpdateWrapper<HealthModel>()
            .eq(HealthModel::getId, id).set(HealthModel::getDefaultFlag, true));
    }

    /** 用途：读取指定用途的模型，未指定时选择默认项；参数：模型编号和用途；返回值：内部模型配置。 */
    public ModelConfig select(String id, String purpose) {
        LambdaQueryWrapper<HealthModel> query = new LambdaQueryWrapper<HealthModel>()
            .eq(HealthModel::getPurpose, purpose).last("limit 1");
        if (id == null || id.isBlank()) {
            query.orderByDesc(HealthModel::getDefaultFlag).orderByAsc(HealthModel::getCreatedAt);
        } else {
            query.eq(HealthModel::getId, id);
        }
        HealthModel found = mapper.selectOne(query);
        if (found == null) {
            throw new IllegalStateException("请先配置" + ("CHAT".equals(purpose) ? "问答" : "报告识别") + "模型");
        }
        return config(found);
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
        String baseUrl = switch (config.provider()) {
            case "DEEPSEEK" -> "https://api.deepseek.com";
            case "OPENAI" -> "https://api.openai.com";
            case "QWEN" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default -> throw new IllegalArgumentException("不支持的服务商：" + config.provider());
        };
        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(baseUrl).apiKey(config.apiKey())
            .model(config.modelId()).build();
        return ChatClient.builder(OpenAiChatModel.builder().options(options).build()).build();
    }

    /** 用途：将数据库实体转换成无密钥视图；参数：模型实体；返回值：模型视图。 */
    private ModelView view(HealthModel model) {
        return new ModelView(model.getId(), model.getName(), model.getProvider(),
            model.getPurpose(), model.getModelId(), Boolean.TRUE.equals(model.getDefaultFlag()));
    }

    /** 用途：将数据库实体转换成内部模型配置；参数：模型实体；返回值：含解密密钥的配置。 */
    private ModelConfig config(HealthModel model) {
        return new ModelConfig(model.getProvider(), model.getModelId(), decrypt(model.getEncryptedKey()));
    }

    /** 用途：加密模型密钥后存储；参数：原始密钥；返回值：带随机向量的密文。 */
    private String encrypt(String value) {
        SecretKeySpec key = secret();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + ":"
                + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法加密模型密钥", ex);
        }
    }

    /** 用途：解密服务端调用模型所需的密钥；参数：密文；返回值：原始密钥。 */
    private String decrypt(String value) {
        SecretKeySpec key = secret();
        try {
            String[] parts = value.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("无法解密模型密钥，请检查 HEALTH_CONFIG_KEY", ex);
        }
    }

    /** 用途：读取服务器提供的 AES 主密钥；参数：无；返回值：256 位密钥。 */
    private SecretKeySpec secret() {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            if (key.length == 32) {
                return new SecretKeySpec(key, "AES");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("HEALTH_CONFIG_KEY 必须是 32 字节密钥的标准 Base64 值", ex);
        }
        throw new IllegalStateException("HEALTH_CONFIG_KEY 必须是 32 字节密钥的标准 Base64 值");
    }

    public record ModelInput(String name, String provider, String purpose, String modelId,
                             String apiKey, boolean makeDefault) {}
    public record ModelView(String id, String name, String provider, String purpose,
                            String modelId, boolean isDefault) {}
    public record ModelConfig(String provider, String modelId, String apiKey) {}
}
