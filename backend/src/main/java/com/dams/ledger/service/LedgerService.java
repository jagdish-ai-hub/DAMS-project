package com.dams.ledger.service;

import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.service.CustomerService;
import com.dams.ledger.dto.CustomerStatementResponse;
import com.dams.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer ledger statements (FEAT-43). Read-only composition over the
 * customer history card — dues, payments and balance in one shareable view.
 * Branch visibility applies: with the cashier toggle OFF a cashier sees only
 * their own branch's customers (enforced inside history loading).
 */
@Service
public class LedgerService {

    private final CustomerService customerService;
    private final BranchScope branchScope;
    private final AppUserRepository userRepo;

    public LedgerService(CustomerService customerService, BranchScope branchScope,
                         AppUserRepository userRepo) {
        this.customerService = customerService;
        this.branchScope = branchScope;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public CustomerStatementResponse statement(Long customerId) {
        Long orgId = TenantContext.requireOrgId();
        CustomerHistoryResponse history = customerService.history(customerId);
        String actorName = userRepo.findNameByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElse("DAMS");
        return new CustomerStatementResponse(
            customerId, history.customerName(), history.phone(), OrgTime.today(), actorName,
            history.totalInvoiced(), history.totalReceived(), history.totalOutstanding(),
            history.jobCards(), history.timeline());
    }
}
