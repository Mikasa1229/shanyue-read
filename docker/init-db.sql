-- 善阅坊数据库初始化脚本
-- PostgreSQL 容器启动时自动执行（仅在 data 目录为空时运行一次）
-- 只负责建库；建表由各服务的 Flyway 迁移脚本（db/migration/V1__init.sql）负责

CREATE DATABASE db_user;
CREATE DATABASE db_novel;
CREATE DATABASE db_comment;
CREATE DATABASE db_interaction;
CREATE DATABASE db_checkin;
CREATE DATABASE db_agent;
