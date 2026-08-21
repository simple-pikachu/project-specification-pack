package com.example.pia.project;

import com.example.pia.common.exception.PiaException;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import com.example.pia.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ProjectService 单元测试
 *
 * <p>【测试策略说明】
 * 这是纯单元测试（不启动 Spring 容器，不连数据库）。
 * 使用 Mockito 模拟 Repository，只测试 Service 的业务逻辑。
 *
 * <p>优点：
 * - 快（毫秒级）
 * - 不依赖外部环境
 * - 专注于业务规则验证
 *
 * <p>集成测试（需要真实数据库）在 ProjectRepositoryTest 中。
 *
 * <p>命名规范：{被测类}_{场景}_{期望结果}
  * 或使用 @DisplayName 写中文描述（更易读）
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    @DisplayName("创建项目：正常场景，应返回新建项目")
    void createProject_whenNameNotExists_shouldReturnNewProject() {
        // Given（准备）
        String name = "my-project";
        String sourcePath = "/home/user/projects/my-project";
        when(projectRepository.existsByName(name)).thenReturn(false);
        when(projectRepository.save(any(Project.class)))
            .thenAnswer(invocation -> invocation.getArgument(0)); // 返回传入的对象

        // When（执行）
        Project result = projectService.createProject(name, Project.SourceType.LOCAL, sourcePath);

        // Then（验证）
        assertThat(result.getId()).isNotNull().hasSize(36); // UUID 格式
        assertThat(result.getName()).isEqualTo(name);
                assertThat(result.getStatus()).isEqualTo(Project.ProjectStatus.CREATED);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("创建项目：项目名已存在，应抛出业务异常")
    void createProject_whenNameExists_shouldThrowPiaException() {
        // Given
        when(projectRepository.existsByName("existing-project")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() ->
            projectService.createProject("existing-project", Project.SourceType.LOCAL, "/path")
        )
        .isInstanceOf(PiaException.class)
        .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("查询项目：项目不存在，应抛出 PROJECT_NOT_FOUND 异常")
    void getProject_whenNotFound_shouldThrowProjectNotFound() {
        // Given
        when(projectRepository.findById(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.getProject("non-existent-id"))
                        .isInstanceOf(PiaException.class)
            .hasMessageContaining("不存在");
    }
}
