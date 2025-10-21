package com.vmware.vsan.client.services.diskGroups.data;

import com.vmware.vise.core.model.data;

@data
public class UnmountDiskGroupSpec {
   public VsanDiskMapping diskMapping;
   public DecommissionMode decommissionMode;

   public String toString() {
      return "UnmountDiskGroupSpec [diskMapping=" + this.diskMapping + ", decommissionMode=" + this.decommissionMode + "]";
   }
}
