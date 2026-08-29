package com.dams.admin;

import com.dams.admin.service.AdminOrgService;
import com.dams.admin.service.OrganizationPurgeService;
import com.dams.common.exception.DamsException;
import com.dams.email.EmailService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminOrgService — real service logic, mocked collaborators.
 * Test names describe the behaviour being proven (see AGENT.md coding standards).
 */
@ExtendWith(MockitoExtension.class)
class AdminOrgServiceTest {

    private static final String APP_BASE_URL = "http://localhost:5173";

    @Mock
    private OrganizationRepository orgRepo;

    @Mock
    private AppUserRepository userRepo;

    @Mock
    private EmailService emailService;

    @Mock
    private OrganizationPurgeService purgeService;

    private AdminOrgService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrgService(orgRepo, userRepo, emailService, purgeService, APP_BASE_URL);
    }

    @Test
    void createOrganization_createsOrgAndOwnerUser_withInviteToken() {
        when(userRepo.findByEmail("owner@testorg.com")).thenReturn(Optional.empty());

        Organization savedOrg = new Organization("Test Org");
        ReflectionTestUtils.setField(savedOrg, "id", 42L);
        when(orgRepo.save(any(Organization.class))).thenReturn(savedOrg);

        AppUser savedUser = new AppUser();
        ReflectionTestUtils.setField(savedUser, "id", 7L);
        when(userRepo.save(any(AppUser.class))).thenReturn(savedUser);

        AdminOrgService.OrgCreationResult result = service.createOrganization(
            "Test Org", "Test Owner", "owner@testorg.com");

        assertThat(result.orgId()).isEqualTo(42L);
        assertThat(result.orgName()).isEqualTo("Test Org");
        assertThat(result.inviteToken()).isNotBlank();
        assertThat(result.inviteLink())
            .startsWith(APP_BASE_URL + "/accept-invite?token=")
            .endsWith(result.inviteToken());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepo).save(userCaptor.capture());
        AppUser captured = userCaptor.getValue();
        assertThat(captured.getRole()).isEqualTo(Role.OWNER);
        assertThat(captured.getEmail()).isEqualTo("owner@testorg.com");
        assertThat(captured.getInviteToken()).isNotBlank();
        assertThat(captured.getPasswordHash()).isNull();
        assertThat(captured.isActive()).isTrue();
    }

    @Test
    void createOrganization_handsInviteLinkToEmailService() {
        when(userRepo.findByEmail("owner@testorg.com")).thenReturn(Optional.empty());
        Organization savedOrg = new Organization("Test Org");
        ReflectionTestUtils.setField(savedOrg, "id", 1L);
        when(orgRepo.save(any(Organization.class))).thenReturn(savedOrg);
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrgService.OrgCreationResult result =
            service.createOrganization("Test Org", "Test Owner", "owner@testorg.com");

        verify(emailService).sendOwnerInvite(eq("owner@testorg.com"), eq("Test Org"), eq(result.inviteLink()));
    }

    @Test
    void createOrganization_inviteTokenExpiresAboutSevenDaysOut() {
        when(userRepo.findByEmail("owner@testorg.com")).thenReturn(Optional.empty());
        Organization savedOrg = new Organization("Test Org");
        ReflectionTestUtils.setField(savedOrg, "id", 1L);
        when(orgRepo.save(any(Organization.class))).thenReturn(savedOrg);
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now().plusSeconds(7L * 24 * 3600 - 5);
        service.createOrganization("Test Org", "Test Owner", "owner@testorg.com");
        Instant after = Instant.now().plusSeconds(7L * 24 * 3600 + 5);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepo).save(userCaptor.capture());
        Instant expiresAt = userCaptor.getValue().getInviteExpiresAt();

        assertThat(expiresAt).isAfter(before).isBefore(after);
    }

    @Test
    void createOrganization_throwsConflict_whenEmailAlreadyExists() {
        when(userRepo.findByEmail("existing@dams.local")).thenReturn(Optional.of(new AppUser()));

        assertThatThrownBy(() ->
            service.createOrganization("Org X", "Owner X", "existing@dams.local"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("existing@dams.local");

        // No org row must be created if the email is taken
        verify(orgRepo, never()).save(any());
    }

    @Test
    void getOrganization_throwsNotFound_whenIdDoesNotExist() {
        when(orgRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrganization(999L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Organization")
            .hasMessageContaining("999");
    }

    @Test
    void toggleActive_setsActiveFalse_whenCalledWithFalse() {
        Organization org = new Organization("Test Org");
        org.setActive(true);
        ReflectionTestUtils.setField(org, "id", 1L);
        when(orgRepo.findById(1L)).thenReturn(Optional.of(org));
        when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization result = service.toggleActive(1L, false);

        assertThat(result.isActive()).isFalse();
    }
}
