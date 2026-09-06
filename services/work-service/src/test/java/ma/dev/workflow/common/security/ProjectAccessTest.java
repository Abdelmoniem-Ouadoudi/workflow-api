package ma.dev.workflow.common.security;

import ma.dev.workflow.common.exception.ForbiddenException;
import ma.dev.workflow.project.models.ProjectMember;
import ma.dev.workflow.project.models.enums.ProjectRole;
import ma.dev.workflow.project.repositories.ProjectMemberRepository;
import ma.dev.workflow.user.models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may touch which project.
 *
 * <p>Worth testing for the same reason as the status transition map: none of it is in the schema.
 * A membership row says somebody is on a project; nothing in the database says that reading an
 * issue requires one. If these rules break, nothing fails — the wrong person simply sees more than
 * they should, quietly, and the only symptom is a screen that looks fine.
 */
class ProjectAccessTest {

    private static final Long ME = 7L;
    private static final Long MY_PROJECT = 1L;
    private static final Long SOMEBODY_ELSES = 2L;

    private CurrentUser currentUser;
    private ProjectMemberRepository memberRepository;
    private ProjectAccess access;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUser.class);
        memberRepository = mock(ProjectMemberRepository.class);
        access = new ProjectAccess(currentUser, memberRepository);

        when(currentUser.requireId()).thenReturn(ME);
    }

    @Nested
    @DisplayName("an ordinary member")
    class OrdinaryMember {

        @BeforeEach
        void notAnAdmin() {
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.role()).thenReturn(Optional.of(Role.DEVELOPER));
        }

        @Test
        @DisplayName("may read a project they are on")
        void passesOnTheirOwnProject() {
            when(memberRepository.existsByProjectIdAndUserId(MY_PROJECT, ME)).thenReturn(true);

            assertThatCode(() -> access.requireMember(MY_PROJECT)).doesNotThrowAnyException();
        }

        /** The check the whole feature rests on. */
        @Test
        @DisplayName("may not read a project they are not on")
        void refusesSomebodyElsesProject() {
            when(memberRepository.existsByProjectIdAndUserId(SOMEBODY_ELSES, ME)).thenReturn(false);

            assertThatThrownBy(() -> access.requireMember(SOMEBODY_ELSES))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo("NOT_A_MEMBER");
        }

        @Test
        @DisplayName("being on a project does not make you its manager")
        void refusesManagementWhenOnlyAMember() {
            when(memberRepository.findByProjectIdAndUserId(MY_PROJECT, ME))
                    .thenReturn(Optional.of(membership(ProjectRole.MEMBER)));

            assertThatThrownBy(() -> access.requireProjectManager(MY_PROJECT))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo("NOT_PROJECT_MANAGER");
        }

        @Test
        @DisplayName("a DEVELOPER cannot start a project")
        void refusesProjectCreation() {
            assertThatThrownBy(() -> access.requireCanCreateProjects())
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo("CANNOT_CREATE_PROJECTS");
        }
    }

    @Nested
    @DisplayName("the chef de projet")
    class ProjectManager {

        @BeforeEach
        void notAnAdmin() {
            when(currentUser.isAdmin()).thenReturn(false);
        }

        @Test
        @DisplayName("may manage the project they run")
        void passesOnTheirOwnProject() {
            when(memberRepository.findByProjectIdAndUserId(MY_PROJECT, ME))
                    .thenReturn(Optional.of(membership(ProjectRole.PROJECT_MANAGER)));

            assertThatCode(() -> access.requireProjectManager(MY_PROJECT)).doesNotThrowAnyException();
        }

        /**
         * The rule that keeps the role useful. Running one project must not be a way into another,
         * or "chef de projet" would quietly mean "administrator of everything".
         */
        @Test
        @DisplayName("may not manage a project they are not on")
        void refusesAProjectTheyAreNotOn() {
            when(memberRepository.findByProjectIdAndUserId(SOMEBODY_ELSES, ME))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> access.requireProjectManager(SOMEBODY_ELSES))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("an administrator")
    class Administrator {

        @BeforeEach
        void isAnAdmin() {
            when(currentUser.isAdmin()).thenReturn(true);
            when(currentUser.role()).thenReturn(Optional.of(Role.ADMIN));
        }

        /**
         * An administrator is normally a member of no project at all. Giving them a membership row
         * in every project would be a lie that needs maintaining, and one that breaks the moment
         * somebody creates a project. It is a short-circuit instead — and the repository is never
         * consulted, which is what these two assert by leaving it unstubbed.
         */
        @Test
        @DisplayName("passes without a membership row anywhere")
        void bypassesMembership() {
            assertThatCode(() -> access.requireMember(SOMEBODY_ELSES)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("may manage any project")
        void bypassesProjectManagement() {
            assertThatCode(() -> access.requireProjectManager(SOMEBODY_ELSES))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("may create a project")
        void mayCreateProjects() {
            assertThatCode(() -> access.requireCanCreateProjects()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a MANAGER may create a project")
    void managerMayCreateProjects() {
        when(currentUser.role()).thenReturn(Optional.of(Role.MANAGER));

        assertThatCode(() -> access.requireCanCreateProjects()).doesNotThrowAnyException();
    }

    /**
     * A service token has {@code role=SERVICE} and no {@code uid}. It is not a person, so it is
     * not an administrator either — parsing SERVICE as a Role would throw, and answering "yes" to
     * isAdmin would give every internal call the run of the system.
     */
    @Test
    @DisplayName("a service token cannot create projects")
    void serviceTokenCannotCreateProjects() {
        when(currentUser.role()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireCanCreateProjects())
                .isInstanceOf(ForbiddenException.class)
                .extracting(ex -> ((ForbiddenException) ex).getCode())
                .isEqualTo("NOT_A_USER");
    }

    private ProjectMember membership(ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setRole(role);
        return member;
    }
}
