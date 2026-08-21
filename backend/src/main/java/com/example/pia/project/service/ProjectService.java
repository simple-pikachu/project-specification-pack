package com.example.pia.project.service;

import com.example.pia.common.exception.PiaException;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 项目业务服务
 *
 * <p>【Service 层职责】
 * Service 是业务逻辑的唯一入口，负责：
 * <ul>
 *   <li>业务规则校验（项目名不能重复）</li>
 *   <li>事务管理（@Transactional 保证数据一致性）</li>
 *   <li>编排调用 Repository 和其他 Service</li>
 * </ul>
 *
 * <p>Controller 只负责 HTTP 层（参数解析、响应格式化），不写业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    /**
     * 创建项目
     *
     * <p>Phase 0：只创建记录，不启动索引。
     * Phase 1：调用 IndexingService 启动异步索引。
     */
    @Transactional
    public Project createProject(String name, Project.SourceType sourceType, String sourcePath) {
        // 业务规则：项目名不能重复
        if (projectRepository.existsByName(name)) {
            throw new PiaException("PROJECT_NAME_EXISTS", "项目名称已存在: " + name);
        }

        Project project = Project.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .sourceType(sourceType)
            .sourcePath(sourcePath)
            .status(Project.ProjectStatus.CREATED)
            .build();

        project = projectRepository.save(project);
        log.info("项目创建成功: id={}, name={}", project.getId(), project.getName());
        return project;
    }

    /** 查询单个项目（不存在则抛业务异常） */
    @Transactional(readOnly = true)
    public Project getProject(String projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> PiaException.projectNotFound(projectId));
    }

    /** 查询所有项目 */
        @Transactional(readOnly = true)
    public List<Project> listProjects() {
        return projectRepository.findAll();
    }
}
