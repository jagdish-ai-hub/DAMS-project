package com.dams.receiver.service;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.receiver.dto.ReceiverRequest;
import com.dams.receiver.dto.ReceiverResponse;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Receiver (vendor/payee) CRUD, org-scoped. Deactivated, never deleted. */
@Service
public class ReceiverService {

    private static final Logger log = LoggerFactory.getLogger(ReceiverService.class);

    private final ReceiverRepository receiverRepo;

    public ReceiverService(ReceiverRepository receiverRepo) {
        this.receiverRepo = receiverRepo;
    }

    @Transactional(readOnly = true)
    public List<ReceiverResponse> list() {
        return receiverRepo.findByOrgIdOrderByNameAsc(TenantContext.requireOrgId()).stream()
            .map(ReceiverResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReceiverResponse get(Long id) {
        return ReceiverResponse.of(load(id));
    }

    @Transactional
    public ReceiverResponse create(ReceiverRequest request) {
        Long orgId = TenantContext.requireOrgId();
        String name = request.getName().trim();

        if (receiverRepo.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
            throw DamsException.conflict("Receiver '" + name + "' already exists in this organization");
        }

        Receiver receiver = new Receiver();
        receiver.setOrgId(orgId);
        receiver.setName(name);
        receiver.setPhone(trimToNull(request.getPhone()));
        receiver.setActive(true);
        receiver = receiverRepo.save(receiver);

        log.info("Receiver created: orgId={} receiverId={} name='{}'", orgId, receiver.getId(), name);
        return ReceiverResponse.of(receiver);
    }

    @Transactional
    public ReceiverResponse update(Long id, ReceiverRequest request) {
        Long orgId = TenantContext.requireOrgId();
        Receiver receiver = load(id);
        String name = request.getName().trim();

        if (!name.equalsIgnoreCase(receiver.getName())
                && receiverRepo.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
            throw DamsException.conflict("Receiver '" + name + "' already exists in this organization");
        }

        receiver.setName(name);
        receiver.setPhone(trimToNull(request.getPhone()));
        if (request.getActive() != null) {
            receiver.setActive(request.getActive());
        }
        receiver = receiverRepo.save(receiver);

        log.info("Receiver updated: orgId={} receiverId={} name='{}' active={}",
            orgId, receiver.getId(), receiver.getName(), receiver.isActive());
        return ReceiverResponse.of(receiver);
    }

    private Receiver load(Long id) {
        return receiverRepo.findByIdAndOrgId(id, TenantContext.requireOrgId())
            .orElseThrow(() -> DamsException.notFound("Receiver", id));
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
