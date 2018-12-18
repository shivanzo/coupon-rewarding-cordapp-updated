package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/**
 * A schema.
 */
public class SchemaV1 extends MappedSchema {
    public SchemaV1() {
        super(Schema.class, 1, ImmutableList.of(PersistentIOU.class));
    }

    @Entity
    @Table(name = "states")
    public static class PersistentIOU extends PersistentState {
        @Column(name = "netmeds") private final String netmeds;
        @Column(name = "amazon") private final String amazon;
        @Column(name = "value") private final int value;
        @Column(name = "coupon_id") private final UUID coupon_id;


        public PersistentIOU(String netmeds, String amazon, int value, UUID coupon_id) {
            this.netmeds = netmeds;
            this.amazon = amazon;
            this.value = value;
            this.coupon_id = coupon_id;
        }

        // Default constructor required by hibernate.
        public PersistentIOU() {
            this.netmeds = null;
            this.amazon = null;
            this.value = 0;
            this.coupon_id = null;
        }

        public String getNetmeds() {
            return netmeds;
        }

        public String getAmazon() {
            return amazon;
        }

        public int getValue() {
            return value;
        }

        public UUID getCoupon_id() {
            return coupon_id;
        }
    }
}