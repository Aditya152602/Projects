package com.devtrack.repository;

import com.devtrack.entity.Task;
import com.devtrack.entity.TaskPriority;
import com.devtrack.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Tasks of one project; status/priority filters are optional (null = ignore filter)
    @Query("select t from Task t where t.project.id = :projectId "
         + "and (:status is null or t.status = :status) "
         + "and (:priority is null or t.priority = :priority)")
    Page<Task> search(@Param("projectId") Long projectId,
                      @Param("status") TaskStatus status,
                      @Param("priority") TaskPriority priority,
                      Pageable pageable);

    // Tasks assigned to one user across all projects, optional status filter
    @Query("select t from Task t where t.assignee.id = :userId "
         + "and (:status is null or t.status = :status)")
    Page<Task> findAssignedTo(@Param("userId") Long userId,
                              @Param("status") TaskStatus status,
                              Pageable pageable);

    // Bulk unassign when a member leaves a project (one UPDATE, no loading rows)
    @Modifying(flushAutomatically = true)
    @Query("update Task t set t.assignee = null where t.project.id = :projectId and t.assignee.id = :userId")
    void unassignUserFromProject(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
