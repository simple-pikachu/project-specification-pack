# 00 Project Overview

## 1. 产品名称

Project Intelligence Agent（PIA）

## 2. 产品目标

对已有项目 A 的：

- Frontend
- Backend
- API
- Database Schema
- Configuration
- Dependency
- Git metadata
- Project documentation

建立机器可查询的项目知识模型，使 Agent 能完成：

1. 需求理解
2. 代码定位
3. 调用链分析
4. 前后端关联分析
5. 数据库影响分析
6. Bug 根因调查
7. 影响范围分析
8. 实现方案生成
9. 测试方案生成
10. 风险识别

## 3. MVP 非目标

MVP 不实现：

- 自动修改源码
- 自动提交 Git
- 自动执行任意 Shell
- 自动执行任意 SQL
- 自动部署生产环境
- 自主修改基础设施

## 4. 目标用户

### Developer

查询代码、分析需求、定位 Bug。

### Tech Lead

进行影响分析、技术方案评审。

### QA

生成测试影响范围和测试用例。

### Agent Platform Engineer

配置 Agent、Tool、Knowledge、Model 和 Evaluation。

## 5. 核心场景

### UC-001 需求影响分析

输入：

> 给订单增加取消功能。

输出：

- 需求理解
- 当前系统能力
- 前端影响
- 后端影响
- API 影响
- DB 影响
- 权限影响
- 状态机影响
- 修改文件列表
- 实现步骤
- 测试方案
- 风险
- Evidence

### UC-002 Bug 根因分析

输入：

> 订单支付成功后状态没有更新。

Agent 必须调查：

1. 前端调用
2. API
3. Controller
4. Service
5. Transaction
6. MQ/Event
7. Mapper
8. DB
9. 日志/异常
10. Git 最近变更

### UC-003 代码问答

必须支持：

- “这个方法谁调用？”
- “这个接口前端在哪里调用？”
- “这个字段在哪里修改？”
- “这个状态在哪里定义？”
- “修改这个类会影响什么？”

## 6. 产品成功标准

MVP：

- 代码定位准确率 ≥ 95%
- API 前后端关联准确率 ≥ 95%
- 核心调用链准确率 ≥ 90%
- 关键结论 Evidence 覆盖率 ≥ 95%
- 不允许虚构文件/方法作为成功答案
- 典型需求 Case 的方案可被人工评审为可实施
