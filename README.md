## 平台简介

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://gitee.com/zccbbg/ruoyi-fast-service/blob/master/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-blue.svg)]()
[![JDK-21](https://img.shields.io/badge/JDK-21-green.svg)]()

## 一个好用的脚手架

一定在细节之处做足功夫，操作极简！

一定在持续不断的优化，保持克制，杜绝臃肿！

一定与客户保持持续的联系，做到力所能及的理解需求！


> 项目代码、文档 均开源免费可商用 遵循开源协议在项目中保留开源协议文件即可<br>
活到老写到老 为兴趣而开源 为学习而开源 为让大家真正可以学到技术而开源

## 更新记录
参考：[UpdateHistory.md](UpdateHistory.md)

## 前端项目地址
#### gitee
[https://gitee.com/zccbbg/ruoyi-fast-vue3](https://gitee.com/zccbbg/ruoyi-fast-vue3)

#### github
[https://github.com/zccbbg/ruoyi-fast-vue3](https://github.com/zccbbg/ruoyi-fast-vue3)

## 本框架与RuoYi的功能差异

> 说明：本项目继承自 RuoYi-Vue-Plus 4.x，并回填了部分 5.x 的实用特性，同时按实际需求精简功能。

### 功能精简

| 范围 | 精简内容 |
|------|----------|
| 系统管理 | 移除部门、岗位、菜单和角色管理，保留用户管理。 |
| 登录认证 | 移除注册、验证码、邮件和短信登录，保留账号密码登录。 |
| 通用模块 | 移除代码生成器、演示模块和 OSS 模块。 |
| 后台功能 | 移除通知公告和缓存监控。 |
| 前端布局 | 移除主题切换、设置抽屉、顶部导航和标签页。 |

### 工程结构差异

- 后端采用**插件化 + 扩展包**结构：`ruoyi-common`（satoken / redis / mybatis / excel / log / sensitive 等插件包）+ `ruoyi-system` + `ruoyi-admin`，模块低耦合、易扩展。
- 官方为模块相互注入，耦合较重、扩展困难。

### 与ruoyi-vue-plus对比

> 本项目继承自 Plus 4.x，持续升级技术底座，目前使用 JDK 21 和 Spring Boot 4.1.1，并舍弃多租户、工作流等重型模块。下表与 Plus 4.x、Plus 5.x 三方对比。

| 维度 | 本项目 ruoyi-fast                | RuoYi-Vue-Plus 4.x | RuoYi-Vue-Plus 5.x |
|------|-------------------------------|--------------------|--------------------|
| JDK | 21                            | 8 | 17（部分支持 21） |
| Spring Boot | 4.1.1                         | 2.7.x | 3.x |
| Service 接口层 | 去除（system 用具体类）               | ✅ I*Service + Impl | ✅ I*Service + Impl |
| 逻辑删除约定 | 0=存在 / 1=删除                   | 2=删除 | 2=删除 |
| 状态约定 | 1=正常 / 0=停用                   | 0=正常 / 1=停用 | 0=正常 / 1=停用 |
| 时间类型 | LocalDateTime                 | Date | LocalDateTime |
| 对象转换 | MapStruct-Plus                | BeanUtil / BeanCopier | MapStruct |
| 前端 | Vue3 + TS（vue3-element-admin） | Vue2/Vue3 + JS | Vue3 + TS / Vben5 |


## 贡献代码

欢迎各路英雄豪杰 `PR` 代码 请提交到 `dev` 开发分支 统一测试发版
