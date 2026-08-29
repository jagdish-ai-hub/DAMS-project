package com.dams.customer.repository;

import com.dams.customer.entity.Customer;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Single-row read scoped by org — never bare findById for a tenant entity. */
    Optional<Customer> findByIdAndOrgId(Long id, Long orgId);

    List<Customer> findByOrgIdOrderByNameAsc(Long orgId);

    /** Name/phone contains-match for the customer picker and universal search. */
    @Query("""
        select c from Customer c
        where c.orgId = :orgId
          and (lower(c.name) like lower(concat('%', :q, '%'))
               or (c.phone is not null and c.phone like concat('%', :q, '%')))
        order by c.name asc
        """)
    List<Customer> search(@Param("orgId") Long orgId, @Param("q") String q, Limit limit);

    List<Customer> findByOrgIdAndIdInOrderByNameAsc(Long orgId, java.util.Collection<Long> ids);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
