package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/** Bank names offered wherever a settlement/expense line needs one. */
@Entity
@Table(name = "bank")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@NoArgsConstructor
public class Bank extends OrgMaster {
}
