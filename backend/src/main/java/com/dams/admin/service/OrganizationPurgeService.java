package com.dams.admin.service;

import com.dams.attachment.repository.AttachmentRepository;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.repository.DocumentSequenceRepository;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.service.MastersService;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes every row belonging to one organization, in FK-safe order. Super Admin
 * only — lives in the admin package because it is one of the few operations that
 * deliberately reaches across the org boundary. See AGENT.md multi-tenancy.
 *
 * DAMS's general rule is deactivate-never-delete; this is the exception, for removing a
 * dealership that was onboarded by mistake. Once transactional documents exist (Stage 4+),
 * {@link com.dams.admin.service.AdminOrgService#deleteOrganization} guards against
 * deleting an org that has any.
 */
@Service
public class OrganizationPurgeService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationPurgeService.class);

    private final AppUserRepository userRepo;
    private final DocumentSequenceRepository documentSequenceRepo;
    private final MastersService mastersService;
    private final ReceiverRepository receiverRepo;
    private final BranchRepository branchRepo;
    private final JobCardRepository jobCardRepo;
    private final AuditEventRepository auditEventRepo;
    private final VehicleRepository vehicleRepo;
    private final CustomerRepository customerRepo;
    private final AttachmentRepository attachmentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;

    public OrganizationPurgeService(AppUserRepository userRepo,
                                    DocumentSequenceRepository documentSequenceRepo,
                                    MastersService mastersService,
                                    ReceiverRepository receiverRepo,
                                    BranchRepository branchRepo,
                                    JobCardRepository jobCardRepo,
                                    AuditEventRepository auditEventRepo,
                                    VehicleRepository vehicleRepo,
                                    CustomerRepository customerRepo,
                                    AttachmentRepository attachmentRepo,
                                    SettlementLineRepository settlementLineRepo,
                                    ClaimCloseRepository claimCloseRepo,
                                    ReceiveDocumentRepository receiveDocumentRepo) {
        this.userRepo = userRepo;
        this.documentSequenceRepo = documentSequenceRepo;
        this.mastersService = mastersService;
        this.receiverRepo = receiverRepo;
        this.branchRepo = branchRepo;
        this.jobCardRepo = jobCardRepo;
        this.auditEventRepo = auditEventRepo;
        this.vehicleRepo = vehicleRepo;
        this.customerRepo = customerRepo;
        this.attachmentRepo = attachmentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
    }

    /**
     * Deletes all child rows of the org. The organization row itself is deleted by the caller.
     * Order matters (all FKs are ON DELETE RESTRICT unless noted):
     *   - job cards reference branch / customer / vehicle / masters, so they go first;
     *   - audit events reference app_user ON DELETE SET NULL — order-independent, cleared early;
     *   - users (and their branch-access rows, via ON DELETE CASCADE) and document sequences
     *     reference branches, so before branches;
     *   - vehicles reference customers, so before customers;
     *   - masters, receivers, customers and branches reference the org, so before it.
     */
    @Transactional
    public void purgeChildren(long orgId) {
        // Receive side first — attachments, then lines, then claim closes + documents,
        // all before the job cards they hang off. (Stored attachment blobs are left to
        // storage lifecycle rules; purge is a rare Super-Admin action on mis-onboarded orgs.)
        long attachments = attachmentRepo.deleteByOrgId(orgId);
        long settlementLines = settlementLineRepo.deleteByOrgId(orgId);
        long claimCloses = claimCloseRepo.deleteByOrgId(orgId);
        long receiveDocuments = receiveDocumentRepo.deleteByOrgId(orgId);

        long jobCards = jobCardRepo.deleteByOrgId(orgId);
        long auditEvents = auditEventRepo.deleteByOrgId(orgId);
        long users = userRepo.deleteByOrganization_Id(orgId);
        long seqs = documentSequenceRepo.deleteByOrgId(orgId);
        mastersService.purgeOrg(orgId);
        long vehicles = vehicleRepo.deleteByOrgId(orgId);
        long customers = customerRepo.deleteByOrgId(orgId);
        long receivers = receiverRepo.deleteByOrgId(orgId);
        long branches = branchRepo.deleteByOrgId(orgId);

        log.info("Org purge: orgId={} deleted attachments={} settlementLines={} claimCloses={} "
                + "receiveDocuments={} jobCards={} auditEvents={} users={} branches={} "
                + "customers={} vehicles={} receivers={} sequences={} + all masters",
            orgId, attachments, settlementLines, claimCloses, receiveDocuments,
            jobCards, auditEvents, users, branches, customers, vehicles, receivers, seqs);
    }
}
