package com.dams.customer.service;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one place that turns "a picked or typed customer + vehicle" into master records
 * (AGENT.md "Linking", rev 56). Used by job-card create, Receipt and Expense so the rule is
 * the same everywhere:
 * <ul>
 *   <li>an id means "link to that existing record" (org-checked);</li>
 *   <li>typed text that matches nothing creates it (a customer is stamped with the creating
 *       branch; a vehicle is deduped on the normalised number);</li>
 *   <li>a vehicle number already registered to a <b>different</b> customer is rejected, never
 *       silently reused; with no customer given it adopts the vehicle's own customer — unless a
 *       customer <i>name</i> was typed that differs from the owner's, which is rejected too
 *       (rev 70: the typed name must never be dropped silently).</li>
 * </ul>
 * Runs inside the caller's transaction.
 */
@Component
public class PartyResolver {

    private static final Logger log = LoggerFactory.getLogger(PartyResolver.class);

    /**
     * @param customer         null when no customer is known yet
     * @param vehicle          null when there is no vehicle, or no customer to own one
     * @param unlinkedVehicleNo the normalised typed vehicle number when it could not become a
     *                         Vehicle row (no customer yet); null otherwise
     */
    public record Party(Customer customer, Vehicle vehicle, String unlinkedVehicleNo) {}

    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final BranchScope branchScope;

    public PartyResolver(CustomerRepository customerRepo, VehicleRepository vehicleRepo, BranchScope branchScope) {
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchScope = branchScope;
    }

    public Party resolve(Long orgId, Long customerId, String newCustomerName, String newCustomerPhone,
                         Long vehicleId, String vehicleNo, Long branchId) {
        Vehicle vehicle = null;
        Customer customer = null;

        if (vehicleId != null) {
            vehicle = vehicleRepo.findByIdAndOrgId(vehicleId, orgId)
                .orElseThrow(() -> DamsException.notFound("Vehicle", vehicleId));
            if (customerId != null && !customerId.equals(vehicle.getCustomerId())) {
                throw DamsException.badRequest("That vehicle does not belong to the selected customer");
            }
            Long ownerId = vehicle.getCustomerId();
            customer = customerRepo.findByIdAndOrgId(ownerId, orgId)
                .orElseThrow(() -> DamsException.notFound("Customer", ownerId));
            if (customerId == null && namesDiffer(newCustomerName, customer.getName())) {
                throw DamsException.conflict(nameConflictMessage(vehicle.getVehicleNo(), customer, newCustomerName));
            }
        } else if (customerId != null) {
            customer = customerRepo.findByIdAndOrgId(customerId, orgId)
                .orElseThrow(() -> DamsException.notFound("Customer", customerId));
        }

        String normalised = Vehicle.normalise(vehicleNo);
        boolean hasNumber = vehicle == null && normalised != null && !normalised.isBlank();

        // A typed number that is already a known vehicle: link to it (and adopt its customer
        // when none was given) instead of creating a duplicate.
        if (hasNumber) {
            Vehicle existing = vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalised).orElse(null);
            if (existing != null) {
                if (customer != null && !customer.getId().equals(existing.getCustomerId())) {
                    throw DamsException.conflict(conflictMessage(orgId, normalised, existing));
                }
                vehicle = existing;
                if (customer == null) {
                    customer = customerRepo.findByIdAndOrgId(existing.getCustomerId(), orgId).orElse(null);
                    if (customer != null && namesDiffer(newCustomerName, customer.getName())) {
                        throw DamsException.conflict(nameConflictMessage(normalised, customer, newCustomerName));
                    }
                }
                hasNumber = false;
            }
        }

        if (customer == null && newCustomerName != null && !newCustomerName.isBlank()) {
            Customer c = new Customer();
            c.setOrgId(orgId);
            c.setName(newCustomerName.trim());
            c.setPhone(newCustomerPhone == null || newCustomerPhone.isBlank() ? null : newCustomerPhone.trim());
            c.setCreatedBranchId(branchId);
            customer = customerRepo.save(c);
            log.info("Customer created inline: orgId={} customerId={} branchId={}", orgId, customer.getId(), branchId);
        }

        if (hasNumber) {
            if (customer == null) {
                return new Party(null, null, normalised); // kept as text until a customer exists
            }
            Vehicle v = new Vehicle();
            v.setOrgId(orgId);
            v.setCustomerId(customer.getId());
            v.setVehicleNo(normalised);
            vehicle = vehicleRepo.save(v);
            log.info("Vehicle created inline: orgId={} vehicleId={} no={}", orgId, vehicle.getId(), normalised);
        }
        return new Party(customer, vehicle, null);
    }

    /** Attach an already-resolved customer to a typed-only vehicle number (job-card attach). */
    public Vehicle vehicleForCustomer(Long orgId, Customer customer, String normalisedNo) {
        Vehicle existing = vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalisedNo).orElse(null);
        if (existing != null) {
            if (!existing.getCustomerId().equals(customer.getId())) {
                throw DamsException.conflict(conflictMessage(orgId, normalisedNo, existing));
            }
            return existing;
        }
        Vehicle v = new Vehicle();
        v.setOrgId(orgId);
        v.setCustomerId(customer.getId());
        v.setVehicleNo(normalisedNo);
        return vehicleRepo.save(v);
    }

    /** A typed name counts as different unless it matches ignoring case and extra spaces. */
    private static boolean namesDiffer(String typed, String owner) {
        if (typed == null || typed.isBlank()) {
            return false;
        }
        return !squash(typed).equals(squash(owner));
    }

    private static String squash(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** Like {@link #conflictMessage}: the owner's name is shown only to callers who are not branch-restricted. */
    private String nameConflictMessage(String vehicleNo, Customer owner, String typed) {
        boolean unrestricted = branchScope.allowedBranchIds().isEmpty();
        return "Vehicle " + vehicleNo + " is on record under "
            + (unrestricted ? owner.getName() : "a different customer")
            + ", but the customer name entered is " + typed.trim()
            + ". Use the customer on record, or update their name first.";
    }

    /** The other customer's name is shown only to callers who are not branch-restricted. */
    private String conflictMessage(Long orgId, String normalised, Vehicle existing) {
        boolean unrestricted = branchScope.allowedBranchIds().isEmpty();
        String owner = unrestricted
            ? customerRepo.findByIdAndOrgId(existing.getCustomerId(), orgId).map(Customer::getName).orElse(null)
            : null;
        return "Vehicle " + normalised + " is already registered to "
            + (owner != null ? owner : "a different customer")
            + ". Pick that customer, or check the number.";
    }
}
