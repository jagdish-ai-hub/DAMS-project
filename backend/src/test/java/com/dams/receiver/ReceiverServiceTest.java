package com.dams.receiver;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.receiver.dto.ReceiverRequest;
import com.dams.receiver.dto.ReceiverResponse;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.receiver.service.ReceiverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Vendor ↔ expense-category mapping and the expense form's vendor search (rev 76). */
@ExtendWith(MockitoExtension.class)
class ReceiverServiceTest {

    private static final Long ORG = 1L;
    private static final Long FUEL = 10L;
    private static final Long SPARES = 11L;

    @Mock private ReceiverRepository receiverRepo;
    @Mock private ExpenseCategoryRepository categoryRepo;

    private ReceiverService service;

    @BeforeEach
    void setUp() {
        service = new ReceiverService(receiverRepo, categoryRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(receiverRepo.save(any(Receiver.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(categoryRepo.findByIdAndOrgId(eq(FUEL), eq(ORG))).thenReturn(Optional.of(new ExpenseCategory()));
        lenient().when(categoryRepo.findByIdAndOrgId(eq(SPARES), eq(ORG))).thenReturn(Optional.of(new ExpenseCategory()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void search_putsVendorsMappedToTheCategoryFirst_thenTheRest_byName() {
        Receiver fuelA = receiver(1L, "Zed Fuel Station", FUEL);
        Receiver spares = receiver(2L, "Alpha Spares", SPARES);
        Receiver fuelB = receiver(3L, "City Fuel", FUEL);
        when(receiverRepo.searchActive(eq(ORG), eq("e"), any())).thenReturn(List.of(spares, fuelB, fuelA)); // name order from the query

        List<ReceiverResponse> out = service.search(" e ", FUEL);

        assertThat(out).extracting(ReceiverResponse::name)
            .containsExactly("City Fuel", "Zed Fuel Station", "Alpha Spares");
    }

    @Test
    void search_withoutACategory_keepsPlainNameOrder() {
        Receiver b = receiver(1L, "Bravo", FUEL);
        Receiver a = receiver(2L, "Alpha", SPARES);
        when(receiverRepo.searchActive(eq(ORG), eq(""), any())).thenReturn(List.of(a, b));

        assertThat(service.search(null, null)).extracting(ReceiverResponse::name).containsExactly("Alpha", "Bravo");
    }

    @Test
    void update_replacesTheCategoryMapping() {
        Receiver r = receiver(1L, "Sharma Auto Spares", FUEL);
        when(receiverRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(r));
        ReceiverRequest req = request("Sharma Auto Spares", List.of(SPARES));

        ReceiverResponse res = service.update(1L, req);

        assertThat(res.categoryIds()).containsExactly(SPARES);
    }

    @Test
    void update_withNoCategoryList_leavesTheMappingAlone() {
        Receiver r = receiver(1L, "Sharma Auto Spares", FUEL, SPARES);
        when(receiverRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(r));

        ReceiverResponse res = service.update(1L, request("Sharma Auto Spares", null));

        assertThat(res.categoryIds()).containsExactly(FUEL, SPARES);
    }

    @Test
    void update_withAnEmptyList_clearsTheMapping() {
        Receiver r = receiver(1L, "Sharma Auto Spares", FUEL);
        when(receiverRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(r));

        assertThat(service.update(1L, request("Sharma Auto Spares", List.of())).categoryIds()).isEmpty();
    }

    @Test
    void update_withACategoryOfAnotherOrg_isRefused() {
        Receiver r = receiver(1L, "Sharma Auto Spares", FUEL);
        when(receiverRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(r));
        when(categoryRepo.findByIdAndOrgId(99L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request("Sharma Auto Spares", List.of(99L))))
            .isInstanceOf(DamsException.class);
        assertThat(r.getCategoryIds()).containsExactly(FUEL);
    }

    @Test
    void create_savesTheMapping() {
        ReceiverResponse res = service.create(request("New Vendor", List.of(FUEL, SPARES)));

        assertThat(res.categoryIds()).containsExactly(FUEL, SPARES);
    }

    private static Receiver receiver(Long id, String name, Long... categories) {
        Receiver r = new Receiver();
        ReflectionTestUtils.setField(r, "id", id);
        r.setOrgId(ORG);
        r.setName(name);
        r.getCategoryIds().addAll(List.of(categories));
        return r;
    }

    private static ReceiverRequest request(String name, List<Long> categoryIds) {
        ReceiverRequest req = new ReceiverRequest();
        req.setName(name);
        req.setCategoryIds(categoryIds);
        return req;
    }
}
