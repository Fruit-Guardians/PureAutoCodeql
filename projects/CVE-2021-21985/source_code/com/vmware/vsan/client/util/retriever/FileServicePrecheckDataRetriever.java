package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.DirectoryServerConfig;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServicePreflightCheckResult;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.concurrent.ExecutionException;

class FileServicePrecheckDataRetriever extends AbstractAsyncDataRetriever<VsanFileServicePreflightCheckResult> {
   private boolean isFileServicesSupported;

   public FileServicePrecheckDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      super(clusterRef, measure);
   }

   public void start() {
      this.isFileServicesSupported = VsanCapabilityUtils.isFileServiceSupported(this.clusterRef);
      if (this.isFileServicesSupported) {
         VsanFileServiceSystem vsanVcFileServiceSystem = VsanProviderUtils.getVsanVcFileServiceSystem(this.clusterRef);
         this.future = this.measure.newFuture("VsanVcFileServiceSystem.performFileServicePreflightCheck");

         try {
            vsanVcFileServiceSystem.performFileServicePreflightCheck(this.clusterRef, (DirectoryServerConfig)null, this.future);
         } catch (Exception var3) {
            this.future.setException(var3);
         }

      }
   }

   protected VsanFileServicePreflightCheckResult prepareResult() throws ExecutionException, InterruptedException {
      return this.isFileServicesSupported ? (VsanFileServicePreflightCheckResult)super.prepareResult() : null;
   }
}
