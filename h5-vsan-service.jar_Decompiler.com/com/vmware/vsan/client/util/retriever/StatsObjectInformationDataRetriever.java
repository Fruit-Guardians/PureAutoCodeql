package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerformanceManager;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;

class StatsObjectInformationDataRetriever extends AbstractAsyncDataRetriever<VsanObjectInformation> {
   public StatsObjectInformationDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.future = this.measure.newFuture("VsanPerformanceManager.queryStatsObjectInformation");
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(this.clusterRef);

      try {
         perfMgr.queryStatsObjectInformation(this.clusterRef, this.future);
      } catch (Exception var3) {
         this.future.setException(var3);
      }

   }
}
