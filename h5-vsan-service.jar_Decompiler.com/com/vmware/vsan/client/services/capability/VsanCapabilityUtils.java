package com.vmware.vsan.client.services.capability;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VSANStretchedClusterCapability;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanCapabilityUtils {
   private static final Log _logger = LogFactory.getLog(VsanCapabilityUtils.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanCapabilityUtils.class);
   private static VsanCapabilityCacheManager capabilityCacheManager;

   public static void setVsanCapabilityCacheManager(VsanCapabilityCacheManager capabilityCacheManager) {
      VsanCapabilityUtils.capabilityCacheManager = capabilityCacheManager;
   }

   public static VsanCapabilityData getVcCapabilities(ManagedObjectReference moRef) {
      return capabilityCacheManager.getVcCapabilities(moRef);
   }

   public static VsanCapabilityData getCapabilities(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      VsanCapabilityData capabilityData = HostSystem.class.getSimpleName().equals(moRef.getType()) ? capabilityCacheManager.getHostCapabilities(moRef) : capabilityCacheManager.getClusterCapabilities(moRef);
      return capabilityData;
   }

   public static boolean isUpgradeSystemExSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isUpgradeSupported;
   }

   public static boolean isUpgradeSystem2SupportedOnVc(ManagedObjectReference moRef) {
      return isUpgradeSystemExSupportedOnVc(moRef);
   }

   public static boolean isObjectSystemSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isObjectIdentitiesSupported;
   }

   public static boolean isObjectSystemSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isObjectIdentitiesSupported;
   }

   public static boolean isSupportInsightSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isSupportInsightSupported;
   }

   public static boolean isClusterConfigSystemSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isClusterConfigSupported;
   }

   public static boolean isDeduplicationAndCompressionSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isDeduplicationAndCompressionSupported;
   }

   public static boolean isDeduplicationAndCompressionSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isDeduplicationAndCompressionSupported;
   }

   public static boolean isIscsiTargetsSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isIscsiTargetsSupported;
   }

   public static boolean isIscsiTargetsSupportedOnHost(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      return getCapabilities(moRef).isIscsiTargetsSupported;
   }

   public static boolean isIscsiTargetsSupportedOnCluster(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      return getCapabilities(moRef).isIscsiTargetsSupported;
   }

   public static boolean isStretchedClusterSupported(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      boolean isSupported = false;
      if (ClusterComputeResource.class.getSimpleName().equals(moRef.getType())) {
         isSupported = isStretchedClusterSupportedOnCluster(moRef);
      } else if (HostSystem.class.getSimpleName().equals(moRef.getType())) {
         isSupported = isStretchedClusterSupportedOnHost(moRef);
      }

      return isSupported;
   }

   public static boolean isAllFlashSupported(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      boolean isSupported = false;
      if (ClusterComputeResource.class.getSimpleName().equals(moRef.getType())) {
         isSupported = isAllFlashSupportedOnCluster(moRef);
      } else if (HostSystem.class.getSimpleName().equals(moRef.getType())) {
         isSupported = isAllFlashSupportedOnHost(moRef);
      }

      return isSupported;
   }

   public static boolean isEncryptionSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isEncryptionSupported;
   }

   public static boolean isEncryptionSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isEncryptionSupported;
   }

   public static boolean isSilentCheckSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isEncryptionSupported;
   }

   public static boolean isPerfVerboseModeSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isPerfVerboseModeSupported;
   }

   public static boolean isPerfSvcAutoConfigSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isPerfSvcAutoConfigSupported;
   }

   public static boolean isResyncThrottlingSupported(ManagedObjectReference moRef) {
      VsanCapabilityData capabilityData = getCapabilities(moRef);
      _logger.info("Resync throttling supported on cluster is: " + capabilityData.isResyncThrottlingSupported);
      return capabilityData.isResyncThrottlingSupported;
   }

   public static boolean isConfigAssistSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isConfigAssistSupported;
   }

   public static boolean isUpdatesMgmtSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isUpdatesMgmtSupported;
   }

   public static boolean isWhatIfComplianceSupported(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      return getVcCapabilities(moRef).isWhatIfComplianceSupported;
   }

   public static boolean getIsHistoricalCapacitySupported(ManagedObjectReference objectRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);

      ManagedObjectReference[] hosts;
      try {
         hosts = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host", (Object)null);
      } catch (Exception var8) {
         return true;
      }

      ManagedObjectReference[] var6 = hosts;
      int var5 = hosts.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference host = var6[var4];
         boolean isSupported = getIsHistoricalCapacitySupportedOnHost(host);
         if (!isSupported) {
            return false;
         }
      }

      return true;
   }

   private static boolean getIsHistoricalCapacitySupportedOnHost(ManagedObjectReference hostRef) {
      validateMoRef(hostRef);
      VsanCapabilityData capabilities = getCapabilities(hostRef);
      return capabilities.isDisconnected || capabilities.isHistoricalCapacitySupported;
   }

   public static boolean isWhatIfSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isWhatIfSupported;
   }

   public static boolean isPerfAnalysisSupportedOnVc(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      return true;
   }

   public static boolean isCloudHealthSupportedOnVc(ManagedObjectReference moRef) {
      validateMoRef(moRef);
      return getVcCapabilities(moRef).isCloudHealthSupported;
   }

   public static boolean isStretchedClusterSupportedOnHost(ManagedObjectReference moRef) {
      boolean isSupported = false;

      try {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.retrieveStretchedClusterHostCapability");

            try {
               VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(moRef);
               VSANStretchedClusterCapability capability = stretchedClusterSystem.retrieveStretchedClusterHostCapability(moRef);
               isSupported = capability.isSupported;
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var14) {
            if (var2 == null) {
               var2 = var14;
            } else if (var2 != var14) {
               var2.addSuppressed(var14);
            }

            throw var2;
         }
      } catch (Exception var15) {
         _logger.error("Failed to retrieve VSAN stretched cluster capability for host: " + var15.getMessage());
         isSupported = false;
      }

      return isSupported;
   }

   public static boolean isStretchedClusterSupportedOnCluster(ManagedObjectReference moRef) {
      boolean isSupported = true;

      try {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.retrieveStretchedClusterVcCapability");

            try {
               VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(moRef);
               VSANStretchedClusterCapability[] capabilities = stretchedClusterSystem.retrieveStretchedClusterVcCapability(moRef, false);
               VSANStretchedClusterCapability[] var10 = capabilities;
               int var9 = capabilities.length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  VSANStretchedClusterCapability hostCapability = var10[var8];
                  if (hostCapability.isSupported == null || !hostCapability.isSupported) {
                     isSupported = false;
                     break;
                  }
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var18) {
            if (var2 == null) {
               var2 = var18;
            } else if (var2 != var18) {
               var2.addSuppressed(var18);
            }

            throw var2;
         }
      } catch (Exception var19) {
         _logger.error("Failed to retrieve stretched cluster capabilities: " + var19.getMessage());
         isSupported = false;
      }

      return isSupported;
   }

   public static boolean isAllFlashSupportedOnCluster(ManagedObjectReference clusterRef) {
      VsanCapabilityData capabilityData = getCapabilities(clusterRef);
      return capabilityData != null && capabilityData.isAllFlashSupported;
   }

   public static boolean isAllFlashSupportedOnHost(ManagedObjectReference hostRef) {
      VsanCapabilityData capabilityData = getCapabilities(hostRef);
      return capabilityData != null && (capabilityData.isDisconnected || capabilityData.isAllFlashSupported);
   }

   public static boolean isLocalDataProtectionSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isLocalDataProtectionSupported;
   }

   public static boolean isLocalDataProtectionSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isLocalDataProtectionSupported;
   }

   public static boolean isArchiveDataProtectionSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isArchiveDataProtectionSupported;
   }

   public static boolean isArchiveDataProtectionSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isArchiveDataProtectionSupported;
   }

   public static boolean isRemoteDataProtectionSupported(ManagedObjectReference moRef) {
      return getCapabilities(moRef).isRemoteDataProtectionSupported;
   }

   public static boolean isRemoteDataProtectionSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isRemoteDataProtectionSupported;
   }

   public static boolean isResyncEnhancedApiSupported(ManagedObjectReference hostRef) {
      return getCapabilities(hostRef).isResyncEnhancedApiSupported;
   }

   public static boolean isFileServiceSupported(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isFileServiceSupported;
   }

   public static boolean isNetworkPerfTestSupportedOnCluster(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isNetworkPerfTestSupported;
   }

   public static boolean isVsanVumIntegrationSupported(ManagedObjectReference clusterRef) {
      return getVcCapabilities(clusterRef).isVsanVumIntegrationSupported;
   }

   public static boolean isVumBaselineRecommendationSupportedOnVc(ManagedObjectReference clusterRef) {
      return getVcCapabilities(clusterRef).isVumBaselineRecommendationSupported;
   }

   public static boolean isWhatIfCapacitySupported(ManagedObjectReference clusterRef) {
      return getVcCapabilities(clusterRef).isWhatIfCapacitySupported;
   }

   public static boolean isVitOnlineResizeSupportedOnCluster(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isVitOnlineResizeSupported;
   }

   public static boolean isVsanNestedFdsSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isNestedFdsSupported;
   }

   public static boolean isRepairTimerInResyncStatsSupported(ManagedObjectReference hostRef) {
      return getCapabilities(hostRef).isRepairTimerInResyncStatsSupported;
   }

   public static boolean isAutomaticRebalanceSupported(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isAutomaticRebalanceSupported;
   }

   public static boolean isPurgeInaccessibleVmSwapObjectsSupported(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isPurgeInaccessibleVmSwapObjectsSupported;
   }

   public static boolean isRecreateDiskGroupSupported(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isRecreateDiskGroupSupported;
   }

   public static boolean isUpdateVumReleaseCatalogOfflineSupported(ManagedObjectReference clusterRef) {
      return getVcCapabilities(clusterRef).isUpdateVumReleaseCatalogOfflineSupported;
   }

   public static boolean isAdvancedClusterSettingsSupported(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isAdvancedClusterOptionsSupported;
   }

   public static boolean isPerfDiagnosticModeSupported(ManagedObjectReference clusterRef) {
      VsanCapabilityData capabilityData = getCapabilities(clusterRef);
      _logger.debug("Performance Diagnostic Mode Supported: " + capabilityData.isPerfDiagnosticModeSupported);
      return capabilityData.isPerfDiagnosticModeSupported;
   }

   public static boolean isPerfDiagnosticsFeedbackSupportedOnVc(ManagedObjectReference clusterRef) {
      VsanCapabilityData capabilityData = getVcCapabilities(clusterRef);
      _logger.debug("Performance Diagnostics Feedback Supported: " + capabilityData.isPerfDiagnosticsFeedbackSupported);
      return capabilityData.isPerfDiagnosticsFeedbackSupported;
   }

   public static boolean isGetHclLastUpdateOnVcSupported(ManagedObjectReference vcRef) {
      VsanCapabilityData capabilityData = getVcCapabilities(vcRef);
      _logger.debug("GetHclLastUpdate methdo on VC Supported: " + capabilityData.isGetHclLastUpdateOnVcSupported);
      return capabilityData.isGetHclLastUpdateOnVcSupported;
   }

   public static boolean isVerboseModeInClusterConfigurationSupported(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isVerboseModeInClusterConfigurationSupported;
   }

   public static boolean isCnsVolumesSupportedOnVc(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isCnsVolumesSupported;
   }

   public static boolean isEvacuationStatusSupportedOnVc(ManagedObjectReference vcRef) {
      return getVcCapabilities(vcRef).isResourcePrecheckSupported;
   }

   public static boolean isEvacuationStatusSupportedOnCluster(ManagedObjectReference clusterRef) {
      return getCapabilities(clusterRef).isResourcePrecheckSupported;
   }

   public static boolean isHostReservedCapacitySupportedOnVc(ManagedObjectReference objectRef) {
      return getVcCapabilities(objectRef).isHostReservedCapacitySupported;
   }

   public static boolean isImprovedCapacityMonitoringSupportedOnVc(ManagedObjectReference moRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(moRef);
      return getVcCapabilities(clusterRef).isImprovedCapacityMonitoringSupported;
   }

   public static boolean isVmLevelCapacityMonitoringSupportedOnVc(ManagedObjectReference moRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(moRef);
      return getVcCapabilities(clusterRef).isVmLevelCapacityMonitoringSupported;
   }

   public static boolean isUnmountWithMaintenanceModeSupported(ManagedObjectReference moRef) {
      return getVcCapabilities(moRef).isUnmountWithMaintenanceModeSupported;
   }

   private static void validateMoRef(ManagedObjectReference moRef) {
      Validate.notNull(moRef);
      String type = moRef.getType();
      if (!ClusterComputeResource.class.getSimpleName().equals(type) && !HostSystem.class.getSimpleName().equals(type)) {
         throw new IllegalArgumentException("Unsupported ManagedObjectReference type given: " + type);
      }
   }
}
