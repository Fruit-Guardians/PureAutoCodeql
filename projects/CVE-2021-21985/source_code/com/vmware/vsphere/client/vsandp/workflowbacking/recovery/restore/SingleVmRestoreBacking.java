package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.RestoreVmSpec;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.VmInventoryModel;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SingleVmRestoreBacking {
   @Autowired
   private RestoreWorkflowBacking commonBacking;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;

   @TsService
   public ManagedObjectReference getRestoreVm(RestoreVmSpec spec) throws Exception {
      ManagedObjectReference vmRef = spec.selectedSyncPoint.vmRef;
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      ManagedObjectReference vmFolder = this.inventoryHelper.getVmFolderOfDataCenter(spec.selectedVmFolder);
      new ArrayList();
      ManagedObjectReference taskRef = (ManagedObjectReference)this.commonBacking.restore(vmRef, spec.selectedSyncPoint, spec.powerOn, spec.createIndependentVm, vmFolder, spec.storagePolicyId, spec.vmName, spec.selectedNetwork, spec.selectedResourcePool, clusterRef).get();
      VmodlHelper.assignServerGuid(taskRef, vmRef.getServerGuid());
      return taskRef;
   }

   @TsService
   public String getValidateTargetInventory(ManagedObjectReference vmRef, VmInventoryModel targetInventory, String userProviderVmName) throws Exception {
      String result = this.commonBacking.getValidatePermissions(vmRef, targetInventory);
      if (result != null) {
         return result;
      } else if (!this.commonBacking.checkHostConnectionState(new ManagedObjectReference[]{targetInventory.compute.ref})) {
         return Utils.getLocalizedString("vsan.restore.validation.compute.connected.error");
      } else {
         return !this.inventoryHelper.isVmNameUniqueForFolder(targetInventory.folder.ref, userProviderVmName) ? Utils.getLocalizedString("vsan.restore.validation.folder.vm.name.exists") : null;
      }
   }

   @TsService
   public String getVmStoragePolicyId(ManagedObjectReference vmRef) {
      try {
         return this.inventoryHelper.getVmStoragePolicyId(vmRef);
      } catch (Exception var3) {
         throw new VsanUiLocalizableException("vsan.restore.default.policy.error", var3);
      }
   }
}
