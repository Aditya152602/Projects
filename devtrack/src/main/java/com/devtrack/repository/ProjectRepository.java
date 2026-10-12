package com.devtrack.repository;

import com.devtrack.entity.Project;
import com.devtrack.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Projects the user owns OR is a member of. Pageable adds LIMIT/OFFSET/ORDER BY automatically
    @Query("select p from Project p where p.owner = :user or :user member of p.members")
    Page<Project> findAccessible(@Param("user") User user, Pageable pageable);
}
