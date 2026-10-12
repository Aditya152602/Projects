package com.devtrack.integration;

import com.devtrack.entity.*;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.TaskRepository;
import com.devtrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

/** Talks to real MySQL through repositories: proves constraints, join tables and JPQL work on the real dialect. */
class PersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TaskRepository taskRepository;

    private User saveUser(String email) {
        return userRepository.save(User.builder().name("U").email(email).password("hash").role(Role.USER).build());
    }

    private Project saveProject(User owner, String name, User... members) {
        Project p = new Project();
        p.setName(name);
        p.setOwner(owner);
        for (User m : members) p.getMembers().add(m);   // owning side of ManyToMany -> fills project_members
        return projectRepository.save(p);
    }

    @Test
    void duplicateEmail_isRejectedByDatabaseUniqueConstraint() {
        String email = uniqueEmail("unique");
        saveUser(email);
        // bypasses the service-level check: the DB itself must refuse the second row
        assertThrows(DataIntegrityViolationException.class, () -> saveUser(email));
    }

    @Test
    void findAccessible_returnsOwnedAndMemberProjects_onMysql() {
        User a = saveUser(uniqueEmail("a"));
        User b = saveUser(uniqueEmail("b"));
        saveProject(a, "owned-by-a");
        saveProject(b, "owned-by-b-with-a", a);          // a is member
        saveProject(b, "owned-by-b-only");               // a has no access

        // exercises "p.owner = :user or :user member of p.members" + count query on real MySQL
        var page = projectRepository.findAccessible(a, PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void taskSearch_nullFilters_workOnMysql() {
        User owner = saveUser(uniqueEmail("owner"));
        Project project = saveProject(owner, "P");
        for (TaskStatus st : new TaskStatus[]{TaskStatus.TODO, TaskStatus.COMPLETED}) {
            Task t = new Task();
            t.setTitle("t-" + st);
            t.setStatus(st);
            t.setProject(project);
            taskRepository.save(t);
        }
        // both filters null = "(:status is null or ...)" branch -> must not blow up with a typed-null parameter
        assertEquals(2, taskRepository.search(project.getId(), null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, taskRepository.search(project.getId(), TaskStatus.TODO, null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void unassignUserFromProject_bulkUpdate_setsAssigneeNull() {
        User owner = saveUser(uniqueEmail("owner"));
        User member = saveUser(uniqueEmail("member"));
        Project project = saveProject(owner, "P", member);
        Task t = new Task();
        t.setTitle("x");
        t.setProject(project);
        t.setAssignee(member);
        Task saved = taskRepository.save(t);

        taskRepository.unassignUserFromProject(project.getId(), member.getId());

        // re-read from DB: findAssignedTo for member must now be empty
        assertEquals(0, taskRepository.findAssignedTo(member.getId(), null, PageRequest.of(0, 10)).getTotalElements());
        assertTrue(taskRepository.existsById(saved.getId()));   // task itself survives
    }
}
