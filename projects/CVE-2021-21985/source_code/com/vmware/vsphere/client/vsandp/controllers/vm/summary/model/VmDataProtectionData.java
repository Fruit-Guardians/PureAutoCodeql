package com.vmware.vsphere.client.vsandp.controllers.vm.summary.model;

import com.vmware.vise.core.model.data;

@data
public class VmDataProtectionData {
   public VmLocalDataProtectionData localProtectionData;
   public VmArchiveDataProtectionData archiveProtectionData;
   public VmRemoteDataProtectionData remoteProtectionData;
   public boolean hasRestorePermission;

   public VmDataProtectionData(VmLocalDataProtectionData localProtectionData, VmArchiveDataProtectionData archiveProtectionData, VmRemoteDataProtectionData remoteProtectionData, boolean hasRestorePermission) {
      this.localProtectionData = localProtectionData;
      this.archiveProtectionData = archiveProtectionData;
      this.remoteProtectionData = remoteProtectionData;
      this.hasRestorePermission = hasRestorePermission;
   }

   public VmDataProtectionData() {
   }
}
