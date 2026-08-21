package com.example.pia.api;

import com.example.pia.project.domain.Project;
import com.example.pia.project.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理 REST API
 *
 * <p>【Controller 层职责】
 * Controller 是 HTTP 请求的入口，只负责：
 * <ul>
 *   <li>接收 HTTP 请求（@GetMapping/@PostMapping）</li>
 *   <li>参数校验（@Valid）</li>
 *   <li>调用 Service 执行业务</li>
 *   <li>将结果转换为 HTTP 响应</li>
 * </ul>
 * 不写任何业务逻辑，不直接访问数据库。
 *
 * <p>API 路径：/api/projects
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * POST /api/projects — 创建项目
     *
   * <p>返回 201 Created + 项目信息。
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.createProject(
            request.name(),
            Project.SourceType.valueOf(request.sourceType().toUpperCase()),
            request.sourcePath()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProjectResponse.from(project));
    }

    /**
     * GET /api/projects/{projectId} — 查询项目详情
     */
    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable String projectId) {
        return ProjectResponse.from(projectService.getProject(projectId));
    }

    /**
     * GET /api/projects — 查询所有项目
     */
    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.listProjects().stream()
            .map(ProjectResponse::from)
             .toList();
    }

    // ──── 请求/响应 DTO（Record 类型，不可变，自带 equals/hashCode/toString） ────

    /** 创建项目请求体 */
    record CreateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 255, message = "项目名称最长 255 字符")
        String name,

        @NotBlank(message = "源码类型不能为空")
        String sourceType,  // LOCAL 或 GIT

        @NotBlank(message = "源码路径不能为空")
        String sourcePath
    ) {}

    /** 项目响应体 */
    record ProjectResponse(
        String id,
        String name,
        String sourceType,
        String sourcePath,
        String status,
        String createdAt,
        String updatedAt
    ) {
        static ProjectResponse from(Project p) {
            return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getSourceType().name(),
                p.getSourcePath(),
                p.getStatus().name(),
               p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
                p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null
            );
        }
    }
}
