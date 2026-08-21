package com.example.pia.api;

import com.example.pia.indexing.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 索引管理 REST API
 *
 * <p>【Agent 开发：为什么要有单独的索引 API？】
 *
 * <p>Agent 分析代码的前提是代码已被索引。
 * 索引是一个耗时操作（几秒到几分钟，取决于项目大小），
 * 不能同步等待，因此设计为：
 * <ul>
 *   <li>POST /index → 202 Accepted，后台异步索引</li>
 *   <li>GET /index/status → 轮询进度，直到 status=INDEXED</li>
 * </ul>
 *
 * <p>规格：docs/01-product-requirements.md FR-002（Repository Scan）
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class IndexingController {

    private final IndexingService indexingService;

    /**
     * POST /api/projects/{projectId}/index — 启动全量索引
     *
     * <p>返回 202 Accepted：索引任务已提交，在后台异步执行。
     * 调用方不需要等待索引完成。
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, String>> startIndex(@PathVariable String projectId) {
        indexingService.startFullIndex(projectId);
        return ResponseEntity.accepted()
            .body(Map.of(
                "projectId", projectId,
                "message", "索引任务已启动，请通过 GET /index/status 查询进度"
            ));
    }

    /**
     * GET /api/projects/{projectId}/index/status — 查询索引状态
     *
     * <p>前端每隔几秒轮询此接口，直到 status 变为 INDEXED 或 ERROR。
     */
    @GetMapping("/index/status")
    public IndexingService.IndexStatus getIndexStatus(@PathVariable String projectId) {
        return indexingService.getIndexStatus(projectId);
    }
}
