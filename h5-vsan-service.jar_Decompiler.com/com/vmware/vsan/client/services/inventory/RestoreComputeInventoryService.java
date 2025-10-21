package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.VirtualApp;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import org.springframework.stereotype.Component;

@Component
public class RestoreComputeInventoryService extends ComputeInventoryService {
   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) throws Exception {
      return super.getNodeChildren(parentRef, pscDetails, contextRef);
   }

   protected boolean isTypeSupported(ManagedObjectReference ref) {
      return this.vmodlHelper.isOfType(ref, VirtualApp.class) ? false : super.isTypeSupported(ref);
   }
}
