package com.example.pia.agent.repository;

import com.example.pia.agent.domain.ToolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCallRepository extends JpaRepository<ToolCall, String> {
    List<ToolCall> findByRunIdOrderByCreatedAtAsc(String runId);
    long countByRunId(String runId);
}
