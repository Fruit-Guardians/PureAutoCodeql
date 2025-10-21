package com.vmware.vsphere.client.vsandp.controllers.vm.summary.model;

import com.vmware.vise.core.model.data;

@data
public class VmLocalDataProtectionData extends VmDataProtectionBaseData {
   public VmLocalDataProtectionData() {
      this.restoreAvailable = true;
   }
}
