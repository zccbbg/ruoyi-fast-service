# 若依 AI 健康档案助手

`ruoyi-ai-health-assistant` 是基于若依二次开发的健康档案 AI 应用。项目以家庭成员为单位管理健康资料，提供档案问答、报告识别和指标趋势查看等功能。

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://gitee.com/zccbbg/ruoyi-fast-service/blob/master/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-blue.svg)]()
[![JDK-21](https://img.shields.io/badge/JDK-21-green.svg)]()

项目沿用 RuoYi-Fast 的后台基础能力，并在此基础上开发健康档案业务。当前 AI 问答根据已有档案片段生成回答并标注来源；报告识别结果需要人工核对后才会写入资料。

## 主要功能

- **健康档案问答**：按成员检索 Markdown 档案，生成带来源的回答，并保存会话记录。
- **报告识别**：上传 PDF 或图片，提取报告摘要、指标和待办事项；核对草稿后写入档案。
- **指标趋势**：按成员查看已整理的结构化健康指标。
- **模型配置**：分别配置问答模型和报告识别模型。
- **后台管理**：保留用户、通知公告、字典、参数及日志等基础管理能力。

## 技术与目录

- 后端：JDK 21、Spring Boot 4.1.1、Spring AI、MyBatis-Plus。
- 前端：Vue 3、Element Plus、Vite，代码位于独立的 `ruoyi-fast-vue3` 仓库。
- `ruoyi-admin` 包含健康档案接口与应用入口；`ruoyi-system` 和 `ruoyi-common` 提供后台管理与公共能力。

## 更新记录
参考：[UpdateHistory.md](UpdateHistory.md)

## 前端项目地址
#### gitee
[https://gitee.com/zccbbg/ruoyi-fast-vue3](https://gitee.com/zccbbg/ruoyi-fast-vue3)

#### github
[https://github.com/zccbbg/ruoyi-fast-vue3](https://github.com/zccbbg/ruoyi-fast-vue3)

## 项目学习文档

项目实现说明和学习笔记统一放在 [`项目学习文档`](项目学习文档/README.md) 目录。记忆功能等后续设计可在这里持续补充。


## 贡献代码

欢迎提交 PR；请提交到 `dev` 开发分支。
