package com.example.pia.agent;

import com.example.pia.agent.domain.AgentRun;
import com.example.pia.agent.repository.AgentRunRepository;
import com.example.pia.agent.tool.ToolRegistry;
import com.example.pia.common.config.PiaProperties;
import com.example.pia.common.exception.PiaException;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentRuntime 单元测试
 *
 * <p>测试 Agent 工作流编排的核心分支：
 * - 项目不存在时抛出业务异常
 * - 项目未索引时拒绝创建 Run
 * - 正常场景创建 Run 并返回 runId
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeTest {

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PlannerService plannerService;

    @Mock
    private ExecutorService executorService;

    @Mock
    private ReviewerService reviewerService;

    @Mock
    private AnswerComposerService answerComposerService;

    @Mock
    private PiaProperties piaProperties;

    @InjectMocks
    private AgentRuntime agentRuntime;

    @Test
    @DisplayName("创建 Run：项目不存在，应抛出 PiaException")
    void createRun_projectNotFound_shouldThrow() {
        when(projectRepository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentRuntime.createRun("not-exist", "test query"))
            .isInstanceOf(PiaException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("创建 Run：项目未完成索引，应抛出 INDEX_NOT_READY")
        void createRun_notIndexed_shouldThrow() {
        Project project = Project.builder()
            .id("proj-001")
            .name("test")
            .status(Project.ProjectStatus.INDEXING)
            .build();
        when(projectRepository.findById("proj-001")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> agentRuntime.createRun("proj-001", "test"))
            .isInstanceOf(PiaException.class)
            .hasMessageContaining("尚未完成索引");
    }

    @Test
    @DisplayName("创建 Run：项目已索引，应成功返回 runId")
    void createRun_indexed_shouldReturnRunId() {
        Project project = Project.builder()
            .id("proj-001")
            .name("test-project")
            .status(Project.ProjectStatus.INDEXED)
            .build();
        when(projectRepository.findById("proj-001")).thenReturn(Optional.of(project));
        when(agentRunRepository.save(any(AgentRun.class)))
            .thenAnswer(inv -> inv.getArgument(0));

                AgentRun run = agentRuntime.createRun("proj-001", "订单取消功能影响分析");

        assertThat(run.getId()).isNotNull().hasSize(36);
        assertThat(run.getQuery()).isEqualTo("订单取消功能影响分析");
        assertThat(run.getStatus()).isEqualTo(AgentRun.RunStatus.PENDING);
        verify(agentRunRepository).save(any(AgentRun.class));
    }
}
