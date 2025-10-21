package com.vmware.vsphere.client.vsandp.controllers.vm.summary;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.ArchivalProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.IntervalProtectionPolicyInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.LimitedInstanceRetentionPolicyInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.LocalProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.NfsArchivalStorageLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.ProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.ProtectionPolicyInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.ProtectionState;
import com.vmware.vim.vsandp.binding.vim.vsandp.RemoteProtectionInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.ReplicatedProtectionPolicyInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.RetentionPolicyInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.RpoProtectionPolicyInfo;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.capacity.VmCapacityDataService;
import com.vmware.vsan.client.services.capacity.model.VmCapacityData;
import com.vmware.vsan.client.services.dataprotection.ClusterDpConfigService;
import com.vmware.vsan.client.services.dataprotection.model.ClusterDpConfigData;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import com.vmware.vsphere.client.vsandp.controllers.vm.summary.model.VmArchiveDataProtectionData;
import com.vmware.vsphere.client.vsandp.controllers.vm.summary.model.VmDataProtectionBaseData;
import com.vmware.vsphere.client.vsandp.controllers.vm.summary.model.VmDataProtectionData;
import com.vmware.vsphere.client.vsandp.controllers.vm.summary.model.VmLocalDataProtectionData;
import com.vmware.vsphere.client.vsandp.controllers.vm.summary.model.VmRemoteDataProtectionData;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmDataProtectionSummaryController {
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private ClusterDpConfigService dpConfigService;
   @Autowired
   private VmCapacityDataService vmCapacityService;
   private static final Logger logger = LoggerFactory.getLogger(VmDataProtectionSummaryController.class);

   @TsService
   public VmDataProtectionData getVmProtectionData(ManagedObjectReference vmRef) throws Exception {
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      CgInfo vmCgInfo = this.cgProvider.getCgInfo(vmRef, clusterRef);
      if (vmCgInfo == null) {
         logger.error("No protection data found for VM {}", vmRef);
         return null;
      } else {
         ClusterDpConfigData dpConfig = null;
         if (vmCgInfo.getArchive() != null || vmCgInfo.getRemote() != null) {
            dpConfig = this.dpConfigService.getClusterDpConfig(clusterRef);
         }

         return new VmDataProtectionData(this.getLocalProtectionData(vmCgInfo, vmRef), this.getArchiveProtectionData(vmCgInfo, dpConfig), this.getRemoteProtectionData(vmCgInfo, dpConfig), this.inventoryHelper.isVmRestoreAllowed(vmRef));
      }
   }

   private VmLocalDataProtectionData getLocalProtectionData(CgInfo vmCgInfo, ManagedObjectReference vmRef) {
      LocalProtectionInfo localCgInfo = vmCgInfo.getLocal();
      if (localCgInfo == null) {
         return null;
      } else {
         VmLocalDataProtectionData localProtectionData = new VmLocalDataProtectionData();
         this.populateBaseProtectionData(localProtectionData, localCgInfo);
         if (StringUtils.isEmpty(localProtectionData.lastError)) {
            localProtectionData.rpoInterval = this.getRpo(localCgInfo.protectionPolicy);
         }

         boolean isVmCapacitySupported = VsanCapabilityUtils.isVmLevelCapacityMonitoringSupportedOnVc(vmRef);
         if (isVmCapacitySupported) {
            localProtectionData.storageUsage = this.getLocalStorageUsage(vmRef);
         }

         return localProtectionData;
      }
   }

   private void populateBaseProtectionData(VmDataProtectionBaseData baseProtectionData, ProtectionInfo protectionInfo) {
      if (protectionInfo.getCurrentError() != null) {
         baseProtectionData.healthState = VsanObjectDataProtectionHealthState.UNKNOWN;
         baseProtectionData.lastError = protectionInfo.getCurrentError().getLocalizedMessage();
      } else {
         baseProtectionData.healthState = VsanObjectDataProtectionHealthState.fromProtectionState(ProtectionState.valueOf(protectionInfo.getState()));
         if (protectionInfo.series != null && protectionInfo.series.lastInstanceTimestamp != null) {
            baseProtectionData.latestSyncPoint = protectionInfo.series.lastInstanceTimestamp.getTime();
         }

         baseProtectionData.quiescingFrequency = this.getQuiescingFrequency(protectionInfo.getProtectionPolicy());
         baseProtectionData.retentionCount = this.getRetentionCount(protectionInfo.getRetentionPolicy());
      }
   }

   private Integer getQuiescingFrequency(ProtectionPolicyInfo protectionPolicyInfo) {
      Integer quiescingFreq = protectionPolicyInfo.getQuiescingFrequency();
      return quiescingFreq != null ? quiescingFreq + 1 : null;
   }

   private Integer getRetentionCount(RetentionPolicyInfo retentionPolicyInfo) {
      return retentionPolicyInfo instanceof LimitedInstanceRetentionPolicyInfo ? ((LimitedInstanceRetentionPolicyInfo)retentionPolicyInfo).getNumInstances() : null;
   }

   private long getLocalStorageUsage(ManagedObjectReference vmRef) {
      VmCapacityData vmSpaceUsage = this.vmCapacityService.getVmSpaceUsage(vmRef);
      return vmSpaceUsage.dataProtectionPrimaryUsage + vmSpaceUsage.dataProtectionRaidOverhead;
   }

   private VmArchiveDataProtectionData getArchiveProtectionData(CgInfo vmCgInfo, ClusterDpConfigData dpConfig) throws VsanUiLocalizableException {
      if (ArrayUtils.isEmpty(vmCgInfo.getArchive())) {
         return null;
      } else {
         ArchivalProtectionInfo archiveCgInfo = vmCgInfo.getArchive()[0];
         VmArchiveDataProtectionData archiveProtectionData = new VmArchiveDataProtectionData();
         this.populateBaseProtectionData(archiveProtectionData, archiveCgInfo);
         if (StringUtils.isEmpty(archiveProtectionData.lastError)) {
            if (archiveCgInfo.location == null || !(archiveCgInfo.location instanceof NfsArchivalStorageLocation)) {
               logger.error("Missing archive protection location {}", archiveCgInfo);
               throw new VsanUiLocalizableException("vsan.dataprotection.vm.archive.location.missing.error");
            }

            String datastoreUrl = ((NfsArchivalStorageLocation)archiveCgInfo.location).datastore;
            if (dpConfig == null) {
               logger.error("Missing data protection configuration for cluster {}", datastoreUrl);
            } else {
               archiveProtectionData.setDatastoreInfo(dpConfig.archivalDpDatastoreRef, dpConfig.archivalDpDatastoreName, datastoreUrl);
            }

            int localRpo = this.getRpo(vmCgInfo.getLocal().protectionPolicy);
            int protectionFrequency = this.getProtectionFrequency(archiveCgInfo.protectionPolicy);
            archiveProtectionData.rpoInterval = localRpo * protectionFrequency;
         }

         return archiveProtectionData;
      }
   }

   private VmRemoteDataProtectionData getRemoteProtectionData(CgInfo vmCgInfo, ClusterDpConfigData dpConfig) {
      if (ArrayUtils.isEmpty(vmCgInfo.getRemote())) {
         return null;
      } else {
         RemoteProtectionInfo remoteCgInfo = vmCgInfo.getRemote()[0];
         VmRemoteDataProtectionData remoteProtectionData = new VmRemoteDataProtectionData();
         this.populateBaseProtectionData(remoteProtectionData, remoteCgInfo);
         if (StringUtils.isEmpty(remoteProtectionData.lastError)) {
            if (remoteCgInfo.location == null || StringUtils.isEmpty(remoteCgInfo.location.cluster)) {
               logger.error("Missing remote protection location {}", remoteCgInfo);
            }

            String clusterUuid = remoteCgInfo.location.cluster;
            if (dpConfig == null) {
               logger.error("Missing data protection configuration for cluster {}", clusterUuid);
            } else {
               remoteProtectionData.setTargetClusterInfo(dpConfig.remoteClusterRef, dpConfig.remoteClusterName, clusterUuid).setTargetVcInfo(dpConfig.remoteVcRef, dpConfig.remoteVcName);
            }

            remoteProtectionData.rpoInterval = this.getRpo(remoteCgInfo.protectionPolicy);
         }

         return remoteProtectionData;
      }
   }

   private int getRpo(ProtectionPolicyInfo policyInfo) {
      if (policyInfo instanceof IntervalProtectionPolicyInfo) {
         return ((IntervalProtectionPolicyInfo)policyInfo).minutes;
      } else {
         return policyInfo instanceof RpoProtectionPolicyInfo ? ((RpoProtectionPolicyInfo)policyInfo).rpo : 0;
      }
   }

   private int getProtectionFrequency(ProtectionPolicyInfo policyInfo) {
      return policyInfo instanceof ReplicatedProtectionPolicyInfo ? ((ReplicatedProtectionPolicyInfo)policyInfo).protectionFrequency : 0;
   }
}
