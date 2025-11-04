package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;

class ConfigInfoExDataRetriever extends AbstractAsyncDataRetriever<ConfigInfoEx> {
   public ConfigInfoExDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.future = this.measure.newFuture("VsanVcClusterConfigSystem.getConfigInfoEx");
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(this.clusterRef);

      try {
         vsanConfigSystem.getConfigInfoEx(this.clusterRef, this.future);
      } catch (Exception var3) {
         this.future.setException(var3);
      }

   }
}
