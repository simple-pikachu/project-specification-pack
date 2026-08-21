package com.example.pia.security.repository;

import com.example.pia.security.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问层
 *
 * <p>支持按用户/项目/时间维度查询操作历史，用于安全溯源和合规审计。
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /** 查询某用户的操作历史（合规审计） */
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 查询某项目的操作历史 */
    List<AuditLog> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /** 查询安全异常事件（安全运营） */
    List<AuditLog> findByEventTypeOrderByCreatedAtDesc(AuditLog.EventType eventType);

    /** 按时间范围查询（日志导出） */
    List<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
