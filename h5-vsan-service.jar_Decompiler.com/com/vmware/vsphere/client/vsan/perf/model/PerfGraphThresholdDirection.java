package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public enum PerfGraphThresholdDirection {
   upper,
   lower;

   public static PerfGraphThresholdDirection fromVmodl(String direction) {
      switch(direction.hashCode()) {
      case 103164673:
         if (direction.equals("lower")) {
            return lower;
         }
         break;
      case 111499426:
         if (direction.equals("upper")) {
            return upper;
         }
      }

      return null;
   }
}
