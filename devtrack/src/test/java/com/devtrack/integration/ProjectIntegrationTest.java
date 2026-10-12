package com.devtrack.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectIntegrationTest extends IntegrationTestBase {

    @Test
    void anonymous_cannotCreateProject() throws Exception {
        doPost("/api/projects", null, Map.of("name", "X")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        String token = registerAndLogin(uniqueEmail("owner"));
        doPost("/api/projects", token, Map.of("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void owner_canCreateAndGetProject() throws Exception {
        String email = uniqueEmail("owner");
        String token = registerAndLogin(email);
        long id = createProject(token, "Alpha");
        doGet("/api/projects/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"))
                .andExpect(jsonPath("$.owner.email").value(email));
    }

    @Test
    void stranger_getsForbidden_onSomeoneElsesProject() throws Exception {
        // IDOR check: logged-in user must NOT read a project by guessing its id
        String owner = registerAndLogin(uniqueEmail("owner"));
        String stranger = registerAndLogin(uniqueEmail("stranger"));
        long id = createProject(owner, "Private");
        doGet("/api/projects/" + id, stranger).andExpect(status().isForbidden());
    }

    @Test
    void unknownProject_returns404() throws Exception {
        String token = registerAndLogin(uniqueEmail("owner"));
        doGet("/api/projects/999999999", token).andExpect(status().isNotFound());
    }

    @Test
    void list_returnsOnlyOwnAndMemberProjects_paged() throws Exception {
        String a = registerAndLogin(uniqueEmail("a"));
        String bEmail = uniqueEmail("b");
        String b = registerAndLogin(bEmail);
        createProject(a, "A1");
        long a2 = createProject(a, "A2");
        createProject(b, "B1");
        addMember(a, a2, bEmail);                                        // b joins A2

        doGet("/api/projects", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));        // A1, A2 (not B1)
        doGet("/api/projects", b).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));        // B1 (own) + A2 (member)
        doGet("/api/projects?page=0&size=1", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void member_canView_butCannotEditDeleteOrAddMembers() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        String memberEmail = uniqueEmail("member");
        String member = registerAndLogin(memberEmail);
        long id = createProject(owner, "Team");
        addMember(owner, id, memberEmail);

        doGet("/api/projects/" + id, member).andExpect(status().isOk());
        doPut("/api/projects/" + id, member, Map.of("name", "Hacked")).andExpect(status().isForbidden());
        doDelete("/api/projects/" + id, member).andExpect(status().isForbidden());
        doPost("/api/projects/" + id + "/members", member, Map.of("email", uniqueEmail("x")))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_canUpdateThenDelete_andProjectIsGone() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        long id = createProject(owner, "Old");
        doPut("/api/projects/" + id, owner, Map.of("name", "New", "description", "d"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New"));
        doDelete("/api/projects/" + id, owner).andExpect(status().isNoContent());
        doGet("/api/projects/" + id, owner).andExpect(status().isNotFound());
    }

    @Test
    void deleteProject_cascadesToTasks() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        long pid = createProject(owner, "WithTasks");
        long tid = createTask(owner, pid, taskBody("t", null, null, null));
        doDelete("/api/projects/" + pid, owner).andExpect(status().isNoContent());
        doGet("/api/tasks/" + tid, owner).andExpect(status().isNotFound()); // task died with project
    }

    @Test
    void addMember_unknownEmail_404_duplicate_409() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        String memberEmail = uniqueEmail("member");
        registerAndLogin(memberEmail);
        long id = createProject(owner, "P");

        doPost("/api/projects/" + id + "/members", owner, Map.of("email", uniqueEmail("nobody")))
                .andExpect(status().isNotFound());
        addMember(owner, id, memberEmail);
        doPost("/api/projects/" + id + "/members", owner, Map.of("email", memberEmail))
                .andExpect(status().isConflict());
    }

    @Test
    void removeMember_revokesAccess_andUnassignsTheirTasks() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        String memberEmail = uniqueEmail("member");
        String member = registerAndLogin(memberEmail);
        long memberId = meId(member);
        long pid = createProject(owner, "P");
        addMember(owner, pid, memberEmail);
        long tid = createTask(owner, pid, taskBody("assigned", null, null, memberId));

        doDelete("/api/projects/" + pid + "/members/" + memberId, owner).andExpect(status().isOk());

        doGet("/api/projects/" + pid, member).andExpect(status().isForbidden());   // access revoked
        doGet("/api/tasks/" + tid, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());                 // task unassigned
    }

    @Test
    void removeMember_ownerCannotBeRemoved_400() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        long ownerId = meId(owner);
        long pid = createProject(owner, "P");
        doDelete("/api/projects/" + pid + "/members/" + ownerId, owner).andExpect(status().isBadRequest());
    }
}
