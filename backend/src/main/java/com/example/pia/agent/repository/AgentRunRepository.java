package com.example.pia.agent.repository;

import com.example.pia.agent.domain.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    List<AgentRun> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<AgentRun> findByStatus(AgentRun.RunStatus status);
}
