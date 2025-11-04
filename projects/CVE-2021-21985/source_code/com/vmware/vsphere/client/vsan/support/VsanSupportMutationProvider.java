package com.vmware.vsphere.client.vsan.support;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanSupportMutationProvider {
   private static final Log _logger = LogFactory.getLog(VsanSupportMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanSupportMutationProvider.class);

   @TsService
   public ManagedObjectReference attachVsanSupportBundleToSr(ManagedObjectReference clusterRef, VsanSRAttachSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.attachVsanSupportBundleToSr");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               ManagedObjectReference taskRef = healthSystem.attachVsanSupportBundleToSr(clusterRef, spec.serviceRequestID);
               var20 = VsanHealthUtil.buildTaskMor(taskRef.getValue(), clusterRef.getServerGuid());
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
            }

            label162:
            try {
               return var20;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (p != null) {
            p.close();
         }

         throw var3;
      } catch (Throwable var19) {
         if (var3 == null) {
            var3 = var19;
         } else if (var3 != var19) {
            var3.addSuppressed(var19);
         }

         throw var3;
      }
   }
}
