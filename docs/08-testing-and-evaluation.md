# 08 Testing and Evaluation

## 1. Test Layers

### Unit

- parser
- extractor
- graph query
- retrieval
- tool schema
- planner parser
- answer validator

### Integration

- PostgreSQL
- pgvector
- Redis
- Agent Tool
- SSE

推荐 Testcontainers。

### E2E

真实 Project A：

```text
import
→ index
→ ask
→ tools
→ answer
```

## 2. Evaluation Dataset

至少准备：

### Code Navigation

- 20 cases

### Frontend/Backend Mapping

- 20 cases

### Impact Analysis

- 20 cases

### Bug Analysis

- 20 cases

### Requirement Analysis

- 20 cases

总计至少 100 cases。

## 3. Metrics

### Retrieval

- Recall@K
- MRR
- Precision@K

### Agent

- Task Success Rate
- Tool Success Rate
- Evidence Coverage
- Unsupported Claim Rate
- Hallucination Rate

### System

- P50/P95 latency
- token usage
- estimated cost
- error rate

## 4. Quality Gates

MVP 建议：

- Code Retrieval Recall@10 ≥ 0.90
- Frontend/Backend mapping ≥ 0.95
- Evidence Coverage ≥ 0.95
- Unsupported Claim Rate ≤ 0.05
- Tool Success Rate ≥ 0.98
- E2E Task Success ≥ 0.80

这些是初始工程目标，不是理论保证。应使用真实 Project A 数据集持续校准。

## 5. Golden Cases

每个真实线上问题修复后，可沉淀为：

```text
question
project snapshot
expected evidence
expected conclusion
expected tools
expected risks
```

进入回归集。
