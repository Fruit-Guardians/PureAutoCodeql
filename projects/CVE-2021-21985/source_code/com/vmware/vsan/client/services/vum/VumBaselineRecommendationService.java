package com.vmware.vsan.client.services.vum;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVumConfig;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.VumBaselineRecommendationType;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class VumBaselineRecommendationService {
   private static final VsanProfiler profiler = new VsanProfiler(VumBaselineRecommendationService.class);

   @TsService
   public ManagedObjectReference setClusterVumBaselineRecommendation(ManagedObjectReference clusterRef, String recommendation) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      VsanVumConfig vumConfig = this.getVumConfig(clusterRef);
      vumConfig.baselinePreferenceType = recommendation;
      ReconfigSpec spec = new ReconfigSpec();
      spec.vumConfig = vumConfig;
      ManagedObjectReference taskRef = vsanConfigSystem.reconfigureEx(clusterRef, spec);
      return VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
   }

   private VsanVumConfig getVumConfig(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      VsanVumConfig vumConfig = null;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = profiler.point("VsanVcClusterConfigSystem.getConfigInfoEx");

         try {
            ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
            vumConfig = configInfoEx.vumConfig;
            if (vumConfig == null) {
               vumConfig = new VsanVumConfig();
            }
         } finally {
            if (point != null) {
               point.close();
            }

         }

         return vumConfig;
      } catch (Throwable var13) {
         if (var4 == null) {
            var4 = var13;
         } else if (var4 != var13) {
            var4.addSuppressed(var13);
         }

         throw var4;
      }
   }

   @TsService
   public BaselineRecommendationData getVumBaselineRecommendation(ManagedObjectReference vcRoot, ManagedObjectReference clusterRef) throws Exception {
      BaselineRecommendationData recommendationData = new BaselineRecommendationData();
      recommendationData.vcRecommendation = this.getVcVumBaselineRecommendation(vcRoot);
      if (clusterRef != null) {
         recommendationData.clusterRecommendation = this.getClusterVumBaselineRecommendation(clusterRef);
      }

      return recommendationData;
   }

   private VumBaselineRecommendationType getVcVumBaselineRecommendation(ManagedObjectReference vcRoot) {
      return VumBaselineRecommendationType.latestRelease;
   }

   public VumBaselineRecommendationType getClusterVumBaselineRecommendation(ManagedObjectReference clusterRef) throws Exception {
      VsanVumConfig vumConfig = this.getVumConfig(clusterRef);
      return StringUtils.isEmpty(vumConfig.baselinePreferenceType) ? VumBaselineRecommendationType.latestRelease : VumBaselineRecommendationType.valueOf(vumConfig.getBaselinePreferenceType());
   }
}
