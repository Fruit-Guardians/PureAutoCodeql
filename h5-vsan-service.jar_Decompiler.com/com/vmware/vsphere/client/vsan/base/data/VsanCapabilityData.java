package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vim.vsan.binding.vim.cluster.VsanCapability;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapabilityStatus;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.cache.Cacheable;
import org.apache.commons.lang.ArrayUtils;

@data
public class VsanCapabilityData implements Cacheable<VsanCapabilityData> {
   public boolean isDisconnected;
   public boolean isCapabilitiesSupported;
   public boolean isAllFlashSupported;
   public boolean isStretchedClusterSupported;
   public boolean isClusterConfigSupported;
   public boolean isDeduplicationAndCompressionSupported;
   public boolean isUpgradeSupported;
   public boolean isObjectIdentitiesSupported;
   public boolean isIscsiTargetsSupported;
   public boolean isWitnessManagementSupported;
   public boolean isPerfVerboseModeSupported;
   public boolean isPerfSvcAutoConfigSupported;
   public boolean isConfigAssistSupported;
   public boolean isUpdatesMgmtSupported;
   public boolean isWhatIfComplianceSupported;
   public boolean isPerfAnalysisSupported;
   public boolean isResyncThrottlingSupported;
   public boolean isEncryptionSupported;
   public boolean isWhatIfSupported;
   public boolean isLocalDataProtectionSupported;
   public boolean isArchiveDataProtectionSupported;
   public boolean isRemoteDataProtectionSupported;
   public boolean isCloudHealthSupported;
   public boolean isResyncEnhancedApiSupported;
   public boolean isFileServiceSupported;
   public boolean isCnsVolumesSupported;
   public boolean isNetworkPerfTestSupported;
   public boolean isVsanVumIntegrationSupported;
   public boolean isWhatIfCapacitySupported;
   public boolean isHistoricalCapacitySupported;
   public boolean isNestedFdsSupported;
   public boolean isRepairTimerInResyncStatsSupported;
   public boolean isPurgeInaccessibleVmSwapObjectsSupported;
   public boolean isRecreateDiskGroupSupported;
   public boolean isUpdateVumReleaseCatalogOfflineSupported;
   public boolean isAdvancedClusterOptionsSupported;
   public boolean isPerfDiagnosticModeSupported;
   public boolean isPerfDiagnosticsFeedbackSupported;
   public boolean isAdvancedPerformanceSupported;
   public boolean isGetHclLastUpdateOnVcSupported;
   public boolean isAutomaticRebalanceSupported;
   public boolean isRdmaSupported;
   public boolean isResyncETAImprovementSupported;
   public boolean isGuestTrimUnmapSupported;
   public boolean isVitOnlineResizeSupported;
   public boolean isVumBaselineRecommendationSupported;
   public boolean isSupportInsightSupported;
   public boolean isResourcePrecheckSupported;
   public boolean isVerboseModeInClusterConfigurationSupported;
   public boolean isImprovedCapacityMonitoringSupported;
   public boolean isVmLevelCapacityMonitoringSupported;
   public boolean isHostReservedCapacitySupported;
   public boolean isVsanCpuMetricsSupported;
   public boolean isFileServiceKerberosSupported;
   public boolean isUnmountWithMaintenanceModeSupported;

   public VsanCapabilityData clone() {
      VsanCapabilityData clone = new VsanCapabilityData();
      clone.isDisconnected = this.isDisconnected;
      clone.isClusterConfigSupported = this.isClusterConfigSupported;
      clone.isDeduplicationAndCompressionSupported = this.isDeduplicationAndCompressionSupported;
      clone.isObjectIdentitiesSupported = this.isObjectIdentitiesSupported;
      clone.isUpgradeSupported = this.isUpgradeSupported;
      clone.isStretchedClusterSupported = this.isStretchedClusterSupported;
      clone.isAllFlashSupported = this.isAllFlashSupported;
      clone.isCapabilitiesSupported = this.isCapabilitiesSupported;
      clone.isIscsiTargetsSupported = this.isIscsiTargetsSupported;
      clone.isWitnessManagementSupported = this.isWitnessManagementSupported;
      clone.isPerfVerboseModeSupported = this.isPerfVerboseModeSupported;
      clone.isPerfSvcAutoConfigSupported = this.isPerfSvcAutoConfigSupported;
      clone.isConfigAssistSupported = this.isConfigAssistSupported;
      clone.isUpdatesMgmtSupported = this.isUpdatesMgmtSupported;
      clone.isWhatIfComplianceSupported = this.isWhatIfComplianceSupported;
      clone.isPerfAnalysisSupported = this.isPerfAnalysisSupported;
      clone.isResyncThrottlingSupported = this.isResyncThrottlingSupported;
      clone.isEncryptionSupported = this.isEncryptionSupported;
      clone.isWhatIfSupported = this.isWhatIfSupported;
      clone.isCloudHealthSupported = this.isCloudHealthSupported;
      clone.isLocalDataProtectionSupported = this.isLocalDataProtectionSupported;
      clone.isArchiveDataProtectionSupported = this.isArchiveDataProtectionSupported;
      clone.isRemoteDataProtectionSupported = this.isRemoteDataProtectionSupported;
      clone.isResyncEnhancedApiSupported = this.isResyncEnhancedApiSupported;
      clone.isFileServiceSupported = this.isFileServiceSupported;
      clone.isCnsVolumesSupported = this.isCnsVolumesSupported;
      clone.isNetworkPerfTestSupported = this.isNetworkPerfTestSupported;
      clone.isVsanVumIntegrationSupported = this.isVsanVumIntegrationSupported;
      clone.isWhatIfCapacitySupported = this.isWhatIfCapacitySupported;
      clone.isHistoricalCapacitySupported = this.isHistoricalCapacitySupported;
      clone.isNestedFdsSupported = this.isNestedFdsSupported;
      clone.isRepairTimerInResyncStatsSupported = this.isRepairTimerInResyncStatsSupported;
      clone.isPurgeInaccessibleVmSwapObjectsSupported = this.isPurgeInaccessibleVmSwapObjectsSupported;
      clone.isRecreateDiskGroupSupported = this.isRecreateDiskGroupSupported;
      clone.isUpdateVumReleaseCatalogOfflineSupported = this.isUpdateVumReleaseCatalogOfflineSupported;
      clone.isAdvancedClusterOptionsSupported = this.isAdvancedClusterOptionsSupported;
      clone.isPerfDiagnosticModeSupported = this.isPerfDiagnosticModeSupported;
      clone.isPerfDiagnosticsFeedbackSupported = this.isPerfDiagnosticsFeedbackSupported;
      clone.isAdvancedPerformanceSupported = this.isAdvancedPerformanceSupported;
      clone.isGetHclLastUpdateOnVcSupported = this.isGetHclLastUpdateOnVcSupported;
      clone.isAutomaticRebalanceSupported = this.isAutomaticRebalanceSupported;
      clone.isRdmaSupported = this.isRdmaSupported;
      clone.isResyncETAImprovementSupported = this.isResyncETAImprovementSupported;
      clone.isGuestTrimUnmapSupported = this.isGuestTrimUnmapSupported;
      clone.isVitOnlineResizeSupported = this.isVitOnlineResizeSupported;
      clone.isVumBaselineRecommendationSupported = this.isVumBaselineRecommendationSupported;
      clone.isSupportInsightSupported = this.isSupportInsightSupported;
      clone.isResourcePrecheckSupported = this.isResourcePrecheckSupported;
      clone.isVerboseModeInClusterConfigurationSupported = this.isVerboseModeInClusterConfigurationSupported;
      clone.isImprovedCapacityMonitoringSupported = this.isImprovedCapacityMonitoringSupported;
      clone.isVmLevelCapacityMonitoringSupported = this.isVmLevelCapacityMonitoringSupported;
      clone.isHostReservedCapacitySupported = this.isHostReservedCapacitySupported;
      clone.isVsanCpuMetricsSupported = this.isVsanCpuMetricsSupported;
      clone.isFileServiceKerberosSupported = this.isFileServiceKerberosSupported;
      clone.isUnmountWithMaintenanceModeSupported = this.isUnmountWithMaintenanceModeSupported;
      return clone;
   }

   public static VsanCapabilityData fromVsanCapability(VsanCapability vsanCapability) {
      VsanCapabilityData result = new VsanCapabilityData();
      String capability;
      int var3;
      int var4;
      String[] var5;
      if (vsanCapability.statuses != null) {
         var4 = (var5 = vsanCapability.statuses).length;

         for(var3 = 0; var3 < var4; ++var3) {
            capability = var5[var3];
            if (capability.equals(VsanCapabilityStatus.disconnected.toString())) {
               result.isDisconnected = true;
               break;
            }
         }
      }

      if (vsanCapability != null && !ArrayUtils.isEmpty(vsanCapability.capabilities)) {
         var4 = (var5 = vsanCapability.capabilities).length;

         for(var3 = 0; var3 < var4; ++var3) {
            capability = var5[var3];
            switch(capability.hashCode()) {
            case -2036548965:
               if (capability.equals("whatifcapacity")) {
                  result.isWhatIfCapacitySupported = true;
               }
               break;
            case -1899620047:
               if (capability.equals("diagnosticsfeedback")) {
                  result.isPerfDiagnosticsFeedbackSupported = true;
               }
               break;
            case -1854461844:
               if (capability.equals("updatevumreleasecatalogoffline")) {
                  result.isUpdateVumReleaseCatalogOfflineSupported = true;
               }
               break;
            case -1806256184:
               if (capability.equals("perfsvcautoconfig")) {
                  result.isPerfSvcAutoConfigSupported = true;
               }
               break;
            case -1804936456:
               if (capability.equals("throttleresync")) {
                  result.isResyncThrottlingSupported = true;
               }
               break;
            case -1682528408:
               if (capability.equals("vitonlineresize")) {
                  result.isVitOnlineResizeSupported = true;
               }
               break;
            case -1636192110:
               if (capability.equals("improvedcapacityscreen")) {
                  result.isImprovedCapacityMonitoringSupported = true;
               }
               break;
            case -1544169576:
               if (capability.equals("netperftest")) {
                  result.isNetworkPerfTestSupported = true;
               }
               break;
            case -1512632445:
               if (capability.equals("encryption")) {
                  result.isEncryptionSupported = true;
               }
               break;
            case -1379946052:
               if (capability.equals("clusterconfig")) {
                  result.isClusterConfigSupported = true;
               }
               break;
            case -1364523474:
               if (capability.equals("localdataprotection")) {
                  result.isLocalDataProtectionSupported = true;
               }
               break;
            case -1195883754:
               if (capability.equals("performanceforsupport")) {
                  result.isAdvancedPerformanceSupported = true;
               }
               break;
            case -1162221165:
               if (capability.equals("dataefficiency")) {
                  result.isDeduplicationAndCompressionSupported = true;
               }
               break;
            case -1160008507:
               if (capability.equals("perfanalysis")) {
                  result.isPerfAnalysisSupported = true;
               }
               break;
            case -1128660023:
               if (capability.equals("remotedataprotection")) {
                  result.isRemoteDataProtectionSupported = true;
               }
               break;
            case -936991535:
               if (capability.equals("cloudhealth")) {
                  result.isCloudHealthSupported = true;
               }
               break;
            case -903430425:
               if (capability.equals("vmlevelcapacity")) {
                  result.isVmLevelCapacityMonitoringSupported = true;
               }
               break;
            case -831219140:
               if (capability.equals("witnessmanagement")) {
                  result.isWitnessManagementSupported = true;
               }
               break;
            case -783669992:
               if (capability.equals("capability")) {
                  result.isCapabilitiesSupported = true;
               }
               break;
            case -713444186:
               if (capability.equals("gethcllastupdateonvc")) {
                  result.isGetHclLastUpdateOnVcSupported = true;
               }
               break;
            case -525799692:
               if (capability.equals("repairtimerinresyncstats")) {
                  result.isRepairTimerInResyncStatsSupported = true;
               }
               break;
            case -458757541:
               if (capability.equals("objectidentities")) {
                  result.isObjectIdentitiesSupported = true;
               }
               break;
            case -335589684:
               if (capability.equals("genericnestedfd")) {
                  result.isNestedFdsSupported = true;
               }
               break;
            case -299262582:
               if (capability.equals("hostreservedcapacity")) {
                  result.isHostReservedCapacitySupported = true;
               }
               break;
            case -276865004:
               if (capability.equals("fileservicekerberos")) {
                  result.isFileServiceKerberosSupported = true;
               }
               break;
            case -233924644:
               if (capability.equals("perfsvcvsancpumetrics")) {
                  result.isVsanCpuMetricsSupported = true;
               }
               break;
            case -231171556:
               if (capability.equals("upgrade")) {
                  result.isUpgradeSupported = true;
               }
               break;
            case -144980092:
               if (capability.equals("historicalcapacity")) {
                  result.isHistoricalCapacitySupported = true;
               }
               break;
            case -86618386:
               if (capability.equals("perfsvcverbosemode")) {
                  result.isPerfVerboseModeSupported = true;
               }
               break;
            case -39348687:
               if (capability.equals("cnsvolumes")) {
                  result.isCnsVolumesSupported = true;
               }
               break;
            case 3593415:
               if (!capability.equals("umap")) {
               }
               break;
            case 26254577:
               if (capability.equals("archivaldataprotection")) {
                  result.isArchiveDataProtectionSupported = true;
               }
               break;
            case 46516698:
               if (capability.equals("fileservices")) {
                  result.isFileServiceSupported = true;
               }
               break;
            case 84189054:
               if (capability.equals("automaticrebalance")) {
                  result.isAutomaticRebalanceSupported = true;
               }
               break;
            case 92063072:
               if (capability.equals("complianceprecheck")) {
                  result.isWhatIfComplianceSupported = true;
               }
               break;
            case 289188182:
               if (capability.equals("enhancedresyncapi")) {
                  result.isResyncEnhancedApiSupported = true;
               }
               break;
            case 560333555:
               if (capability.equals("recreatediskgroup")) {
                  result.isRecreateDiskGroupSupported = true;
               }
               break;
            case 599591979:
               if (capability.equals("configassist")) {
                  result.isConfigAssistSupported = true;
               }
               break;
            case 613526532:
               if (capability.equals("purgeinaccessiblevmswapobjects")) {
                  result.isPurgeInaccessibleVmSwapObjectsSupported = true;
               }
               break;
            case 1114122305:
               if (capability.equals("decomwhatif")) {
                  result.isWhatIfSupported = true;
               }
               break;
            case 1148008019:
               if (capability.equals("resourceprecheck")) {
                  result.isResourcePrecheckSupported = true;
               }
               break;
            case 1252691946:
               if (capability.equals("fullStackFw")) {
                  result.isVsanVumIntegrationSupported = true;
               }
               break;
            case 1265995522:
               if (capability.equals("clusteradvancedoptions")) {
                  result.isAdvancedClusterOptionsSupported = true;
               }
               break;
            case 1330664524:
               if (capability.equals("vumbaselinerecommendation")) {
                  result.isVumBaselineRecommendationSupported = true;
               }
               break;
            case 1338309188:
               if (capability.equals("firmwareupdate")) {
                  result.isUpdatesMgmtSupported = true;
               }
               break;
            case 1344181014:
               if (capability.equals("stretchedcluster")) {
                  result.isStretchedClusterSupported = true;
               }
               break;
            case 1359008240:
               if (capability.equals("vsanrdma")) {
                  result.isRdmaSupported = true;
               }
               break;
            case 1487189745:
               if (capability.equals("verbosemodeconfiguration")) {
                  result.isVerboseModeInClusterConfigurationSupported = true;
               }
               break;
            case 1567155603:
               if (capability.equals("iscsitargets")) {
                  result.isIscsiTargetsSupported = true;
               }
               break;
            case 1804489455:
               if (capability.equals("allflash")) {
                  result.isAllFlashSupported = true;
               }
               break;
            case 1930300114:
               if (capability.equals("resyncetaimprovement")) {
                  result.isResyncETAImprovementSupported = true;
               }
               break;
            case 1934435561:
               if (capability.equals("supportinsight")) {
                  result.isSupportInsightSupported = true;
               }
               break;
            case 2101078474:
               if (capability.equals("diagnosticmode")) {
                  result.isPerfDiagnosticModeSupported = true;
               }
            }
         }
      }

      return result;
   }
}
