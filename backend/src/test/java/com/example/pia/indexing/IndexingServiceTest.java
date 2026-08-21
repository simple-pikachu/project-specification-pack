package com.example.pia.indexing;

import com.example.pia.indexing.domain.ProjectFile;
import com.example.pia.indexing.repository.ProjectFileRepository;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IndexingService 单元测试
 *
 * <p>重点测试增量索引逻辑（syncFiles）：
 * 新文件→INSERT，变更文件→UPDATE，未变文件→跳过，消失文件→DELETE。
 * 这是 Phase 1 的核心业务逻辑，必须完整覆盖。
 */
@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectFileRepository projectFileRepository;
    @Mock
    private FileScanner fileScanner;

    @InjectMocks
    private IndexingService indexingService;

    private static final String PROJECT_ID = "proj-001";

    @BeforeEach
    void setup() {
        // 默认：项目存在
        Project project = Project.builder()
            .id(PROJECT_ID)
            .name("test-project")
            .sourceType(Project.SourceType.LOCAL)
            .sourcePath("/tmp/test-project")
            .status(Project.ProjectStatus.CREATED)
            .build();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("syncFiles：全新文件，应全部 INSERT")
    void syncFiles_allNewFiles_shouldInsertAll() {
        // Given：数据库中没有文件，扫描到 2 个新文件
                when(projectFileRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

        List<FileScanner.ScannedFile> scanned = List.of(
            new FileScanner.ScannedFile("src/OrderService.java", LanguageDetector.Language.JAVA, "hash1", 1000L),
            new FileScanner.ScannedFile("frontend/App.vue", LanguageDetector.Language.VUE, "hash2", 500L)
        );

        // When
        indexingService.syncFiles(PROJECT_ID, scanned);

        // Then：应 save 2 次（每个新文件一次 INSERT）
        verify(projectFileRepository, times(2)).save(any(ProjectFile.class));
    }

    @Test
    @DisplayName("syncFiles：文件哈希变更，应 UPDATE")
    void syncFiles_fileHashChanged_shouldUpdate() {
        // Given：数据库中有旧记录（旧哈希）
        ProjectFile existing = ProjectFile.builder()
            .id("file-001")
            .projectId(PROJECT_ID)
            .path("src/OrderService.java")
            .language(LanguageDetector.Language.JAVA)
            .fileHash("old-hash")
                        .build();
        when(projectFileRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(existing));

        // 扫描到新哈希（文件内容变了）
        List<FileScanner.ScannedFile> scanned = List.of(
            new FileScanner.ScannedFile("src/OrderService.java", LanguageDetector.Language.JAVA, "new-hash", 1100L)
        );

        // When
        indexingService.syncFiles(PROJECT_ID, scanned);

        // Then：验证 save 被调用，且保存的对象哈希已更新
        ArgumentCaptor<ProjectFile> captor = ArgumentCaptor.forClass(ProjectFile.class);
        verify(projectFileRepository).save(captor.capture());
        assertThat(captor.getValue().getFileHash()).isEqualTo("new-hash");
    }

    @Test
    @DisplayName("syncFiles：文件哈希未变，应跳过（不调用 save）")
    void syncFiles_hashUnchanged_shouldSkip() {
        // Given：数据库和扫描结果哈希相同
        ProjectFile existing = ProjectFile.builder()
            .id("file-001")
            .projectId(PROJECT_ID)
            .path("src/OrderService.java")
              .fileHash("same-hash")
            .language(LanguageDetector.Language.JAVA)
            .build();
        when(projectFileRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(existing));

        List<FileScanner.ScannedFile> scanned = List.of(
            new FileScanner.ScannedFile("src/OrderService.java", LanguageDetector.Language.JAVA, "same-hash", 1000L)
        );

        // When
        indexingService.syncFiles(PROJECT_ID, scanned);

        // Then：哈希相同，不调用 save
        verify(projectFileRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFiles：文件被删除，应从数据库移除")
    void syncFiles_fileDeleted_shouldRemoveFromDb() {
        // Given：数据库中有文件，但这次扫描没扫到（文件被删了）
        ProjectFile deleted = ProjectFile.builder()
            .id("file-001")
            .projectId(PROJECT_ID)
            .path("src/DeletedService.java")
            .fileHash("old-hash")
            .language(LanguageDetector.Language.JAVA)
                        .build();
        when(projectFileRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(deleted));

        // 扫描结果为空（或没有这个文件）
        List<FileScanner.ScannedFile> scanned = List.of();

        // When
        indexingService.syncFiles(PROJECT_ID, scanned);

        // Then：应调用 deleteAllById 删除该文件记录
        verify(projectFileRepository).deleteAllById(argThat(ids ->
            ids.iterator().next().equals("file-001")
        ));
    }
}
