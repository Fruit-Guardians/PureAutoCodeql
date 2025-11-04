package com.vmware.vsan.client.services.capacity.model;

import com.vmware.vise.core.model.data;

@data
public class WhatifCapacityData {
   public long total;
   public long free;

   public WhatifCapacityData() {
   }

   public WhatifCapacityData(long total, long free) {
      this.total = total;
      this.free = free;
   }
}
