# 12 Acceptance Checklist

## Phase 0

- [ ] Backend starts
- [ ] Frontend starts
- [ ] PostgreSQL starts
- [ ] Redis starts
- [ ] Flyway succeeds
- [ ] CI succeeds

## Phase 1

- [ ] Project can be created
- [ ] Repository can be imported
- [ ] Files indexed
- [ ] Full reindex works
- [ ] Incremental reindex design exists
- [ ] Path security tests pass

## Phase 2

- [ ] Java symbols extracted
- [ ] JS/TS/Vue symbols extracted
- [ ] Controller detected
- [ ] Service detected
- [ ] Mapper detected
- [ ] Frontend API detected
- [ ] Backend endpoint detected
- [ ] Frontend/backend mapping works
- [ ] Caller/callee query works

## Phase 3

- [ ] Exact search
- [ ] Keyword search
- [ ] Vector search
- [ ] Metadata filter
- [ ] Graph expansion
- [ ] Evidence extraction
- [ ] Retrieval Evaluation

## Phase 4

- [ ] Planner works
- [ ] Executor works
- [ ] Tool Registry works
- [ ] Reviewer works
- [ ] SSE works
- [ ] Trace works
- [ ] Final schema validates
- [ ] 100 Evaluation Cases loaded

## Phase 5

- [ ] Regression evaluation
- [ ] Prompt versioning
- [ ] Model versioning
- [ ] Token metrics
- [ ] Latency metrics
- [ ] Failure recovery
- [ ] Quality gates

## Phase 6

- [ ] Authentication
- [ ] RBAC
- [ ] Project authorization
- [ ] Tool authorization
- [ ] Audit log
- [ ] Prompt injection tests
- [ ] Path traversal tests
- [ ] Secret leakage tests
- [ ] Backup
- [ ] Restore drill
- [ ] Production deployment
- [ ] Monitoring

## Release Gate

必须全部满足：

- [ ] No known P0/P1 defects
- [ ] Build green
- [ ] Tests green
- [ ] Evaluation gate green
- [ ] Security gate green
- [ ] Deployment rehearsal complete
- [ ] Rollback verified
- [ ] Documentation updated
