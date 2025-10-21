package com.vmware.vsphere.client.vsan.impl;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.encryption.KeyProviderId;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo.HostDefaultInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanDiskMappingsConfigSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanFaultDomainsConfigSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHostDiskMapping;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfsvcConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanWitnessSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHostDiskMapping.VsanDiskGroupCreationType;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.DataEfficiencyConfig;
import com.vmware.vim.vsan.binding.vim.vsan.DataEncryptionConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ProactiveRebalanceInfo;
import com.vmware.vim.vsan.binding.vim.vsan.RdmaConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanExtendedConfig;
import com.vmware.vsan.client.services.capability.VsanCapabilityProvider;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.ClaimOption;
import com.vmware.vsphere.client.vsan.data.VsanConfigSpec;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ConfigureVsanClusterMutationProvider {
   private static final Log _logger = LogFactory.getLog(ConfigureVsanClusterMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(ConfigureVsanClusterMutationProvider.class);
   private static final long DEFAULT_OBJECT_REPAIR_TIMER = 60L;
   private static final boolean DEFAULT_READ_SITE_LOCALITY = false;
   private static final boolean DEFAULT_ENABLE_CUSTOMIZED_SWAP_OBJECT = true;
   @Autowired
   private PermissionService permissionService;
   @Autowired
   private VsanCapabilityProvider capabilityProvider;

   @TsService
   public ManagedObjectReference configure(ManagedObjectReference clusterRef, VsanConfigSpec spec) throws Exception {
      if (!VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         throw new UnsupportedOperationException("This operation is not supported on the current VC instance.");
      } else {
         _logger.info("Invoke configure vsan cluster mutation operation for cluster: " + clusterRef.getValue());
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         ReconfigSpec reconfigSpec = this.getReconfigSpec(clusterRef, spec);
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point point = _profiler.point("vsanConfigSystem.reconfigureEx");

            Throwable var10000;
            label237: {
               label240: {
                  boolean var10001;
                  ManagedObjectReference var21;
                  try {
                     ManagedObjectReference taskRef = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
                     if (taskRef == null) {
                        break label240;
                     }

                     taskRef.setServerGuid(clusterRef.getServerGuid());
                     var21 = taskRef;
                  } catch (Throwable var19) {
                     var10000 = var19;
                     var10001 = false;
                     break label237;
                  }

                  if (point != null) {
                     point.close();
                  }

                  try {
                     return var21;
                  } catch (Throwable var18) {
                     var10000 = var18;
                     var10001 = false;
                     break label237;
                  }
               }

               if (point != null) {
                  point.close();
               }

               return null;
            }

            var5 = var10000;
            if (point != null) {
               point.close();
            }

            throw var5;
         } catch (Throwable var20) {
            if (var5 == null) {
               var5 = var20;
            } else if (var5 != var20) {
               var5.addSuppressed(var20);
            }

            throw var5;
         }
      }
   }

   private ReconfigSpec getReconfigSpec(ManagedObjectReference clusterRef, VsanConfigSpec spec) throws Exception {
      ConfigInfo vsanClusterConfig = this.getVsanConfigInfo(spec);
      DataEfficiencyConfig dedupConfig = null;
      if (VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef)) {
         dedupConfig = this.getDataEfficiencySpec(spec);
      }

      DataEncryptionConfig encryptionConfig = null;
      boolean hasEncryptionPermissions = this.permissionService.hasPermissions(clusterRef, new String[]{"Cryptographer.ManageKeys", "Cryptographer.ManageEncryptionPolicy", "Cryptographer.ManageKeyServers"});
      if (VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef) && hasEncryptionPermissions) {
         encryptionConfig = this.getEncryptionSpec(spec);
      }

      VsanDiskMappingsConfigSpec diskMappingsSpec = this.getDiskMappingsConfigSpec(spec);
      VsanFaultDomainsConfigSpec fdConfigSpec = this.getFdSpec(spec);
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.vsanClusterConfig = vsanClusterConfig;
      reconfigSpec.dataEfficiencyConfig = dedupConfig;
      reconfigSpec.diskMappingSpec = diskMappingsSpec;
      reconfigSpec.dataEncryptionConfig = encryptionConfig;
      reconfigSpec.faultDomainsSpec = fdConfigSpec;
      reconfigSpec.modify = true;
      reconfigSpec.allowReducedRedundancy = spec.allowReducedRedundancy;
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx vsanConfig = vsanConfigSystem.getConfigInfoEx(clusterRef);
      if (vsanConfig != null) {
         if (vsanConfig.perfsvcConfig == null) {
            reconfigSpec.perfsvcConfig = new VsanPerfsvcConfig();
            reconfigSpec.perfsvcConfig.enabled = true;
         } else {
            reconfigSpec.perfsvcConfig = vsanConfig.perfsvcConfig;
         }
      }

      VsanExtendedConfig extendedConfig = new VsanExtendedConfig(60L, false, true, spec.largeScaleClusterSupport, (ProactiveRebalanceInfo)null);
      reconfigSpec.setExtendedConfig(extendedConfig);
      if (this.capabilityProvider.getClusterCapabilityData(clusterRef).isRdmaSupported) {
         reconfigSpec.rdmaConfig = new RdmaConfig();
         reconfigSpec.rdmaConfig.rdmaEnabled = spec.enableRdma;
      }

      return reconfigSpec;
   }

   private ConfigInfo getVsanConfigInfo(VsanConfigSpec configSpec) {
      ConfigInfo vsanClusterConfig = new ConfigInfo();
      vsanClusterConfig.enabled = true;
      vsanClusterConfig.defaultConfig = new HostDefaultInfo();
      vsanClusterConfig.defaultConfig.autoClaimStorage = configSpec.autoClaimDisks;
      return vsanClusterConfig;
   }

   private DataEncryptionConfig getEncryptionSpec(VsanConfigSpec configSpec) {
      DataEncryptionConfig encryptionConfig = new DataEncryptionConfig();
      encryptionConfig.encryptionEnabled = configSpec.enableEncryption;
      if (encryptionConfig.encryptionEnabled) {
         if (!StringUtils.isEmpty(configSpec.kmipClusterId)) {
            encryptionConfig.kmsProviderId = new KeyProviderId();
            encryptionConfig.kmsProviderId.setId(configSpec.kmipClusterId);
         }

         encryptionConfig.eraseDisksBeforeUse = configSpec.eraseDisksBeforeUse;
      }

      return encryptionConfig;
   }

   private DataEfficiencyConfig getDataEfficiencySpec(VsanConfigSpec configSpec) {
      DataEfficiencyConfig dedupConfig = new DataEfficiencyConfig();
      dedupConfig.dedupEnabled = configSpec.enabledDedup;
      dedupConfig.compressionEnabled = dedupConfig.dedupEnabled;
      return dedupConfig;
   }

   private VsanDiskMappingsConfigSpec getDiskMappingsConfigSpec(VsanConfigSpec configSpec) {
      VsanDiskMappingsConfigSpec diskMappingsSpec = null;
      if (!configSpec.autoClaimDisks && !CollectionUtils.isEmpty(configSpec.diskMappings)) {
         diskMappingsSpec = new VsanDiskMappingsConfigSpec();
         diskMappingsSpec.hostDiskMappings = this.getHostsDisksMappings(configSpec.diskMappings);
      }

      return diskMappingsSpec;
   }

   private VsanFaultDomainsConfigSpec getFdSpec(VsanConfigSpec configSpec) {
      VsanFaultDomainsConfigSpec fdConfigSpec = null;
      if (configSpec.stretchedClusterConfig != null) {
         fdConfigSpec = new VsanFaultDomainsConfigSpec();
         List<VsanFaultDomainSpec> fdSpecs = new ArrayList();
         fdSpecs.add(this.createFaultDomainSpec(configSpec.stretchedClusterConfig.preferredSiteName, configSpec.stretchedClusterConfig.preferredSiteHosts));
         fdSpecs.add(this.createFaultDomainSpec(configSpec.stretchedClusterConfig.secondarySiteName, configSpec.stretchedClusterConfig.secondarySiteHosts));
         fdConfigSpec.faultDomains = (VsanFaultDomainSpec[])fdSpecs.toArray(new VsanFaultDomainSpec[fdSpecs.size()]);
         fdConfigSpec.witness = this.getVsanWitnessSpec(configSpec.stretchedClusterConfig);
      }

      if (!CollectionUtils.isEmpty(configSpec.faultDomainSpecs)) {
         fdConfigSpec = new VsanFaultDomainsConfigSpec();
         Map<String, List<ManagedObjectReference>> fdToHostsMap = new HashMap();
         List<VsanFaultDomainSpec> fdSpecs = new ArrayList();

         com.vmware.vsphere.client.vsan.spec.VsanFaultDomainSpec spec;
         Iterator var6;
         for(var6 = configSpec.faultDomainSpecs.iterator(); var6.hasNext(); ((List)fdToHostsMap.get(spec.faultDomain)).add(spec.hostRef)) {
            spec = (com.vmware.vsphere.client.vsan.spec.VsanFaultDomainSpec)var6.next();
            if (!fdToHostsMap.containsKey(spec.faultDomain)) {
               fdToHostsMap.put(spec.faultDomain, new ArrayList());
            }
         }

         var6 = fdToHostsMap.keySet().iterator();

         while(var6.hasNext()) {
            String fdName = (String)var6.next();
            List<ManagedObjectReference> hosts = (List)fdToHostsMap.get(fdName);
            fdSpecs.add(this.createFaultDomainSpec(fdName, hosts));
         }

         fdConfigSpec.faultDomains = (VsanFaultDomainSpec[])fdSpecs.toArray(new VsanFaultDomainSpec[fdSpecs.size()]);
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

   private VsanFaultDomainSpec createFaultDomainSpec(String fdName, List<ManagedObjectReference> hosts) {
      VsanFaultDomainSpec faultDomainSpec = new VsanFaultDomainSpec();
      faultDomainSpec.name = fdName;
      faultDomainSpec.hosts = (ManagedObjectReference[])hosts.toArray(new ManagedObjectReference[hosts.size()]);
      return faultDomainSpec;
   }

   private VsanHostDiskMapping[] getHostsDisksMappings(List<VsanSemiAutoDiskMappingsSpec> semiAutoDiskSpecs) {
      List<VsanHostDiskMapping> result = new ArrayList();
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
