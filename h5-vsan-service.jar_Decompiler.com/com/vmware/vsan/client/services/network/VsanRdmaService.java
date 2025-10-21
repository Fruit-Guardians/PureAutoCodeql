package com.vmware.vsan.client.services.network;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHclInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanHclNicInfo;
import com.vmware.vim.vsan.binding.vim.host.VsanHostHclInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.RdmaConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vsan.client.services.capability.VsanCapabilityProvider;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanRdmaService {
   @Autowired
   private VsanCapabilityProvider capabilityProvider;

   @TsService
   public ManagedObjectReference configureVsanRdma(ManagedObjectReference clusterRef, boolean rdmaEnabled) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ReconfigSpec spec = new ReconfigSpec();
      spec.rdmaConfig = new RdmaConfig();
      spec.rdmaConfig.rdmaEnabled = rdmaEnabled;
      return vsanConfigSystem.reconfigureEx(clusterRef, spec);
   }

   @TsService
   public boolean isRdmaEnabled(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
      return configInfoEx.rdmaConfig != null ? configInfoEx.rdmaConfig.rdmaEnabled : false;
   }

   @TsService
   public boolean isRdmaHardwareSupported(ManagedObjectReference clusterRef) throws Exception {
      if (!this.capabilityProvider.getVcCapabilityData(clusterRef).isRdmaSupported) {
         return false;
      } else {
         VsanVcClusterHealthSystem vsanHealthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
         VsanClusterHclInfo clusterHclInfo = vsanHealthSystem.getClusterHclInfo(clusterRef, true, false, (String)null);
         if (clusterHclInfo == null) {
            return false;
         } else {
            VsanHostHclInfo[] hostsHclInfo = clusterHclInfo.getHostResults();
            if (ArrayUtils.isEmpty(hostsHclInfo)) {
               return false;
            } else {
               VsanHostHclInfo[] var8 = hostsHclInfo;
               int var7 = hostsHclInfo.length;

               for(int var6 = 0; var6 < var7; ++var6) {
                  VsanHostHclInfo hostHclInfo = var8[var6];
                  if (ArrayUtils.isEmpty(hostHclInfo.pnics)) {
                     return false;
                  }

                  boolean supportedOnCurrentHost = false;
                  VsanHclNicInfo[] var13;
                  int var12 = (var13 = hostHclInfo.pnics).length;

                  for(int var11 = 0; var11 < var12; ++var11) {
                     VsanHclNicInfo nicInfo = var13[var11];
                     if (BooleanUtils.isTrue(nicInfo.rdmaCapable)) {
                        supportedOnCurrentHost = true;
                        break;
                     }
                  }

                  if (!supportedOnCurrentHost) {
                     return false;
                  }
               }

               return true;
            }
         }
      }
   }
}
