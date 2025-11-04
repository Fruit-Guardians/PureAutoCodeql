package com.vmware.vsphere.client.vsandp.controllers.vm.summary.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VmArchiveDataProtectionData extends VmDataProtectionBaseData {
   public String datastoreName;
   public ManagedObjectReference datastoreRef;
   public String datastoreUuid;

   public VmArchiveDataProtectionData() {
      this.restoreAvailable = true;
   }

   public VmArchiveDataProtectionData setDatastoreInfo(ManagedObjectReference dsRef, String dsName, String datastoreUuid) {
      this.datastoreRef = dsRef;
      this.datastoreName = dsName;
      this.datastoreUuid = datastoreUuid;
      return this;
   }

   public String toString() {
      return super.toString() + ", datastoreName=" + this.datastoreName + ", datastoreRef=" + this.datastoreRef + ",datastoreUuid=" + this.datastoreUuid;
   }
}
