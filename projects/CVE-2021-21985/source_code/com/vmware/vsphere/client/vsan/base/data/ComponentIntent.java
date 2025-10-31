package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;

@data
public enum ComponentIntent {
   REPAIR(65536),
   DECOM(131072),
   REBALANCE(262144),
   FIXCOMPLIANCE(524288),
   POLICYCHANGE(1048576),
   MOVE(2097152),
   STALE(16777216),
   MERGE_CONTACT(67108864);

   private int value;

   private ComponentIntent(int value) {
      this.value = value;
   }

   public int getValue() {
      return this.value;
   }
}
