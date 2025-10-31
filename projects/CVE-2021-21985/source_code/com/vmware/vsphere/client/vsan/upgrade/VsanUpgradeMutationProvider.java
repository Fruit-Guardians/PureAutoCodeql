package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.VsanUpgradeSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanUpgradeMutationProvider {
   public static final String TASK_TYPE = "Task";
   private static final Log _logger = LogFactory.getLog(VsanUpgradeMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanUpgradeMutationProvider.class);

   @TsService
   public ManagedObjectReference performUpgrade(ManagedObjectReference clusterRef, VsanUpgradeSpec spec) throws Exception {
      boolean isUpgradeSystem2Supported = VsanCapabilityUtils.isUpgradeSystem2SupportedOnVc(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("upgradeSystem.performUpgrade");

         Throwable var10000;
         label193: {
            boolean var10001;
            ManagedObjectReference var21;
            try {
               VsanUpgradeSystem upgradeSystem = isUpgradeSystem2Supported ? VsanProviderUtils.getVsanUpgradeSystem(clusterRef) : VsanProviderUtils.getVsanLegacyUpgradeSystem(clusterRef);
               ManagedObjectReference taskRef = upgradeSystem.performUpgrade(clusterRef, spec.performObjectUpgrade, spec.downgradeFormat, spec.allowReducedRedundancy, (ManagedObjectReference[])null);
               var21 = buildTaskMor(taskRef.getValue(), clusterRef.getServerGuid());
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label193;
            }

            if (p != null) {
               p.close();
            }

            label182:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label182;
            }
         }

         var4 = var10000;
         if (p != null) {
            p.close();
         }

         throw var4;
      } catch (Throwable var20) {
         if (var4 == null) {
            var4 = var20;
         } else if (var4 != var20) {
            var4.addSuppressed(var20);
         }

         throw var4;
      }
   }

   @TsService
   public ManagedObjectReference performUpgradePreflightAsyncCheck(ManagedObjectReference param1, VsanUpgradePrecheckSpec param2) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   private static ManagedObjectReference buildTaskMor(String taskId, String vcGuid) {
      ManagedObjectReference task = new ManagedObjectReference("Task", taskId, vcGuid);
      return task;
   }
}
