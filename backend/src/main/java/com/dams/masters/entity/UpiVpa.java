package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A UPI ID (VPA) the org can collect payments on. {@code name} doubles as the payee
 * name a customer's UPI app shows when paying — e.g. "JJ Motors - HDFC". An org can
 * have several (one per bank account / branch); the payment QR shows one card per
 * active row.
 */
@Entity
@Table(name = "upi_vpa")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class UpiVpa extends OrgMaster {

    @Column(nullable = false, length = 120)
    private String vpa;
}
