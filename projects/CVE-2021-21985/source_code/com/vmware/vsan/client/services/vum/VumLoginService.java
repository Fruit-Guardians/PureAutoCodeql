package com.vmware.vsan.client.services.vum;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVumSystem;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import org.springframework.stereotype.Component;

@Component
public class VumLoginService {
   private static final VsanProfiler _profiler = new VsanProfiler(VumLoginService.class);

   @TsService
   public boolean loginToVum(ManagedObjectReference clusterRef, String username, String password) throws Exception {
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("VsanVumSystem.fetchIsoDepotCookie");

         Throwable var10000;
         label173: {
            boolean var10001;
            try {
               VsanVumSystem vumSystem = VsanProviderUtils.getVsanVumSystem(clusterRef);
               vumSystem.fetchIsoDepotCookie(username, password);
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
               return true;
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
}
