package com.ruoyi.common.satoken.core.service;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;

/**
 * sa-token 权限管理实现类
 *
 * @author Lion Li
 */
public class SaPermissionImpl implements StpInterface {

    /**
     * 获取菜单权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of("*:*:*");
    }

    /**
     * 获取角色权限列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of("user");
    }
}
