package com.vmware.vsan.client.services.config;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServicePreflightCheckResult;
import com.vmware.vsan.client.services.advancedoptions.AdvancedOptionsService;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.common.CeipService;
import com.vmware.vsan.client.services.dataprotection.ClusterDpConfigService;
import com.vmware.vsan.client.services.dataprotection.model.ClusterDpConfigData;
import com.vmware.vsan.client.services.encryption.EncryptionStatus;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServicePrecheckResult;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.data.EncryptionState;
import com.vmware.vsphere.client.vsan.health.VsanHealthPropertyProvider;
import com.vmware.vsphere.client.vsan.health.VsanHealthServiceStatus;
import com.vmware.vsphere.client.vsan.iscsi.models.config.VsanIscsiTargetConfig;
import com.vmware.vsphere.client.vsan.iscsi.providers.VsanIscsiPropertyProvider;
import com.vmware.vsphere.client.vsan.perf.VsanPerfPropertyProvider;
import com.vmware.vsphere.client.vsan.perf.model.PerfStatsObjectInfo;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanConfigService {
   private static final Log logger = LogFactory.getLog(VsanConfigService.class);
   @Autowired
   private VsanHealthPropertyProvider healthPropertyProvider;
   @Autowired
   private VsanPerfPropertyProvider perfPropertyProvider;
   @Autowired
   private VsanIscsiPropertyProvider iscsiPropertyProvider;
   @Autowired
   private AdvancedOptionsService advancedOptionsService;
   @Autowired
   private ClusterDpConfigService dpConfigService;
   @Autowired
   private CeipService ceipService;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState;

   @TsService
   public VsanServiceData getSupportInsightConfig(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.isSupportInsightSupportedOnVc(clusterRef)) {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      } else {
         boolean ceipEnabled = this.ceipService.getCeipServiceEnabled(clusterRef);
         VsanServiceStatus status = ceipEnabled ? VsanServiceStatus.ENABLED : VsanServiceStatus.DISABLED;
         return new VsanServiceData(status);
      }
   }

   @TsService
   public String getVsanVersion(ManagedObjectReference clusterRef) throws Exception {
      VsanHealthServiceStatus status = this.healthPropertyProvider.getVsanHealthServiceStatus(clusterRef);
      return status != null && status.versionCheck != null ? status.versionCheck.latestVersiobNumber : null;
   }

   @TsService
   public VsanServiceData getDedupConfig(ManagedObjectReference clusterRef) throws Exception {
      if (VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef) && VsanCapabilityUtils.isDeduplicationAndCompressionSupported(clusterRef)) {
         boolean dedupEnabled = (Boolean)QueryUtil.getProperty(clusterRef, "dataEfficiencyStatus");
         VsanServiceStatus status = dedupEnabled ? VsanServiceStatus.ENABLED : VsanServiceStatus.DISABLED;
         return new VsanServiceData(status);
      } else {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      }
   }

   @TsService
   public VsanServiceData getEcnryptionConfig(ManagedObjectReference clusterRef) throws Exception {
      if (VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef) && VsanCapabilityUtils.isEncryptionSupported(clusterRef)) {
         EncryptionStatus config = (EncryptionStatus)QueryUtil.getProperty(clusterRef, "vsanEncryptionStatus");
         if (config != null && config.state != null) {
            VsanServiceStatus serviceStatus;
            switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState()[config.state.ordinal()]) {
            case 1:
            case 3:
               serviceStatus = VsanServiceStatus.ENABLED;
               break;
            case 2:
            default:
               serviceStatus = VsanServiceStatus.DISABLED;
            }

            return new VsanServiceData(serviceStatus, config);
         } else {
            return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
         }
      } else {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      }
   }

   @TsService
   public VsanServiceData getPerformanceConfig(ManagedObjectReference clusterRef) throws Exception {
      VsanServiceStatus serviceStatus = this.perfPropertyProvider.getPerfServiceEnabled(clusterRef) ? VsanServiceStatus.ENABLED : VsanServiceStatus.DISABLED;
      PerfStatsObjectInfo statsInfo = this.perfPropertyProvider.getStatesObjectInformation(clusterRef);
      if (statsInfo.vsanHealth != null) {
         statsInfo.vsanHealth = statsInfo.vsanHealth.toUpperCase().replaceAll("-", "_");
      }

      return new VsanServiceData(serviceStatus, statsInfo);
   }

   @TsService
   public VsanServiceData getIscsiTargetConfig(ManagedObjectReference clusterRef) throws Exception {
      if (VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef) && VsanCapabilityUtils.isIscsiTargetsSupportedOnCluster(clusterRef)) {
         VsanIscsiTargetConfig config = this.iscsiPropertyProvider.getVsanIscsiTargetConfig(clusterRef);
         VsanServiceStatus serviceStatus = config.status ? VsanServiceStatus.ENABLED : VsanServiceStatus.DISABLED;
         return new VsanServiceData(serviceStatus, config);
      } else {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      }
   }

   @TsService
   public VsanServiceData getDataProtectionConfig(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.getVcCapabilities(clusterRef).isLocalDataProtectionSupported) {
         return null;
      } else if (!VsanCapabilityUtils.getCapabilities(clusterRef).isLocalDataProtectionSupported) {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      } else {
         ClusterDpConfigData details = this.dpConfigService.getClusterDpConfig(clusterRef);
         return new VsanServiceData(VsanServiceStatus.ENABLED, details);
      }
   }

   @TsService
   public VsanServiceData getFileServicesConfig(ManagedObjectReference clusterRef) {
      Validate.notNull(clusterRef);
      if (!VsanCapabilityUtils.getVcCapabilities(clusterRef).isFileServiceSupported) {
         return null;
      } else if (!VsanCapabilityUtils.getCapabilities(clusterRef).isFileServiceSupported) {
         return new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED);
      } else {
         VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever("Retrieving File Service configuration", clusterRef).loadConfigInfoEx().loadFileShares().loadFileServicePrecheckResult();
         ConfigInfoEx vsanConfig = this.getConfigInfoEx(clusterRef, dataRetriever);
         this.getPrecheckResult(clusterRef, dataRetriever);
         return vsanConfig == null ? new VsanServiceData(VsanServiceStatus.DISABLED) : new VsanServiceData(VsanServiceStatus.DISABLED);
      }
   }

   private VsanFileServicePrecheckResult getPrecheckResult(ManagedObjectReference clusterRef, VsanAsyncDataRetriever dataRetriever) {
      VsanFileServicePrecheckResult precheckResult = null;

      try {
         VsanFileServicePreflightCheckResult vsanFileServicePreflightCheckResult = dataRetriever.getFileServicePrecheckResult();
         precheckResult = VsanFileServicePrecheckResult.fromVmodl(vsanFileServicePreflightCheckResult);
      } catch (Exception var5) {
         logger.error("Unable to get file service precheck for cluster: " + clusterRef + "'. Returning partial result.", var5);
      }

      return precheckResult;
   }

   private int getNumberOfShares(ManagedObjectReference clusterRef, VsanAsyncDataRetriever dataRetriever) {
      int numberOfShares = 0;

      try {
         FileShare[] shares = dataRetriever.getFileShares();
         if (ArrayUtils.isNotEmpty(shares)) {
            numberOfShares = shares.length;
         }
      } catch (Exception var5) {
         logger.error("Unable to load file shares for cluster: " + clusterRef + "'. Returning partial result.", var5);
      }

      return numberOfShares;
   }

   private ConfigInfoEx getConfigInfoEx(ManagedObjectReference clusterRef, VsanAsyncDataRetriever dataRetriever) {
      ConfigInfoEx vsanConfig = null;

      try {
         vsanConfig = dataRetriever.getConfigInfoEx();
      } catch (Exception var5) {
         logger.error("Unable to get vSAN configuration for cluster: '" + clusterRef + "'. Returning partial result.", var5);
      }

      return vsanConfig;
   }

   @TsService
   public VsanServiceData getAdvancedOptionsConfig(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.getVcCapabilities(clusterRef).isAdvancedClusterOptionsSupported) {
         return null;
      } else {
         return !VsanCapabilityUtils.getCapabilities(clusterRef).isAdvancedClusterOptionsSupported ? new VsanServiceData(VsanServiceStatus.NOT_SUPPORTED) : new VsanServiceData(VsanServiceStatus.ENABLED, this.advancedOptionsService.getAdvancedOptionsInfo(clusterRef));
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[EncryptionState.values().length];

         try {
            var0[EncryptionState.Disabled.ordinal()] = 2;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[EncryptionState.Enabled.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[EncryptionState.EnabledNoKmip.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState = var0;
         return var0;
      }
   }

   private class NetworkProperties {
      private String name;
      private String iconId;
   }
}
