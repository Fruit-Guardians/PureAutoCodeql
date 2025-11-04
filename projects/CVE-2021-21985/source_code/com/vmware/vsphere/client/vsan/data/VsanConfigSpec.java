package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.vim.encryption.KeyProviderId;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo.HostDefaultInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanDiskMappingsConfigSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainsConfigSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHostDiskMapping;
import com.vmware.vim.vsan.binding.vim.cluster.VsanWitnessSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHostDiskMapping.VsanDiskGroupCreationType;
import com.vmware.vim.vsan.binding.vim.vsan.DataEfficiencyConfig;
import com.vmware.vim.vsan.binding.vim.vsan.DataEncryptionConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.ProxygenSerializer;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.spec.VsanFaultDomainSpec;
import com.vmware.vsphere.client.vsan.spec.VsanSemiAutoDiskMappingsSpec;
import com.vmware.vsphere.client.vsan.spec.VsanSemiAutoDiskSpec;
import com.vmware.vsphere.client.vsan.stretched.VsanStretchedClusterConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

@data
public class VsanConfigSpec {
   private static final long serialVersionUID = 1L;
   public boolean enabledDedup;
   public boolean allowReducedRedundancy;
   public boolean enableEncryption;
   public boolean eraseDisksBeforeUse;
   public String kmipClusterId;
   public boolean largeScaleClusterSupport;
   public VsanStretchedClusterConfig stretchedClusterConfig;
   public boolean autoClaimDisks;
   public boolean enableRdma;
   @ProxygenSerializer.ElementType(VsanSemiAutoDiskMappingsSpec.class)
   public List<VsanSemiAutoDiskMappingsSpec> diskMappings;
   @ProxygenSerializer.ElementType(VsanFaultDomainSpec.class)
   public List<VsanFaultDomainSpec> faultDomainSpecs;

   public ReconfigSpec getReconfigSpec(ManagedObjectReference clusterRef, boolean hasEncryptionPermissions) throws Exception {
      ConfigInfo vsanClusterConfig = this.getVsanConfigInfo();
      DataEfficiencyConfig dedupConfig = null;
      if (VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef)) {
         dedupConfig = this.getDataEfficiencySpec();
      }

      DataEncryptionConfig encryptionConfig = null;
      if (VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef) && hasEncryptionPermissions) {
         encryptionConfig = this.getEncryptionSpec();
      }

      ReconfigSpec reconfigSpec = this.getBasicReconfigSpec();
      reconfigSpec.vsanClusterConfig = vsanClusterConfig;
      reconfigSpec.dataEfficiencyConfig = dedupConfig;
      reconfigSpec.dataEncryptionConfig = encryptionConfig;
      reconfigSpec.allowReducedRedundancy = this.allowReducedRedundancy;
      return reconfigSpec;
   }

   public ReconfigSpec getBasicReconfigSpec() throws Exception {
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.diskMappingSpec = this.getDiskMappingsConfigSpec();
      reconfigSpec.faultDomainsSpec = this.getFdSpec();
      reconfigSpec.modify = true;
      return reconfigSpec;
   }

   private ConfigInfo getVsanConfigInfo() {
      ConfigInfo vsanClusterConfig = new ConfigInfo();
      vsanClusterConfig.enabled = true;
      vsanClusterConfig.defaultConfig = new HostDefaultInfo();
      vsanClusterConfig.defaultConfig.autoClaimStorage = this.autoClaimDisks;
      return vsanClusterConfig;
   }

   private DataEncryptionConfig getEncryptionSpec() {
      DataEncryptionConfig encryptionConfig = new DataEncryptionConfig();
      encryptionConfig.encryptionEnabled = this.enableEncryption;
      if (encryptionConfig.encryptionEnabled) {
         if (!StringUtils.isEmpty(this.kmipClusterId)) {
            encryptionConfig.kmsProviderId = new KeyProviderId();
            encryptionConfig.kmsProviderId.setId(this.kmipClusterId);
         }

         encryptionConfig.eraseDisksBeforeUse = this.eraseDisksBeforeUse;
      }

      return encryptionConfig;
   }

   private DataEfficiencyConfig getDataEfficiencySpec() {
      DataEfficiencyConfig dedupConfig = new DataEfficiencyConfig();
      dedupConfig.dedupEnabled = this.enabledDedup;
      dedupConfig.compressionEnabled = dedupConfig.dedupEnabled;
      return dedupConfig;
   }

   private VsanDiskMappingsConfigSpec getDiskMappingsConfigSpec() {
      VsanDiskMappingsConfigSpec diskMappingsSpec = null;
      if (!this.autoClaimDisks && !CollectionUtils.isEmpty(this.diskMappings)) {
         diskMappingsSpec = new VsanDiskMappingsConfigSpec();
         diskMappingsSpec.hostDiskMappings = this.getHostsDisksMappings(this.diskMappings);
      }

      return diskMappingsSpec;
   }

   private VsanFaultDomainsConfigSpec getFdSpec() {
      VsanFaultDomainsConfigSpec fdConfigSpec = null;
      if (this.stretchedClusterConfig != null) {
         fdConfigSpec = new VsanFaultDomainsConfigSpec();
         List<com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec> fdSpecs = new ArrayList();
         fdSpecs.add(this.createFaultDomainSpec(this.stretchedClusterConfig.preferredSiteName, this.stretchedClusterConfig.preferredSiteHosts));
         fdSpecs.add(this.createFaultDomainSpec(this.stretchedClusterConfig.secondarySiteName, this.stretchedClusterConfig.secondarySiteHosts));
         fdConfigSpec.faultDomains = (com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec[])fdSpecs.toArray(new com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec[fdSpecs.size()]);
         fdConfigSpec.witness = this.getVsanWitnessSpec(this.stretchedClusterConfig);
      }

      if (!CollectionUtils.isEmpty(this.faultDomainSpecs)) {
         fdConfigSpec = new VsanFaultDomainsConfigSpec();
         Map<String, List<ManagedObjectReference>> fdToHostsMap = new HashMap();
         List<com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec> fdSpecs = new ArrayList();

         VsanFaultDomainSpec spec;
         Iterator var5;
         for(var5 = this.faultDomainSpecs.iterator(); var5.hasNext(); ((List)fdToHostsMap.get(spec.faultDomain)).add(spec.hostRef)) {
            spec = (VsanFaultDomainSpec)var5.next();
            if (!fdToHostsMap.containsKey(spec.faultDomain)) {
               fdToHostsMap.put(spec.faultDomain, new ArrayList());
            }
         }

         var5 = fdToHostsMap.keySet().iterator();

         while(var5.hasNext()) {
            String fdName = (String)var5.next();
            List<ManagedObjectReference> hosts = (List)fdToHostsMap.get(fdName);
            fdSpecs.add(this.createFaultDomainSpec(fdName, hosts));
         }

         fdConfigSpec.faultDomains = (com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec[])fdSpecs.toArray(new com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec[fdSpecs.size()]);
      }

      return fdConfigSpec;
   }

   private VsanWitnessSpec getVsanWitnessSpec(VsanStretchedClusterConfig stretchedClusterConfig) {
      VsanWitnessSpec result = new VsanWitnessSpec();
      result.host = stretchedClusterConfig.witnessHost;
      result.preferredFaultDomainName = stretchedClusterConfig.preferredSiteName;
      result.diskMapping = stretchedClusterConfig.witnessHostDiskMapping;
      return result;
   }

   private com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec createFaultDomainSpec(String fdName, List<ManagedObjectReference> hosts) {
      com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec faultDomainSpec = new com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec();
      faultDomainSpec.name = fdName;
      faultDomainSpec.hosts = (ManagedObjectReference[])hosts.toArray(new ManagedObjectReference[hosts.size()]);
      return faultDomainSpec;
   }

   private VsanHostDiskMapping[] getHostsDisksMappings(List<VsanSemiAutoDiskMappingsSpec> semiAutoDiskSpecs) {
      List<VsanHostDiskMapping> result = new ArrayList(semiAutoDiskSpecs.size());
      Iterator var4 = semiAutoDiskSpecs.iterator();

      while(var4.hasNext()) {
         VsanSemiAutoDiskMappingsSpec spec = (VsanSemiAutoDiskMappingsSpec)var4.next();
         result.add(this.getDiskMappingsSpec(spec));
      }

      return (VsanHostDiskMapping[])result.toArray(new VsanHostDiskMapping[result.size()]);
   }

   private VsanHostDiskMapping getDiskMappingsSpec(VsanSemiAutoDiskMappingsSpec semiAutoDiskSpec) {
      List<ScsiDisk> cacheDisksToAdd = new ArrayList();
      List<ScsiDisk> storageDisksToAdd = new ArrayList();
      boolean isAllFlash = false;
      VsanSemiAutoDiskSpec[] var8;
      int var7 = (var8 = semiAutoDiskSpec.disks).length;

      for(int var6 = 0; var6 < var7; ++var6) {
         VsanSemiAutoDiskSpec disk = var8[var6];
         if (ClaimOption.ClaimForCache == disk.claimOption) {
            cacheDisksToAdd.add(disk.disk);
         } else if (ClaimOption.ClaimForStorage == disk.claimOption) {
            storageDisksToAdd.add(disk.disk);
            isAllFlash = disk.markedAsFlash;
         }
      }

      VsanHostDiskMapping createSpec = this.createVsanHostDiskMapping(semiAutoDiskSpec.hostRef, cacheDisksToAdd, storageDisksToAdd, isAllFlash);
      return createSpec;
   }

   private VsanHostDiskMapping createVsanHostDiskMapping(ManagedObjectReference hostRef, List<ScsiDisk> cacheDisksToAdd, List<ScsiDisk> storageDisksToAdd, boolean isAllFlash) {
      VsanHostDiskMapping createSpec = new VsanHostDiskMapping();
      createSpec.host = hostRef;
      createSpec.cacheDisks = (ScsiDisk[])cacheDisksToAdd.toArray(new ScsiDisk[cacheDisksToAdd.size()]);
      createSpec.capacityDisks = (ScsiDisk[])storageDisksToAdd.toArray(new ScsiDisk[storageDisksToAdd.size()]);
      createSpec.type = isAllFlash ? VsanDiskGroupCreationType.allflash.toString() : VsanDiskGroupCreationType.hybrid.toString();
      return createSpec;
   }
}
