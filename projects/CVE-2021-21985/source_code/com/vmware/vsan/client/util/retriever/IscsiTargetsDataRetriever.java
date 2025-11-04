package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.concurrent.ExecutionException;

class IscsiTargetsDataRetriever extends AbstractAsyncDataRetriever<VsanIscsiTarget[]> {
   private boolean isSupported;

   public IscsiTargetsDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.isSupported = VsanCapabilityUtils.isIscsiTargetsSupportedOnCluster(this.clusterRef);
      if (this.isSupported) {
         this.future = this.measure.newFuture("VsanIscsiTargetSystem.getIscsiTargetss");
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(this.clusterRef);

         try {
            vsanIscsiSystem.getIscsiTargets(this.clusterRef, this.future);
         } catch (Exception var3) {
            this.future.setException(var3);
         }

      }
   }

   protected VsanIscsiTarget[] prepareResult() throws ExecutionException, InterruptedException {
      return this.isSupported ? (VsanIscsiTarget[])super.prepareResult() : new VsanIscsiTarget[0];
   }
}
