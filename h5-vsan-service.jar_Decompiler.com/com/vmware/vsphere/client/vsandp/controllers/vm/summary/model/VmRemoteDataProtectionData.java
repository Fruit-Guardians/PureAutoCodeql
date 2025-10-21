package com.vmware.vsphere.client.vsandp.controllers.vm.summary.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class VmRemoteDataProtectionData extends VmDataProtectionBaseData {
   public String clusterName;
   public String clusterUuid;
   public ManagedObjectReference clusterRef;
   public String vcName;
   public ManagedObjectReference vcRef;

   public VmRemoteDataProtectionData() {
      this.restoreAvailable = false;
   }

   public VmRemoteDataProtectionData setTargetClusterInfo(ManagedObjectReference clusterRef, String clusterName, String clusterUuid) {
      this.clusterRef = clusterRef;
      this.clusterName = clusterName;
      this.clusterUuid = clusterUuid;
      return this;
   }

   public VmRemoteDataProtectionData setTargetVcInfo(ManagedObjectReference vcRef, String vcName) {
      this.vcRef = vcRef;
      this.vcName = vcName;
      return this;
   }

   public String toString() {
      return super.toString() + ", clusterName=" + this.clusterName + ", clusterUuid=" + this.clusterUuid + ", clusterRef=" + this.clusterRef;
   }
}
