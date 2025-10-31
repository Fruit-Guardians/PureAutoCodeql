package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanStretchedClusterPropertyProvider {
   private static final Log _logger = LogFactory.getLog(VsanStretchedClusterPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanStretchedClusterPropertyProvider.class);

   @TsService
   public boolean getIsStretchedClusterSupported(ManagedObjectReference cluster) {
      return VsanCapabilityUtils.isStretchedClusterSupportedOnCluster(cluster);
   }

   @TsService
   public List<WitnessHostData> getWitnessHosts(ManagedObjectReference clusterRef) {
      List<WitnessHostData> witnessHosts = new ArrayList();
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      if (stretchedClusterSystem != null) {
         VSANWitnessHostInfo[] witnessHostInfos = null;

         try {
            Throwable var5 = null;
            Object var6 = null;

            try {
               VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.getWitnessHosts");

               try {
                  witnessHostInfos = stretchedClusterSystem.getWitnessHosts(clusterRef);
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var17) {
               if (var5 == null) {
                  var5 = var17;
               } else if (var5 != var17) {
                  var5.addSuppressed(var17);
               }

               throw var5;
            }
         } catch (Exception var18) {
            _logger.error("Could not retrieve witness hosts " + var18.getMessage());
         }

         if (witnessHostInfos != null) {
            VSANWitnessHostInfo[] var8 = witnessHostInfos;
            int var21 = witnessHostInfos.length;

            for(int var20 = 0; var20 < var21; ++var20) {
               VSANWitnessHostInfo witnessHost = var8[var20];
               if (witnessHost.host != null) {
                  WitnessHostData witness = new WitnessHostData(witnessHost, clusterRef.getServerGuid());
                  witnessHosts.add(witness);
               }
            }
         }
      }

      return witnessHosts;
   }
}
