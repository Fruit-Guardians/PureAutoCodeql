package com.vmware.vsan.client.services.encryption;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.EncryptionRekeySpec;
import org.springframework.stereotype.Component;

@Component
public class EncryptionMutationProvider {
   private static final VsanProfiler _profiler = new VsanProfiler(EncryptionMutationProvider.class);

   @TsService
   public ManagedObjectReference rekeyEncryptedCluster(ManagedObjectReference clusterRef, EncryptionRekeySpec spec) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("vsanConfigSystem.rekeyEncryptedCluster");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               ManagedObjectReference taskRef = vsanConfigSystem.rekeyEncryptedCluster(clusterRef, spec.reEncryptData, spec.allowReducedRedundancy);
               VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
               var20 = taskRef;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
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
         if (point != null) {
            point.close();
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
}
