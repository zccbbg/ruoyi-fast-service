-- 已有数据库升级脚本：移除部门、岗位、角色和菜单管理相关数据表。
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_role_dept`;
DROP TABLE IF EXISTS `sys_role_menu`;
DROP TABLE IF EXISTS `sys_user_post`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_dept`;
DROP TABLE IF EXISTS `sys_post`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_menu`;

ALTER TABLE `sys_user` DROP COLUMN `dept_id`;

SET FOREIGN_KEY_CHECKS = 1;
