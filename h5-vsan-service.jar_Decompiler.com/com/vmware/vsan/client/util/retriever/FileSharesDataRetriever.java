package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.concurrent.ExecutionException;

class FileSharesDataRetriever extends AbstractAsyncDataRetriever<FileShare[]> {
   private boolean isFileServicesSupported;

   public FileSharesDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.isFileServicesSupported = VsanCapabilityUtils.isFileServiceSupported(this.clusterRef);
      if (this.isFileServicesSupported) {
         VsanProviderUtils.getVsanVcFileServiceSystem(this.clusterRef);
         this.future = this.measure.newFuture("VsanVcFileServiceSystem.queryFileShares");
      }
   }

   protected FileShare[] prepareResult() throws ExecutionException, InterruptedException {
      return this.isFileServicesSupported ? (FileShare[])super.prepareResult() : new FileShare[0];
   }
}
