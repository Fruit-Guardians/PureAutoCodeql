package com.vmware.vsan.client.services.health;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class PurgeInaccessibleVmSwapObjectsProvider {
   private static final Log logger = LogFactory.getLog(PurgeInaccessibleVmSwapObjectsProvider.class);
   private static final VsanProfiler profiler = new VsanProfiler(PurgeInaccessibleVmSwapObjectsProvider.class);

   @TsService
   public String[] getInaccessibleVmSwapObjects(ManagedObjectReference clusterRef) throws Exception {
      this.validateIfApiIsSupported(clusterRef);
      VsanObjectSystem vsanObjectSystem = VsanProviderUtils.getVsanObjectSystem(clusterRef);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = profiler.point("vsanObjectSystem.queryInaccessibleVmSwapObjects");

         Throwable var10000;
         label185: {
            boolean var10001;
            String[] var19;
            try {
               String[] inaccessibleSwapObjects = vsanObjectSystem.queryInaccessibleVmSwapObjects(clusterRef);
               var19 = inaccessibleSwapObjects != null ? inaccessibleSwapObjects : new String[0];
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label185;
            }

            if (p != null) {
               p.close();
            }

            label174:
            try {
               return var19;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label174;
            }
         }

         var3 = var10000;
         if (p != null) {
            p.close();
         }

         throw var3;
      } catch (Throwable var18) {
         if (var3 == null) {
            var3 = var18;
         } else if (var3 != var18) {
            var3.addSuppressed(var18);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference purgeInaccessibleVmSwapObjects(ManagedObjectReference clusterRef, String[] objUuids) throws Exception {
      this.validateIfApiIsSupported(clusterRef);
      VsanObjectSystem vsanObjectSystem = VsanProviderUtils.getVsanObjectSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = profiler.point("vsanObjectSystem.DeleteObjects");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               ManagedObjectReference taskRef = vsanObjectSystem.deleteObjects(clusterRef, objUuids, true);
               var20 = VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
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

         var4 = var10000;
         if (p != null) {
            p.close();
         }

         throw var4;
      } catch (Throwable var19) {
         if (var4 == null) {
            var4 = var19;
         } else if (var4 != var19) {
            var4.addSuppressed(var19);
         }

         throw var4;
      }
   }

   private void validateIfApiIsSupported(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.isPurgeInaccessibleVmSwapObjectsSupported(clusterRef)) {
         throw new VsanUiLocalizableException("vsan.common.error.notSupported");
      }
   }
}
