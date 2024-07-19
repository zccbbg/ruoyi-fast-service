package com.ruoyi.system.domain.bo;

import com.ruoyi.system.domain.entity.SysLogininfor;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统访问记录业务对象 sys_logininfor
 *
 * @author Michelle.Chung
 */

@Data
@AutoMapper(target = SysLogininfor.class, reverseConvertGenerate = false)
public class SysLogininforBo {

    /**
     * 访问ID
     */
    private Long infoId;

    /**
     * 用户账号
     */
    private String userName;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 登录状态（0失败 1成功）
     */
    private String status;

    /**
     * 提示消息
     */
    private String msg;

    /**
     * 访问时间
     */
    private LocalDateTime loginTime;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();


}
