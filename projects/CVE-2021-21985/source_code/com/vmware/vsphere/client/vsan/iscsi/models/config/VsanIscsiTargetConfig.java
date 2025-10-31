package com.vmware.vsphere.client.vsan.iscsi.models.config;

import com.vmware.vim.binding.pbm.compliance.ComplianceResult.ComplianceStatus;
import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.vsan.binding.vim.cluster.StorageComplianceResult;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.StorageCompliance;
import com.vmware.vsphere.client.vsan.base.data.StoragePolicyData;

@data
public class VsanIscsiTargetConfig {
   public boolean emptyCluster;
   public boolean iscsiSupportedOnCluster;
   public boolean hostsVersionValid;
   public boolean status;
   public String network;
   public Integer port;
   public VsanIscsiTargetConfig.IscsiTargetAuthType authType;
   public String homeObjectUuid;
   public StorageCompliance homeObjectStorageCompliance;
   public VsanIscsiTargetConfig.ObjectHealthStatus homeObjectHealthStatus;
   public StoragePolicyData homeObjectStorageProfile;
   public String homeObjectStorageProfileUuid;
   public String incomingUser;
   public String incomingSecret;
   public String outgoingUser;
   public String outgoingSecret;

   public VsanIscsiTargetConfig(VsanIscsiConfig config, boolean emptyCluster, boolean iscsiSupportedOnCluster, boolean hostsVersionValid, Profile profile) {
      this.emptyCluster = emptyCluster;
      this.iscsiSupportedOnCluster = iscsiSupportedOnCluster;
      this.hostsVersionValid = hostsVersionValid;
      if (config == null) {
         this.iscsiSupportedOnCluster = false;
      } else {
         if (config.vsanIscsiTargetServiceConfig != null) {
            this.status = config.vsanIscsiTargetServiceConfig.enabled;
            if (config.vsanIscsiTargetServiceConfig.defaultConfig != null) {
               this.network = config.vsanIscsiTargetServiceConfig.defaultConfig.networkInterface;
               this.port = config.vsanIscsiTargetServiceConfig.defaultConfig.port;
               if (config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec != null && config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.authType != null) {
                  this.authType = VsanIscsiTargetConfig.IscsiTargetAuthType.valueOf(config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.authType);
                  this.incomingUser = config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.userNameAttachToTarget;
                  this.incomingSecret = config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.userSecretAttachToTarget;
                  this.outgoingUser = config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.userNameAttachToInitiator;
                  this.outgoingSecret = config.vsanIscsiTargetServiceConfig.defaultConfig.iscsiTargetAuthSpec.userSecretAttachToInitiator;
               }
            }
         }

         if (config.vsanObjectInformation != null) {
            this.homeObjectUuid = config.vsanObjectInformation.vsanObjectUuid;
            this.homeObjectStorageCompliance = this.getStorageComplianceStatus(config.vsanObjectInformation.spbmComplianceResult);
            this.homeObjectHealthStatus = this.getHomeObjectHealthStatus(config.vsanObjectInformation.spbmComplianceResult);
            this.homeObjectStorageProfile = profile == null ? null : new StoragePolicyData(profile);
            this.homeObjectStorageProfileUuid = config.vsanObjectInformation.spbmProfileUuid;
         }

      }
   }

   private StorageCompliance getStorageComplianceStatus(StorageComplianceResult storageComplianceResult) {
      if (storageComplianceResult == null) {
         return StorageCompliance.unknown;
      } else if (storageComplianceResult.profile == null) {
         return null;
      } else if (storageComplianceResult.mismatch) {
         return StorageCompliance.outOfDate;
      } else if (storageComplianceResult.complianceStatus.equals(ComplianceStatus.compliant.toString())) {
         return StorageCompliance.compliant;
      } else if (storageComplianceResult.complianceStatus.equals(ComplianceStatus.nonCompliant.toString())) {
         return StorageCompliance.nonCompliant;
      } else {
         return storageComplianceResult.complianceStatus.equals(ComplianceStatus.notApplicable.toString()) ? StorageCompliance.notApplicable : StorageCompliance.unknown;
      }
   }

   private VsanIscsiTargetConfig.ObjectHealthStatus getHomeObjectHealthStatus(StorageComplianceResult storageComplianceResult) {
      if (storageComplianceResult != null && storageComplianceResult.operationalStatus != null) {
         boolean transitional = false;
         if (storageComplianceResult.operationalStatus.transitional != null) {
            transitional = storageComplianceResult.operationalStatus.transitional;
         }

         if (storageComplianceResult.operationalStatus.healthy) {
            return transitional ? VsanIscsiTargetConfig.ObjectHealthStatus.transitionalHealthy : VsanIscsiTargetConfig.ObjectHealthStatus.healthy;
         } else {
            return transitional ? VsanIscsiTargetConfig.ObjectHealthStatus.transitionalUnhealthy : VsanIscsiTargetConfig.ObjectHealthStatus.unhealthy;
         }
      } else {
         return VsanIscsiTargetConfig.ObjectHealthStatus.unknown;
      }
   }

   @data
   public static enum IscsiTargetAuthType {
      NoAuth,
      CHAP,
      CHAP_Mutual;
   }

   @data
   public static enum ObjectHealthStatus {
      transitionalHealthy,
      healthy,
      transitionalUnhealthy,
      unhealthy,
      unknown;
   }
}
