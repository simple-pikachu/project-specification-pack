package com.example.pia.project.repository;

import com.example.pia.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目数据访问层
 *
 * <p>继承 {@link JpaRepository} 自动获得 CRUD 操作：
 * save(), findById(), findAll(), deleteById() 等。
 * 自定义查询方法通过方法命名约定自动生成 SQL。
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    /** 查询指定状态的项目列表（用于管理界面展示） */
    List<Project> findByStatusOrderByCreatedAtDesc(Project.ProjectStatus status);

    /** 检查项目名称是否已存在 */
    boolean existsByName(String name);
}
