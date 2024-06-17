package com.ruoyi.common.core.translation.impl;

import com.ruoyi.common.core.core.service.DeptService;
import com.ruoyi.common.core.annotation.TranslationType;
import com.ruoyi.common.core.constant.TransConstant;
import com.ruoyi.common.core.translation.TranslationInterface;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 部门翻译实现
 *
 * @author Lion Li
 */
@Component
@AllArgsConstructor
@TranslationType(type = TransConstant.DEPT_ID_TO_NAME)
public class DeptNameTranslationImpl implements TranslationInterface<String> {

    private final DeptService deptService;

    @Override
    public String translation(Object key, String other) {
        return deptService.selectDeptNameByIds(key.toString());
    }
}
