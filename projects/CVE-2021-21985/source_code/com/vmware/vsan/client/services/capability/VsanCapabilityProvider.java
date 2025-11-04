package com.vmware.vsan.client.services.capability;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanCapabilityProvider {
   private static final Log _logger = LogFactory.getLog(VsanCapabilityProvider.class);
   @Autowired
   private ObjectReferenceService objectRefService;

   @TsService
   public VsanCapabilityData getVcCapabilityData(ManagedObjectReference moRef) {
      VsanCapabilityData capabilityData = new VsanCapabilityData();

      try {
         capabilityData = VsanCapabilityUtils.getVcCapabilities(moRef);
      } catch (Exception var4) {
         _logger.error("Cannot load capabilities", var4);
      }

      return capabilityData;
   }

   @TsService
   public VsanCapabilityData getClusterCapabilityData(ManagedObjectReference moRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(moRef);
      VsanCapabilityData capabilityData = new VsanCapabilityData();

      try {
         capabilityData = VsanCapabilityUtils.getCapabilities(clusterRef);
      } catch (Exception var5) {
         _logger.error("Cannot load capabilities", var5);
      }

      return capabilityData;
   }

   @TsService
   public VsanCapabilityData getHostCapabilityData(ManagedObjectReference moRef) {
      VsanCapabilityData capabilityData = new VsanCapabilityData();

      try {
         capabilityData = VsanCapabilityUtils.getCapabilities(moRef);
      } catch (Exception var4) {
         _logger.error("Cannot load capabilities", var4);
      }

      return capabilityData;
   }

   @TsService
   public Map<String, VsanCapabilityData> getHostsCapabilitiyData(ManagedObjectReference[] hostRefs) {
      Map<String, VsanCapabilityData> result = new HashMap();
      ManagedObjectReference[] var6 = hostRefs;
      int var5 = hostRefs.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference hostRef = var6[var4];
         result.put(this.objectRefService.getUid(hostRef), this.getHostCapabilityData(hostRef));
      }

      return result;
   }

   @TsService
   public boolean getIsDeduplicationSupported(ManagedObjectReference clusterRef) {
      boolean dedupSupported = VsanCapabilityUtils.isDeduplicationAndCompressionSupported(clusterRef);
      return dedupSupported;
   }

   @TsService
   public boolean getIsEncryptionSupported(ManagedObjectReference clusterRef) {
      boolean encryptionSupported = VsanCapabilityUtils.isEncryptionSupported(clusterRef);
      return encryptionSupported;
   }

   @TsService
   public boolean getIsLocalDataProtectionSupportedOnVc(ManagedObjectReference objectRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      return this.getVcCapabilityData(clusterRef).isLocalDataProtectionSupported;
   }

   @TsService
   public boolean getIsLocalDataProtectionSupportedOnCluster(ManagedObjectReference objectRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      return this.getClusterCapabilityData(clusterRef).isLocalDataProtectionSupported;
   }

   @TsService
   public boolean getIsRemoteDataProtectionSupported(ManagedObjectReference objectRef) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      return this.getClusterCapabilityData(clusterRef).isRemoteDataProtectionSupported;
   }

   @TsService
   public boolean getIsObjectIdentitiesSupportedOnCluster(ManagedObjectReference clusterRef) {
      return this.getClusterCapabilityData(clusterRef).isObjectIdentitiesSupported;
   }

   @TsService
   public boolean getIsHistoricalCapacitySupported(ManagedObjectReference objectRef) {
      return VsanCapabilityUtils.getIsHistoricalCapacitySupported(objectRef);
   }

   @TsService
   public boolean getIsPerfVerboseModeSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isPerfVerboseModeSupportedOnVc(clusterRef);
   }

   @TsService
   public boolean getIsPerfNetworkDiagnosticModeSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isPerfDiagnosticModeSupported(clusterRef);
   }

   @TsService
   public boolean getIsPerfDiagnosticsFeedbackSupportedOnVc(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isPerfDiagnosticsFeedbackSupportedOnVc(clusterRef);
   }

   @TsService
   public boolean getIsAdvancedClusterSettingsSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isAdvancedClusterSettingsSupported(clusterRef);
   }

   @TsService
   public boolean getIsRecreateDiskGroupSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isRecreateDiskGroupSupported(clusterRef);
   }

   @TsService
   public boolean getIsPurgeInaccessibleVmSwapObjectsSupported(ManagedObjectReference moRef) {
      return VsanCapabilityUtils.isPurgeInaccessibleVmSwapObjectsSupported(moRef);
   }

   @TsService
   public boolean getIsUpdateVumReleaseCatalogOfflineSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isUpdateVumReleaseCatalogOfflineSupported(clusterRef);
   }

   @TsService
   public boolean getIsVitOnlineResizeSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isVitOnlineResizeSupportedOnCluster(clusterRef);
   }

   @TsService
   public boolean getIsImprovedCapacityMonitoringSupportedOnVc(ManagedObjectReference objectRef) {
      return VsanCapabilityUtils.isImprovedCapacityMonitoringSupportedOnVc(objectRef);
   }

   @TsService
   public boolean getIsVmLevelCapacityMonitoringSupported(ManagedObjectReference objectRef) {
      return VsanCapabilityUtils.isVmLevelCapacityMonitoringSupportedOnVc(objectRef);
   }

   @TsService
   public boolean getIsWhatIfCapacitySupported(ManagedObjectReference clusterRef) {
      return this.getClusterCapabilityData(clusterRef).isWhatIfCapacitySupported;
   }

   @TsService
   public boolean getIsHostReservedCapacitySupported(ManagedObjectReference vcRef) {
      return VsanCapabilityUtils.isHostReservedCapacitySupportedOnVc(vcRef);
   }

   @TsService
   public boolean getIsUnmountWithMaintenanceModeSupported(ManagedObjectReference moRef) {
      return VsanCapabilityUtils.isUnmountWithMaintenanceModeSupported(moRef);
   }

   @TsService
   public boolean getIsEvacuationStatusSupportedOnCluster(ManagedObjectReference moRef) {
      return VsanCapabilityUtils.isEvacuationStatusSupportedOnCluster(moRef);
   }
}
