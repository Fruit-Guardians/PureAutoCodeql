package com.vmware.vsan.client.services.advancedoptions;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.ProactiveRebalanceInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanExtendedConfig;
import com.vmware.vim.vsan.binding.vim.vsan.VsanUnmapConfig;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.stereotype.Component;

@Component
public class AdvancedOptionsService {
   private static final String IS_WITNESS = "isWitnessHost";

   @TsService
   public AdvancedOptionsInfo getAdvancedOptionsInfo(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
      VsanExtendedConfig extendedConfig = configInfoEx.getExtendedConfig();
      AdvancedOptionsInfo result = new AdvancedOptionsInfo();
      result.objectRepairTimer = extendedConfig.getObjectRepairTimer();
      result.isSiteReadLocalityEnabled = !extendedConfig.getDisableSiteReadLocality();
      result.isCustomizedSwapObjectEnabled = extendedConfig.getEnableCustomizedSwapObject();
      result.largeClusterSupportEnabled = extendedConfig.getLargeScaleClusterSupport();
      if (extendedConfig.proactiveRebalanceInfo != null && VsanCapabilityUtils.isAutomaticRebalanceSupported(clusterRef)) {
         result.isAutomaticRebalanceEnabled = BooleanUtils.toBoolean(extendedConfig.getProactiveRebalanceInfo().enabled);
         result.rebalancingThreshold = extendedConfig.getProactiveRebalanceInfo().threshold == null ? 30 : extendedConfig.getProactiveRebalanceInfo().threshold;
      }

      if (configInfoEx.getUnmapConfig() != null) {
         result.isGuestTrimUnmapEnabled = configInfoEx.getUnmapConfig().enable;
      }

      return result;
   }

   @TsService
   public ManagedObjectReference configureAdvancedOptions(ManagedObjectReference clusterRef, AdvancedOptionsInfo advancedOptionsInfo) throws Exception {
      ProactiveRebalanceInfo rebalanceInfo = new ProactiveRebalanceInfo();
      if (VsanCapabilityUtils.isAutomaticRebalanceSupported(clusterRef)) {
         rebalanceInfo.enabled = advancedOptionsInfo.isAutomaticRebalanceEnabled;
         rebalanceInfo.threshold = advancedOptionsInfo.rebalancingThreshold;
      } else {
         rebalanceInfo = null;
      }

      ReconfigSpec spec = new ReconfigSpec();
      VsanExtendedConfig extendedConfig = new VsanExtendedConfig(advancedOptionsInfo.objectRepairTimer, !advancedOptionsInfo.isSiteReadLocalityEnabled, advancedOptionsInfo.isCustomizedSwapObjectEnabled, advancedOptionsInfo.largeClusterSupportEnabled, rebalanceInfo);
      spec.setExtendedConfig(extendedConfig);
      VsanUnmapConfig unmapConfig = new VsanUnmapConfig();
      unmapConfig.enable = advancedOptionsInfo.isGuestTrimUnmapEnabled;
      spec.setUnmapConfig(unmapConfig);
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ManagedObjectReference configureAdvancedOptionsTask = vsanConfigSystem.reconfigureEx(clusterRef, spec);
      return VmodlHelper.assignServerGuid(configureAdvancedOptionsTask, clusterRef.getServerGuid());
   }

   @TsService
   public boolean getVsanStretchedCluster(ManagedObjectReference clusterRef) throws Exception {
      PropertyValue[] hostProps = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), new String[]{"isWitnessHost"}).getPropertyValues();
      PropertyValue[] var6 = hostProps;
      int var5 = hostProps.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue val = var6[var4];
         if (val.propertyName.equals("isWitnessHost") && (Boolean)val.value) {
            return true;
         }
      }

      return false;
   }
}
