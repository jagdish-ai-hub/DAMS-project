package com.dams.spareparts;

import com.dams.spareparts.entity.InvImport;
import com.dams.spareparts.entity.InvMovement;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvRolePermission;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.InvTransfer;
import com.dams.user.entity.Role;
import jakarta.persistence.Column;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules about the SHAPE of the module that must not drift (docs/modules/spare-parts.md, AGENT.md "Modules"):
 * every table is tenant-filtered, the ledger can never be edited, and the Finance core never reads the module.
 */
class SparePartsStructureTest {

    private static final List<Class<?>> TENANT_ENTITIES =
        List.of(InvPart.class, InvStock.class, InvMovement.class, InvImport.class, InvTransfer.class, InvRolePermission.class);

    @Test
    void everySparePartsTable_isFilteredByOrganization_soAnotherDealershipCanNeverReachIt() {
        for (Class<?> entity : TENANT_ENTITIES) {
            Filter filter = entity.getAnnotation(Filter.class);
            assertThat(filter).as(entity.getSimpleName() + " must carry the orgFilter").isNotNull();
            assertThat(filter.name()).isEqualTo("orgFilter");
            assertThat(filter.condition()).isEqualTo("org_id = :orgId");
        }
    }

    @Test
    void theLedgerRowCannotBeEdited_everyColumnIsMarkedNotUpdatable() throws Exception {
        for (Field field : InvMovement.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column == null || Modifier.isStatic(field.getModifiers())) {
                continue;   // the @Id has no @Column
            }
            assertThat(column.updatable()).as("inv_movement." + column.name() + " must be updatable=false").isFalse();
        }
    }

    @Test
    void theLedgerRepository_hasNoDeleteOrUpdateExceptTheOrganizationPurge() {
        List<String> methods = java.util.Arrays.stream(com.dams.spareparts.repository.InvMovementRepository.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName).toList();

        assertThat(methods).noneMatch(name -> name.startsWith("update") || (name.startsWith("delete") && !name.equals("deleteByOrgId")));
    }

    @Test
    void theFinanceCore_neverReadsTheSparePartsModule() throws Exception {
        // Allowed readers of com.dams.spareparts: the module itself, and the Super Admin purge (platform-level, cross-org).
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        var main = resolver.getResources("classpath*:com/dams/**/*.class");
        for (var resource : main) {
            String path = resource.getURL().toString();
            if (path.contains("/spareparts/") || path.contains("/admin/service/OrganizationPurgeService")) {
                continue;
            }
            if (path.contains("/test-classes/")) {
                continue;
            }
            String source = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(source).as(path + " must not depend on the spareparts package").doesNotContain("com/dams/spareparts");
        }
    }

    @Test
    void theTwoNewRoles_areBranchAssignedLikeAnAccountant_andNothingElseChanged() {
        assertThat(Role.INVENTORY_MANAGER.usesBranchAccessRows()).isTrue();
        assertThat(Role.STORE_KEEPER.usesBranchAccessRows()).isTrue();
        assertThat(Role.ACCOUNTANT.usesBranchAccessRows()).isTrue();
        assertThat(Role.FINANCE_MANAGER.usesBranchAccessRows()).isTrue();
        assertThat(Role.CASHIER.usesBranchAccessRows()).isFalse();
        assertThat(Role.OWNER.usesBranchAccessRows()).isFalse();
        assertThat(Role.INVENTORY_MANAGER.isSparePartsRole()).isTrue();
        assertThat(Role.CASHIER.isSparePartsRole()).isFalse();
    }
}
