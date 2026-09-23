-- 清理已有数据库中不再使用的主框架外观配置。
DELETE FROM `sys_config`
WHERE `config_key` IN ('sys.index.skinName', 'sys.index.sideTheme');
