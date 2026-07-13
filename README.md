## 平台简介

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://gitee.com/zccbbg/ruoyi-fast-service/blob/master/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1-blue.svg)]()
[![JDK-17](https://img.shields.io/badge/JDK-17-green.svg)]()

> 本项目继承自ruoyi-vue-plus,由于ruoyi-vue-plus 4.x版本不再开发，而5.x过于庞大，增加了flowable和多租户，本项目在4.x基础上适当增加一些5.x比较好的功能。


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

> 说明：本项目继承自 RuoYi-Vue-Plus 4.x，并回填了部分 5.x 的实用特性、同时做了精简。下表对比对象为官方若依 **RuoYi-Vue 前后端分离版**。

### 技术栈差异

| 维度 | 本框架 ruoyi-fast | 官方 RuoYi-Vue |
|------|------------------|----------------|
| 前端 | Vue3 + TypeScript + Element Plus（基于 vue3-element-admin 重写） | Vue2 + JavaScript |
| JDK / Spring Boot | JDK 17 + Spring Boot 3.2.6 | JDK 8 + Spring Boot 2.x |
| 权限认证 | Sa-Token + JWT，注解支持 `AND`/`OR` 等复杂表达式 | Spring Security，仅支持是否匹配 |
| ORM | MyBatis-Plus，基于对象几乎不写 SQL | 原生 MyBatis，需手写 XML SQL |
| Web 容器 | Undertow（基于 XNIO 高性能容器） | Tomcat |
| Redis 客户端 | Redisson，支持分布式限流/队列等 | Lettuce + RedisTemplate |
| 对象转换 | MapStruct-Plus（编译期生成，规避 JDK17 下 BeanUtil 转换出错） | BeanUtils |
| SQL 监控 | p6spy，输出完整 SQL 与执行耗时 | 日志拼接，需手动还原参数 |
| 数据分页 | MyBatis-Plus 分页插件，支持多字段/复杂排序 | PageHelper，仅单查询单排序 |
| 数据权限 | MyBatis-Plus 插件无感拼接 SQL | 注解 + AOP 手动拼接 |

### 工程结构差异

- 后端采用**插件化 + 扩展包**结构：`ruoyi-common`（satoken / redis / mybatis / oss / excel / sms / log / idempotent / ratelimiter / translation 等插件包）+ `ruoyi-modules`（system / generator / demo）+ `ruoyi-admin`，模块低耦合、易扩展。
- 官方为模块相互注入，耦合较重、扩展困难。

### 与ruoyi-vue-plus对比

> 本项目继承自 Plus 4.x，仅把 5.x 的现代技术底座（JDK17 + Spring Boot 3）搬过来，主动舍弃多租户、工作流等重型模块。下表与 Plus 4.x、Plus 5.x 三方对比。

| 维度 | 本项目 ruoyi-fast | RuoYi-Vue-Plus 4.x | RuoYi-Vue-Plus 5.x |
|------|------------------|--------------------|--------------------|
| JDK | 17 | 8 | 17（部分支持 21） |
| Spring Boot | 3.2.6 | 2.7.x | 3.x |
| 多租户 | ❌ 无（拦截器仅注释保留） | ❌ 基本无 | ✅ SaaS 多租户 |
| 工作流 | ❌ 无 | ❌ 无（需自行整合 Flowable） | ✅ 内置（5.2 为 Warm-Flow） |
| 分布式任务 | ❌ 无（已移除 snail-job） | Xxl-Job（extend 扩展） | ✅ SnailJob 内置 |
| 第三方登录 | ❌ 无 | ✅ JustAuth | ✅ JustAuth |
| WebSocket / SSE | ❌ 无 | 部分 | ✅ SSE 消息推送 |
| 多数据库异构 | ❌ 仅 MySQL（其余注释掉） | ✅ MySQL/Oracle/PG/SQLServer | ✅ 同 4.x |
| Service 接口层 | 去除（system 用具体类） | ✅ I*Service + Impl | ✅ I*Service + Impl |
| 逻辑删除约定 | 0=存在 / 1=删除 | 2=删除 | 2=删除 |
| 状态约定 | 1=正常 / 0=停用 | 0=正常 / 1=停用 | 0=正常 / 1=停用 |
| 时间类型 | LocalDateTime | Date | LocalDateTime |
| 对象转换 | MapStruct-Plus | BeanUtil / BeanCopier | MapStruct |
| Docker | ❌ 已删除 | ✅ | ✅ |
| 前端 | Vue3 + TS（vue3-element-admin） | Vue2/Vue3 + JS | Vue3 + TS / Vben5 |


## 贡献代码

欢迎各路英雄豪杰 `PR` 代码 请提交到 `dev` 开发分支 统一测试发版