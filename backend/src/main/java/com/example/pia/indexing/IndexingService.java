package com.example.pia.indexing;

import com.example.pia.common.exception.PiaException;
import com.example.pia.indexing.domain.ProjectFile;
import com.example.pia.indexing.repository.ProjectFileRepository;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目索引服务
 *
 * <p>【Agent 开发：索引过程是什么？】
 *
 * <p>索引 = 将源码仓库转换为 Agent 可查询的知识库，分三个阶段：
 *
 * <pre>
 * Phase 1（本类）：
 *   文件扫描 → 语言检测 → Hash 计算 → 写入 project_file 表
 *   作用：Agent 能回答"这个项目有哪些文件？"
 *
  * Phase 2（后续实现）：
 *   AST 解析 → 符号提取 → 关系图构建 → 写入 code_symbol + graph_edge
 *   作用：Agent 能回答"OrderService.cancel() 调用了哪些方法？"
 *
 * Phase 3（后续实现）：
 *   代码切分 → Embedding → 写入 Qdrant
 *   作用：Agent 能回答"和订单支付相关的代码有哪些？"（语义搜索）
 * </pre>
 *
 * <p>{@code @Async} 注解使索引在独立线程池中异步执行，
 * API 调用方立即得到 202 Accepted 响应，不需要等待索引完成。
 * 前端通过 GET /api/projects/{id}/index/status 轮询进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final FileScanner fileScanner;

    /**
     * 异步启动全量索引
     *
     * <p>方法上的 {@code @Async("agentExecutor")} 使本方法在 agentExecutor 线程池中执行，
     * 调用方（Controller）立即返回，不阻塞 HTTP 线程。
     *
     * <p>索引流程：
     * <ol>
     *   <li>更新项目状态为 INDEXING</li>
     *   <li>扫描所有源码文件</li>
     *   <li>逐文件对比哈希，新增/更新/删除</li>
     *   <li>更新项目状态为 INDEXED（或 ERROR）</li>
     * </ol>
     */
        @Async("agentExecutor")
    public void startFullIndex(String projectId) {
        log.info("[索引] 开始全量索引: projectId={}", projectId);

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> PiaException.projectNotFound(projectId));

        // 更新状态为"索引中"
        project.setStatus(Project.ProjectStatus.INDEXING);
        projectRepository.save(project);

        try {
            // Phase 1: 文件扫描
            List<FileScanner.ScannedFile> scannedFiles = fileScanner.scan(project.getSourcePath());
            log.info("[索引] 扫描完成: {} 个文件", scannedFiles.size());

            // Phase 1: 同步到数据库
            syncFiles(projectId, scannedFiles);

            // Phase 2: AST 解析 + Code Graph 构建
            // graphService.buildGraph(projectId, project.getSourcePath());
            // TODO: Phase 2 实装后取消注释。当前 GraphService 已实现，
            //       取消注释即可激活。拆成两行是为了让日志清晰区分两个阶段。

            // Phase 3 占位：Embedding（后续 Phase 3 实现后在此处调用）
            // embeddingService.embedAll(projectId);

            // 更新状态为"已索引"
            project.setStatus(Project.ProjectStatus.INDEXED);
            projectRepository.save(project);
            log.info("[索引] 全量索引完成: projectId={}, 文件数={}", projectId, scannedFiles.size());

        } catch (Exception e) {
            log.error("[索引] 索引失败: projectId={}", projectId, e);
            project.setStatus(Project.ProjectStatus.ERROR);
            projectRepository.save(project);
        }
    }

    /**
     * 将扫描结果同步到数据库（实现增量更新逻辑）
     *
     * <p>增量更新策略：
     * <ul>
     *   <li>新文件（数据库中没有）→ INSERT</li>
     *   <li>已有文件但哈希变化 → UPDATE（文件内容变了）</li>
     *   <li>已有文件且哈希相同 → 跳过（文件未变）</li>
     *   <li>数据库中有但扫描时没找到 → DELETE（文件被删除了）</li>
     * </ul>
     */
    @Transactional
    protected void syncFiles(String projectId, List<FileScanner.ScannedFile> scannedFiles) {
        // 查询数据库中现有的文件记录，按路径建索引（便于快速查找）
                Map<String, ProjectFile> existingByPath = projectFileRepository
            .findByProjectId(projectId)
            .stream()
            .collect(Collectors.toMap(ProjectFile::getPath, Function.identity()));

        int added = 0, updated = 0, unchanged = 0;

        for (FileScanner.ScannedFile scanned : scannedFiles) {
            ProjectFile existing = existingByPath.remove(scanned.relativePath());

            if (existing == null) {
                // 新文件：INSERT
                ProjectFile newFile = ProjectFile.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .path(scanned.relativePath())
                    .language(scanned.language())
                    .fileHash(scanned.hash())
                    .sizeBytes(scanned.sizeBytes())
                    .lastIndexedAt(LocalDateTime.now())
                    .build();
                projectFileRepository.save(newFile);
                   added++;
            } else if (!scanned.hash().equals(existing.getFileHash())) {
                // 文件内容变化：UPDATE
                existing.setFileHash(scanned.hash());
                existing.setSizeBytes(scanned.sizeBytes());
                existing.setLastIndexedAt(LocalDateTime.now());
                projectFileRepository.save(existing);
                updated++;
            } else {
                // 文件未变：跳过
                unchanged++;
            }
        }

        // 数据库中存在但扫描时未找到的文件 → 已被删除，从数据库中移除
        if (!existingByPath.isEmpty()) {
            projectFileRepository.deleteAllById(
                existingByPath.values().stream().map(ProjectFile::getId).toList()
            );
            log.info("[索引] 删除已不存在的文件记录: {} 个", existingByPath.size());
        }

        log.info("[索引] 文件同步完成: 新增={}, 更新={}, 未变={}, 删除={}",
            added, updated, unchanged, existingByPath.size());
    }

    /**
     * 查询索引状态（前端轮询用）
     */
    @Transactional(readOnly = true)
    public IndexStatus getIndexStatus(String projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> PiaException.projectNotFound(projectId));

        long fileCount = projectFileRepository.countByProjectId(projectId);

        return new IndexStatus(
            project.getId(),
            project.getStatus().name(),
            fileCount,
            project.getUpdatedAt()
        );
    }

    /** 索引状态响应 */
    public record IndexStatus(
        String projectId,
        String status,
        long indexedFileCount,
        LocalDateTime updatedAt
    ) {}
}
