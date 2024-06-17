package com.ruoyi.common.core.translation.impl;

import com.ruoyi.common.core.core.service.OssService;
import com.ruoyi.common.core.annotation.TranslationType;
import com.ruoyi.common.core.constant.TransConstant;
import com.ruoyi.common.core.translation.TranslationInterface;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OSS翻译实现
 *
 * @author Lion Li
 */
@Component
@AllArgsConstructor
@TranslationType(type = TransConstant.OSS_ID_TO_URL)
public class OssUrlTranslationImpl implements TranslationInterface<String> {

    private final OssService ossService;

    @Override
    public String translation(Object key, String other) {
        return ossService.selectUrlByIds(key.toString());
    }
}
