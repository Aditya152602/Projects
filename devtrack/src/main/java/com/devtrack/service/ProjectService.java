package com.devtrack.service;

import com.devtrack.dto.*;
import com.devtrack.entity.Project;
import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.exception.ApiException;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.TaskRepository;
import com.devtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public ProjectResponse create(ProjectRequest req, User current) {
        Project project = new Project();
        project.setName(req.name().trim());
        project.setDescription(req.description());
        project.setOwner(current);                                     // creator becomes owner
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)                                    // read-only hint = cheaper tx
    public PageResponse<ProjectResponse> list(User current, Pageable pageable) {
        Page<Project> page = current.getRole() == Role.ADMIN
                ? projectRepository.findAll(pageable)                  // admin sees everything
                : projectRepository.findAccessible(current, pageable); // user sees own + member projects
        return PageResponse.from(page.map(ProjectResponse::from));     // entity -> DTO inside the transaction
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id, User current) {
        return ProjectResponse.from(findProjectForUser(id, current));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest req, User current) {
        Project project = findProjectForUser(id, current);
        requireOwnerOrAdmin(project, current);                         // members cannot edit project itself
        project.setName(req.name().trim());
        project.setDescription(req.description());
        return ProjectResponse.from(project);                          // no save() needed: managed entity auto-flushes
    }

    @Transactional
    public void delete(Long id, User current) {
        Project project = findProjectForUser(id, current);
        requireOwnerOrAdmin(project, current);
        projectRepository.delete(project);                             // cascades to its tasks
    }

    @Transactional
    public ProjectResponse addMember(Long id, MemberRequest req, User current) {
        Project project = findProjectForUser(id, current);
        requireOwnerOrAdmin(project, current);
        User member = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("No user with that email"));
        if (isParticipant(project, member)) {
            throw ApiException.conflict("User is already in this project");
        }
        project.getMembers().add(member);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse removeMember(Long id, Long userId, User current) {
        Project project = findProjectForUser(id, current);
        requireOwnerOrAdmin(project, current);
        if (project.getOwner().getId().equals(userId)) {
            throw ApiException.badRequest("Owner cannot be removed");
        }
        taskRepository.unassignUserFromProject(id, userId);            // their tasks become unassigned
        project.getMembers().removeIf(m -> m.getId().equals(userId));
        return ProjectResponse.from(project);
    }

    // ---- shared helpers (TaskService reuses these) ----

    /** Load project or 404; then 403 unless user is admin, owner or member. */
    @Transactional(readOnly = true)
    public Project findProjectForUser(Long id, User current) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (current.getRole() != Role.ADMIN && !isParticipant(project, current)) {
            throw ApiException.forbidden("You do not have access to this project");
        }
        return project;
    }

    public boolean isParticipant(Project project, User user) {
        return project.getOwner().getId().equals(user.getId())
                || project.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
    }

    public void requireOwnerOrAdmin(Project project, User user) {
        boolean owner = project.getOwner().getId().equals(user.getId());
        if (!owner && user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only the project owner can do this");
        }
    }
}
