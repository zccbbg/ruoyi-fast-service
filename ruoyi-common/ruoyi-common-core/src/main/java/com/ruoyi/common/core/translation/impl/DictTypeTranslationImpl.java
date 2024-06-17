package com.ruoyi.common.core.translation.impl;

import com.ruoyi.common.core.core.service.DictService;
import com.ruoyi.common.core.annotation.TranslationType;
import com.ruoyi.common.core.constant.TransConstant;
import com.ruoyi.common.core.translation.TranslationInterface;
import com.ruoyi.common.core.utils.StringUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字典翻译实现
 *
 * @author Lion Li
 */
@Component
@AllArgsConstructor
@TranslationType(type = TransConstant.DICT_TYPE_TO_LABEL)
public class DictTypeTranslationImpl implements TranslationInterface<String> {

    private final DictService dictService;

    @Override
    public String translation(Object key, String other) {
        if (key instanceof String && StringUtils.isNotBlank(other)) {
            return dictService.getDictLabel(other, key.toString());
        }
        return null;
    }
}
