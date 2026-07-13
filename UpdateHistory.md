## 260611
1. service：统一状态码定义规范。
2. vue：修复 OSS 配置状态切换功能。

## 251016
1. 代码生成：优化 Vue 模板自动加载数据，引入 onMounted 钩子，组件挂载时自动执行 getList()。

## 251015
1. 配置日志文件输出路径，application.yml 增加 logging.file.path，logback-plus.xml 改用 Spring 属性动态注入日志路径。

## 250924
1. vue：修复 DictTag 组件空字符串时标签类型显示问题，默认显示 primary 类型。

## 250916
1. service：修复 OSS 配置状态加载错误。
2. vue：移除导航栏中的冗余组件。

## 250912
1. 更新数据库脚本并调整数据结构。
2. 删除 Docker 部署配置文件。

## 250422
1. 代码生成：修改生成模板中的默认排序方式和菜单可见性。
2. 更新版权年份为 2025 年。

## 250421
1. service：添加实体类基类和工具类。
2. vue：代码生成工具页每页显示条数调整为 50。

## 240820
1. vue：menu 设置页面默认显示状态改为「显示」，是否为外链默认为「否」。

## 240819
1. vue：icon 更新。

## 240818
1. profile controller 优化，增加 ProfileVo；SysRoleMapper.xml 查询优化。

## 240804
1. 简化代码，去除service 接口，去除一些没必要校验。
2. 逻辑删除优化：0代表存在 1代表删除。
3. BaseEntity 优化 Date->LocalDateTime, createBy和updateBy保持String。
4. 状态修改，1代表正常，0代表停用。
5. 简化数据库支持，目前只支持mysql。
6. docker 暂时不支持。
7. 代码生成前端vue文件去掉一些不需要的方法。
8. 代码生成前端vue文件优化，增加el-card，表格样式修改。
9. system模块增加vo、bo。
10. 全局异常处理优化:增加文件名或填写内容过长提示。
11. service端baseMapper改类名小驼峰。
12. 代码生成，前端编辑页面由dialog改为drawer

## 240627
1. 删除 snail-job monitor-admin ruoyi-extend 基本用不到
2. 优化 获取aop代理的方式 减少与其他使用aop的功能冲突的概率

## 240620
1. 完成重构 common, framework 包结构, 参照 cloud 版本拆分子模块 ;
update 更新模块包名 ;
2. 使用 jdk17 语法优化代码
3. mapstruct-plus 接入替换 BeanUtil ,用于实体类转换 ; 原先BeanUtil 对jdk17实体类转换会出错。

## 240613
1.删除vue代码，vue相关代码重新建项目。