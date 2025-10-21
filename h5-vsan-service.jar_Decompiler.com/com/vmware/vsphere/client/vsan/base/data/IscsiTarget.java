package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vim.vsan.binding.vim.cluster.StorageComplianceResult;
import com.vmware.vim.vsan.binding.vim.cluster.StorageOperationalStatus;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetAuthSpec;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.config.VsanIscsiAuthSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;

@data
public class IscsiTarget extends VsanObject {
   public String iqn;
   public String alias;
   public Integer lunCount;
   public String networkInterface;
   public Integer port;
   public String ioOwnerHost;
   public String authType;
   public String vmStoragePolicyUuid;
   public Date lastChecked;
   public VsanOperationalStatus operationalStatus;
   public List<IscsiLun> luns = new ArrayList();
   public VsanIscsiAuthSpec authSpec;

   public IscsiTarget() {
      this.objectType = VsanObjectType.iscsiTarget;
   }

   public IscsiTarget(VsanIscsiTarget iscsi, List<VsanIscsiLUN> iscsiLuns, Map<String, String> storageProfiles, Object namespaceMetadata) {
      this.objectType = VsanObjectType.iscsiTarget;
      this.iqn = iscsi.iqn;
      this.alias = iscsi.alias;
      this.name = iscsi.alias;
      this.lunCount = iscsi.lunCount;
      this.networkInterface = iscsi.networkInterface;
      this.port = iscsi.port;
      this.ioOwnerHost = iscsi.ioOwnerHost;
      this.authType = iscsi.authSpec.authType;
      this.authSpec = this.getVsanIscsiAuthSpec(iscsi.authSpec);
      this.namespaceCapabilityMetadata = namespaceMetadata;
      if (iscsi.objectInformation != null) {
         this.vsanObjectUuid = iscsi.objectInformation.vsanObjectUuid;
         this.healthState = VsanObjectHealthState.fromServerLocalizedString(iscsi.objectInformation.vsanHealth);
         if (iscsi.objectInformation.spbmProfileUuid != null) {
            this.vmStoragePolicyUuid = iscsi.objectInformation.spbmProfileUuid;
            this.storagePolicy = storageProfiles.containsKey(this.vmStoragePolicyUuid) ? (String)storageProfiles.get(this.vmStoragePolicyUuid) : this.vmStoragePolicyUuid;
         }

         StorageComplianceResult storageStatus = iscsi.objectInformation.spbmComplianceResult;
         if (storageStatus != null) {
            this.complianceResult = BaseUtils.toComplianceResult(storageStatus);
            this.complianceStatus = BaseUtils.getComplianceStatus(this.complianceResult);
            this.lastChecked = storageStatus.checkTime.getTime();
            StorageOperationalStatus opStatus = storageStatus.operationalStatus;
            if (opStatus != null) {
               this.operationalStatus = BaseUtils.getOperationalState(opStatus);
            }
         }
      }

      if (!CollectionUtils.isEmpty(iscsiLuns)) {
         Iterator var8 = iscsiLuns.iterator();

         while(var8.hasNext()) {
            VsanIscsiLUN lun = (VsanIscsiLUN)var8.next();
            this.luns.add(new IscsiLun(lun, storageProfiles, this.iqn, this.namespaceCapabilityMetadata));
         }

      }
   }

   private VsanIscsiAuthSpec getVsanIscsiAuthSpec(VsanIscsiTargetAuthSpec sourceAuthSpec) {
      if (sourceAuthSpec == null) {
         return null;
      } else {
         VsanIscsiAuthSpec authSpec = new VsanIscsiAuthSpec();
         authSpec.authType = sourceAuthSpec.authType;
         authSpec.initiatorSecret = sourceAuthSpec.userSecretAttachToInitiator;
         authSpec.initiatorUsername = sourceAuthSpec.userNameAttachToInitiator;
         authSpec.targetSecret = sourceAuthSpec.userSecretAttachToTarget;
         authSpec.targetUsername = sourceAuthSpec.userNameAttachToTarget;
         return authSpec;
      }
   }
}
