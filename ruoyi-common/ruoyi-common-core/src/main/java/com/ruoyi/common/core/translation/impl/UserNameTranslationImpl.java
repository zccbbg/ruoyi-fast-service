package com.ruoyi.common.core.translation.impl;

import com.ruoyi.common.core.core.service.UserService;
import com.ruoyi.common.core.annotation.TranslationType;
import com.ruoyi.common.core.constant.TransConstant;
import com.ruoyi.common.core.translation.TranslationInterface;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户名翻译实现
 *
 * @author Lion Li
 */
@Component
@AllArgsConstructor
@TranslationType(type = TransConstant.USER_ID_TO_NAME)
public class UserNameTranslationImpl implements TranslationInterface<String> {

    private final UserService userService;

    @Override
    public String translation(Object key, String other) {
        if (key instanceof Long) {
            return userService.selectUserNameById((Long) key);
        }
        return null;
    }
}
