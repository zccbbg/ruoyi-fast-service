-- 移除代码生成模块的菜单权限与数据表。
DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT menu_id
    FROM (
        SELECT menu_id
        FROM sys_menu
        WHERE path = 'tool/gen/index'
           OR perms LIKE 'tool:gen:%'
    ) AS code_generator_menus
);

DELETE FROM sys_menu
WHERE path = 'tool/gen/index'
   OR perms LIKE 'tool:gen:%';

DROP TABLE IF EXISTS gen_table_column;
DROP TABLE IF EXISTS gen_table;
