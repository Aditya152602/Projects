package com.devtrack.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskIntegrationTest extends IntegrationTestBase {

    private String ownerToken;
    private long ownerId;
    private long projectId;

    @BeforeEach
    void setUp() throws Exception {                                      // fresh owner + project per test
        ownerToken = registerAndLogin(uniqueEmail("owner"));
        ownerId = meId(ownerToken);
        projectId = createProject(ownerToken, "Board");
    }

    @Test
    void create_appliesDefaults_todoAndMedium() throws Exception {
        doPost("/api/projects/" + projectId + "/tasks", ownerToken, taskBody("First", null, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void create_blankTitle_returns400() throws Exception {
        doPost("/api/projects/" + projectId + "/tasks", ownerToken, taskBody("", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void create_invalidEnumValue_returns400() throws Exception {
        doPost("/api/projects/" + projectId + "/tasks", ownerToken, taskBody("T", "DONE_ISH", null, null))
                .andExpect(status().isBadRequest());                     // not TODO/IN_PROGRESS/COMPLETED
    }

    @Test
    void create_onForeignProject_returns403() throws Exception {
        String stranger = registerAndLogin(uniqueEmail("stranger"));
        doPost("/api/projects/" + projectId + "/tasks", stranger, taskBody("T", null, null, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_assigneeMustBeProjectParticipant() throws Exception {
        String otherEmail = uniqueEmail("other");
        String other = registerAndLogin(otherEmail);
        long otherId = meId(other);

        doPost("/api/projects/" + projectId + "/tasks", ownerToken, taskBody("T", null, null, otherId))
                .andExpect(status().isBadRequest());                     // not in project yet

        addMember(ownerToken, projectId, otherEmail);
        doPost("/api/projects/" + projectId + "/tasks", ownerToken, taskBody("T", null, null, otherId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.email").value(otherEmail));
    }

    @Test
    void list_filtersByStatus() throws Exception {
        createTask(ownerToken, projectId, taskBody("a", "TODO", null, null));
        createTask(ownerToken, projectId, taskBody("b", "TODO", null, null));
        createTask(ownerToken, projectId, taskBody("c", "IN_PROGRESS", null, null));

        doGet("/api/projects/" + projectId + "/tasks?status=IN_PROGRESS", ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("c"));
    }

    @Test
    void list_filtersByPriority_andCombinesWithStatus() throws Exception {
        createTask(ownerToken, projectId, taskBody("hi-todo", "TODO", "HIGH", null));
        createTask(ownerToken, projectId, taskBody("lo-todo", "TODO", "LOW", null));
        createTask(ownerToken, projectId, taskBody("hi-done", "COMPLETED", "HIGH", null));

        doGet("/api/projects/" + projectId + "/tasks?status=TODO&priority=HIGH", ownerToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("hi-todo"));
    }

    @Test
    void list_paginates() throws Exception {
        for (String t : new String[]{"t1", "t2", "t3"}) createTask(ownerToken, projectId, taskBody(t, null, null, null));

        doGet("/api/projects/" + projectId + "/tasks?page=0&size=2", ownerToken)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
        doGet("/api/projects/" + projectId + "/tasks?page=1&size=2", ownerToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void list_sortsByTitleAscending() throws Exception {
        createTask(ownerToken, projectId, taskBody("b-task", null, null, null));
        createTask(ownerToken, projectId, taskBody("a-task", null, null, null));

        doGet("/api/projects/" + projectId + "/tasks?sort=title,asc", ownerToken)
                .andExpect(jsonPath("$.content[0].title").value("a-task"))
                .andExpect(jsonPath("$.content[1].title").value("b-task"));
    }

    @Test
    void list_invalidSortField_returns400() throws Exception {
        doGet("/api/projects/" + projectId + "/tasks?sort=doesNotExist,asc", ownerToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_invalidStatusFilter_returns400() throws Exception {
        doGet("/api/projects/" + projectId + "/tasks?status=NOPE", ownerToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    void myTasks_returnsOnlyTasksAssignedToMe() throws Exception {
        createTask(ownerToken, projectId, taskBody("mine", null, null, ownerId));
        createTask(ownerToken, projectId, taskBody("unassigned", null, null, null));

        doGet("/api/tasks", ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("mine"));
    }

    @Test
    void update_changesFields() throws Exception {
        long tid = createTask(ownerToken, projectId, taskBody("old", "TODO", "LOW", null));
        doPut("/api/tasks/" + tid, ownerToken, taskBody("new", "COMPLETED", "HIGH", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("new"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assignee.id").value(ownerId));
    }

    @Test
    void update_withoutAssignee_unassignsTask() throws Exception {
        long tid = createTask(ownerToken, projectId, taskBody("t", null, null, ownerId));
        doPut("/api/tasks/" + tid, ownerToken, taskBody("t", null, null, null))   // full-replace semantics
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    void stranger_cannotReadUpdateOrDeleteTask() throws Exception {
        long tid = createTask(ownerToken, projectId, taskBody("secret", null, null, null));
        String stranger = registerAndLogin(uniqueEmail("stranger"));
        doGet("/api/tasks/" + tid, stranger).andExpect(status().isForbidden());
        doPut("/api/tasks/" + tid, stranger, taskBody("x", null, null, null)).andExpect(status().isForbidden());
        doDelete("/api/tasks/" + tid, stranger).andExpect(status().isForbidden());
    }

    @Test
    void member_canUpdateTask_butOnlyOwnerCanDelete() throws Exception {
        String memberEmail = uniqueEmail("member");
        String member = registerAndLogin(memberEmail);
        addMember(ownerToken, projectId, memberEmail);
        long tid = createTask(ownerToken, projectId, taskBody("t", null, null, null));

        doPut("/api/tasks/" + tid, member, taskBody("t", "IN_PROGRESS", null, null)).andExpect(status().isOk());
        doDelete("/api/tasks/" + tid, member).andExpect(status().isForbidden());
        doDelete("/api/tasks/" + tid, ownerToken).andExpect(status().isNoContent());
        doGet("/api/tasks/" + tid, ownerToken).andExpect(status().isNotFound());
    }

    @Test
    void unknownTask_returns404() throws Exception {
        doGet("/api/tasks/999999999", ownerToken).andExpect(status().isNotFound());
    }
}
