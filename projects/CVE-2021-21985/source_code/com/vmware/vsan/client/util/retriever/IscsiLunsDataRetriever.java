package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.concurrent.ExecutionException;

class IscsiLunsDataRetriever extends AbstractAsyncDataRetriever<VsanIscsiLUN[]> {
   private boolean isSupported;

   public IscsiLunsDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.isSupported = VsanCapabilityUtils.isIscsiTargetsSupportedOnCluster(this.clusterRef);
      if (this.isSupported) {
         this.future = this.measure.newFuture("VsanIscsiTargetSystem.getIscsiLUNs");
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(this.clusterRef);

         try {
            vsanIscsiSystem.getIscsiLUNs(this.clusterRef, (String[])null, this.future);
         } catch (Exception var3) {
            this.future.setException(var3);
         }

      }
   }

   protected VsanIscsiLUN[] prepareResult() throws ExecutionException, InterruptedException {
      return this.isSupported ? (VsanIscsiLUN[])super.prepareResult() : new VsanIscsiLUN[0];
   }
}
