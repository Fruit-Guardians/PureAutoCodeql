package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.HostSystem.PowerState;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.host.VirtualNic;
import com.vmware.vim.binding.vim.host.VirtualNicManagerInfo;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.host.VirtualNicManager.NetConfig;
import com.vmware.vim.binding.vim.host.VirtualNicManager.NicType;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo.StorageInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VSANStretchedClusterFaultDomainConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vim.vsan.binding.vim.vsan.host.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.host.EncryptionInfo;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.VsanSemiAutoClaimDisksData;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanStretchedClusterMutationProvider {
   private static final String VNIC_PREFIX = "vim.host.VirtualNic-";
   private static final String VIRTUAL_NIC_MANAGER_INFO_PROPERTY = "config.virtualNicManagerInfo";
   private static final String VIRTUAL_NIC_PROPERTY = "config.network.vnic";
   private static final String PARENT_PROPERTY = "parent";
   private static final String CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   private static final String POWER_STATE_PROPERTY = "runtime.powerState";
   private static final String MAINTENANCE_MODE_PROPERTY = "runtime.inMaintenanceMode";
   private static final String DISABLED_METHODS_PROPERTY = "disabledMethod";
   private static final String VSAN_SEMI_AUTO_DISKS_PROPERTY_NAME = "vsanSemiAutoClaimDisksData";
   private static final String ENTER_MAINTENANCE_MODE_DISABLED_METHOD = "EnterMaintenanceMode_Task";
   private static final String EXIT_MAINTENANCE_MODE_DISABLED_METHOD = "ExitMaintenanceMode_Task";
   private static final Log _logger = LogFactory.getLog(VsanStretchedClusterMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanStretchedClusterMutationProvider.class);
   @Autowired
   private VcClient vcClient;

   @TsService
   public WitnessHostValidationResult validateWitnessHost(ManagedObjectReference clusterRef, WitnessHostSpec spec) throws Exception {
      return this.getWitnessValidationResult(clusterRef, spec.witnessHost);
   }

   @TsService
   public ManagedObjectReference configureStretchedCluster(ManagedObjectReference clusterRef, VsanStretchedClusterConfig spec) throws Exception {
      _logger.info("Invoke configure stretched cluster mutation operation for cluster: " + clusterRef.getServerGuid());
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      _logger.info("Configuring stretched cluster with witness `" + spec.witnessHost.getServerGuid() + "` and preffered fault domain `" + spec.preferredSiteName + "`");
      ManagedObjectReference stretchedClusterTask = null;
      DiskMapping diskMapping = null;
      if (spec.witnessHostDiskMapping != null) {
         diskMapping = this.copyProperties(spec.witnessHostDiskMapping);
      }

      Throwable var7;
      VsanProfiler.Point point;
      if (spec.isFaultDomainConfigurationChanged) {
         VSANStretchedClusterFaultDomainConfig fdConfig = new VSANStretchedClusterFaultDomainConfig();
         fdConfig.firstFdName = spec.preferredSiteName;
         fdConfig.firstFdHosts = (ManagedObjectReference[])spec.preferredSiteHosts.toArray(new ManagedObjectReference[spec.preferredSiteHosts.size()]);
         fdConfig.secondFdName = spec.secondarySiteName;
         fdConfig.secondFdHosts = (ManagedObjectReference[])spec.secondarySiteHosts.toArray(new ManagedObjectReference[spec.secondarySiteHosts.size()]);
         var7 = null;
         point = null;

         try {
            VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.convertToStretchedCluster");

            try {
               stretchedClusterTask = stretchedClusterSystem.convertToStretchedCluster(clusterRef, fdConfig, spec.witnessHost, spec.preferredSiteName, diskMapping);
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var29) {
            if (var7 == null) {
               var7 = var29;
            } else if (var7 != var29) {
               var7.addSuppressed(var29);
            }

            throw var7;
         }
      } else {
         Throwable var30 = null;
         var7 = null;

         try {
            point = _profiler.point("stretchedClusterSystem.addWitnessHost");

            try {
               stretchedClusterSystem.addWitnessHost(clusterRef, spec.witnessHost, spec.preferredSiteName, diskMapping, (Future)null);
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var27) {
            if (var30 == null) {
               var30 = var27;
            } else if (var30 != var27) {
               var30.addSuppressed(var27);
            }

            throw var30;
         }
      }

      stretchedClusterTask.setServerGuid(clusterRef.getServerGuid());
      return stretchedClusterTask;
   }

   @TsService
   public ManagedObjectReference setPreferredFaultDomain(ManagedObjectReference clusterRef, PreferredFaultDomainData preferredFaultDomainData) throws Exception {
      _logger.info("Invoke set preferred fault domain mutation operation for cluster: " + clusterRef.getServerGuid());
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.setPreferredFaultDomain");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
               _logger.info("Setting preferred fault domain to: " + preferredFaultDomainData.preferredFaultDomainName);
               ManagedObjectReference setPreferredFdTask = stretchedClusterSystem.setPreferredFaultDomain(clusterRef, preferredFaultDomainData.preferredFaultDomainName, (ManagedObjectReference)null);
               setPreferredFdTask.setServerGuid(clusterRef.getServerGuid());
               var20 = setPreferredFdTask;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
            }

            label162:
            try {
               return var20;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (point != null) {
            point.close();
         }

         throw var3;
      } catch (Throwable var19) {
         if (var3 == null) {
            var3 = var19;
         } else if (var3 != var19) {
            var3.addSuppressed(var19);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference setWitnessHost(ManagedObjectReference clusterRef, VsanWitnessConfig witnessConfig) throws Exception {
      _logger.info("Invoke change witness host mutation operation for cluster: " + clusterRef);
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.addWitnessHost");

         Throwable var10000;
         label173: {
            boolean var10001;
            try {
               _logger.info("Changing witness host: " + witnessConfig.host);
               stretchedClusterSystem.addWitnessHost(clusterRef, witnessConfig.host, witnessConfig.preferredFaultDomain, witnessConfig.diskMapping, (Future)null);
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
            }

            label162:
            try {
               return null;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label162;
            }
         }

         var4 = var10000;
         if (point != null) {
            point.close();
         }

         throw var4;
      } catch (Throwable var18) {
         if (var4 == null) {
            var4 = var18;
         } else if (var4 != var18) {
            var4.addSuppressed(var18);
         }

         throw var4;
      }
   }

   @TsService
   public ManagedObjectReference removeWitnessHost(ManagedObjectReference clusterRef, WitnessHostSpec spec) throws Exception {
      _logger.info("Invoke remove witness host mutation operation for cluster: " + clusterRef.getServerGuid());
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.removeWitnessHost");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               _logger.info("Removing witness host: " + spec.witnessHost.getServerGuid());
               ManagedObjectReference removeWitnessTask = stretchedClusterSystem.removeWitnessHost(clusterRef, spec.witnessHost, (String)null);
               removeWitnessTask.setServerGuid(clusterRef.getServerGuid());
               var20 = removeWitnessTask;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
            }

            label162:
            try {
               return var20;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label162;
            }
         }

         var4 = var10000;
         if (point != null) {
            point.close();
         }

         throw var4;
      } catch (Throwable var19) {
         if (var4 == null) {
            var4 = var19;
         } else if (var4 != var19) {
            var4.addSuppressed(var19);
         }

         throw var4;
      }
   }

   public boolean reconcileUnicastAgents(ManagedObjectReference clusterRef, ResyncUnicastAgentSpec spec) throws Exception {
      _logger.info("Invoke resync unicast agents mutation operation for cluster: " + clusterRef.getServerGuid());
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.reconcileUnicastAgents");

         Throwable var10000;
         label173: {
            boolean var10001;
            boolean var19;
            try {
               VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
               _logger.info("Resyncing unicast agents for: " + clusterRef.getServerGuid());
               var19 = stretchedClusterSystem.reconcileUnicastAgents(clusterRef);
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
            }

            label162:
            try {
               return var19;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (point != null) {
            point.close();
         }

         throw var3;
      } catch (Throwable var18) {
         if (var3 == null) {
            var3 = var18;
         } else if (var3 != var18) {
            var3.addSuppressed(var18);
         }

         throw var3;
      }
   }

   private WitnessHostValidationResult getWitnessValidationResult(ManagedObjectReference clusterRef, ManagedObjectReference hostRef) throws Exception {
      if (hostRef == null) {
         return null;
      } else {
         PropertyValue[] propValue = QueryUtil.getProperties(hostRef, new String[]{"config.virtualNicManagerInfo", "config.network.vnic", "parent", "runtime.connectionState", "runtime.powerState", "runtime.inMaintenanceMode", "disabledMethod", "vsanSemiAutoClaimDisksData", "configManager.vsanSystem"}).getPropertyValues();
         VirtualNicManagerInfo nicManagerInfo = null;
         VirtualNic[] vnic = null;
         ManagedObjectReference parent = null;
         boolean isConnected = false;
         boolean isPoweredOn = false;
         boolean isInMaintenanceMode = false;
         boolean canClaimHybridGroup = false;
         boolean hasDiskGroups = false;
         boolean isVsanEnabled = false;
         long claimedCapacity = 0L;
         PropertyValue[] var18 = propValue;
         int var17 = propValue.length;

         for(int var16 = 0; var16 < var17; ++var16) {
            PropertyValue prop = var18[var16];
            String var19;
            switch((var19 = prop.propertyName).hashCode()) {
            case -1890534126:
               if (var19.equals("config.virtualNicManagerInfo")) {
                  nicManagerInfo = (VirtualNicManagerInfo)prop.value;
               }
               break;
            case -995424086:
               if (var19.equals("parent")) {
                  parent = (ManagedObjectReference)prop.value;
               }
               break;
            case -533996354:
               if (var19.equals("config.network.vnic")) {
                  vnic = (VirtualNic[])prop.value;
               }
               break;
            case -453313988:
               if (var19.equals("configManager.vsanSystem")) {
                  Throwable var26 = null;
                  Object var27 = null;

                  try {
                     VcConnection conn = this.vcClient.getConnection(hostRef.getServerGuid());

                     try {
                        ManagedObjectReference vsanSystemRef = (ManagedObjectReference)prop.value;
                        VsanSystem vsanSystem = (VsanSystem)conn.createStub(VsanSystem.class, vsanSystemRef);
                        Throwable var31 = null;
                        Object var32 = null;

                        try {
                           VsanProfiler.Point p = _profiler.point("vsanSystem.getConfig");

                           try {
                              isVsanEnabled = vsanSystem.getConfig().enabled;
                           } finally {
                              if (p != null) {
                                 p.close();
                              }

                           }
                        } catch (Throwable var77) {
                           if (var31 == null) {
                              var31 = var77;
                           } else if (var31 != var77) {
                              var31.addSuppressed(var77);
                           }

                           throw var31;
                        }
                     } finally {
                        if (conn != null) {
                           conn.close();
                        }

                     }
                  } catch (Throwable var79) {
                     if (var26 == null) {
                        var26 = var79;
                     } else if (var26 != var79) {
                        var26.addSuppressed(var79);
                     }

                     throw var26;
                  }
               }
               break;
            case 541171298:
               if (var19.equals("runtime.powerState")) {
                  isPoweredOn = prop.value.equals(PowerState.poweredOn);
               }
               break;
            case 767699581:
               if (var19.equals("disabledMethod")) {
                  List<String> disabledMethods = Arrays.asList((String[])prop.value);
                  isInMaintenanceMode |= disabledMethods.containsAll(Arrays.asList("EnterMaintenanceMode_Task", "ExitMaintenanceMode_Task"));
               }
               break;
            case 898574043:
               if (var19.equals("runtime.inMaintenanceMode")) {
                  isInMaintenanceMode |= (Boolean)prop.value;
               }
               break;
            case 1457701451:
               if (!var19.equals("vsanSemiAutoClaimDisksData")) {
                  break;
               }

               VsanSemiAutoClaimDisksData eligibleDisks = (VsanSemiAutoClaimDisksData)prop.value;
               boolean canClaimCacheDisk = false;
               boolean canClaimDataDisk = false;
               boolean allFlashDiskGroupExist = false;
               boolean hybridDiskGroupExist = false;
               if (eligibleDisks != null) {
                  canClaimCacheDisk = eligibleDisks.numNotInUseSsdDisks > 0;
                  canClaimDataDisk = eligibleDisks.numNotInUseDataDisks > 0;
                  allFlashDiskGroupExist = eligibleDisks.allFlashDiskGroupExist;
                  hybridDiskGroupExist = eligibleDisks.hybridDiskGroupExist;
                  claimedCapacity = eligibleDisks.claimedCapacity;
               }

               canClaimHybridGroup = canClaimCacheDisk && canClaimDataDisk;
               hasDiskGroups = allFlashDiskGroupExist || hybridDiskGroupExist;
               break;
            case 2004020797:
               if (var19.equals("runtime.connectionState")) {
                  isConnected = prop.value.equals(ConnectionState.connected);
               }
            }
         }

         WitnessHostValidationResult result = new WitnessHostValidationResult();
         result.witnessHostRef = hostRef;
         result.isHostInTheSameCluster = parent.equals(clusterRef);
         if (!result.isHostInTheSameCluster && parent.getType().equals(ClusterComputeResource.class.getSimpleName())) {
            result.isHostInVsanEnabledCluster = (Boolean)QueryUtil.getProperty(parent, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled", (Object)null);
         }

         if (!result.isHostInTheSameCluster && !result.isHostInVsanEnabledCluster && vnic != null) {
            VirtualNic[] var88 = vnic;
            int var87 = vnic.length;

            for(var17 = 0; var17 < var87; ++var17) {
               VirtualNic virtualNic = var88[var17];
               if (this.isVsanEnabled(virtualNic.device, nicManagerInfo)) {
                  result.hasVsanEnabledNic = true;
                  break;
               }
            }
         }

         boolean isEncrypted = false;

         try {
            Throwable var86 = null;
            var18 = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanSystem.getConfig");

               try {
                  VsanSystem vsanSystem = VsanProviderUtils.getVsanSystem(hostRef);
                  ConfigInfo config = vsanSystem.getConfig();
                  if (config != null && config instanceof ConfigInfoEx) {
                     EncryptionInfo encryptionInfo = ((ConfigInfoEx)config).encryptionInfo;
                     if (encryptionInfo != null) {
                        isEncrypted = encryptionInfo.enabled;
                     }
                  } else {
                     _logger.error("ConfigInfo is null or is not an instance of ConfigInfoEx");
                  }
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var81) {
               if (var86 == null) {
                  var86 = var81;
               } else if (var86 != var81) {
                  var86.addSuppressed(var81);
               }

               throw var86;
            }
         } catch (Exception var82) {
            _logger.error("Cannot retrieve host's configuration from VsanSystem living in vSAN Health service.", var82);
         }

         result.isHostConnected = isConnected;
         result.isPoweredOn = isPoweredOn;
         result.isHostInMaintenanceMode = isInMaintenanceMode;
         result.isStretchedClusterSupported = VsanCapabilityUtils.isStretchedClusterSupportedOnHost(hostRef);
         result.canClaimHybridGroup = canClaimHybridGroup;
         result.hasDiskGroups = hasDiskGroups;
         result.claimedCapacity = claimedCapacity;
         result.isExternalWitness = isVsanEnabled && !result.isHostInVsanEnabledCluster;
         result.isEncrypted = isEncrypted;
         result.autoClaimMode = (Boolean)QueryUtil.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.autoClaimStorage", (Object)null);
         return result;
      }
   }

   private boolean isVsanEnabled(String vnicDevice, VirtualNicManagerInfo nicInfo) {
      if (vnicDevice != null && nicInfo != null && nicInfo.netConfig != null) {
         NetConfig[] var6;
         int var5 = (var6 = nicInfo.netConfig).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            NetConfig netConfig = var6[var4];
            if ((NicType.vsan.toString().equals(netConfig.nicType) || NicType.vsanWitness.toString().equals(netConfig.nicType)) && netConfig.selectedVnic != null) {
               String[] var10;
               int var9 = (var10 = netConfig.selectedVnic).length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  String selectedVnic = var10[var8];
                  if (vnicDevice.equals(this.getVnicDeviceName(selectedVnic))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private String getVnicDeviceName(String deviceKey) {
      if (deviceKey == null) {
         return deviceKey;
      } else {
         int vnicPrefix = deviceKey.indexOf("vim.host.VirtualNic-");
         return vnicPrefix < 0 ? deviceKey : deviceKey.substring(vnicPrefix + "vim.host.VirtualNic-".length());
      }
   }

   private DiskMapping copyProperties(DiskMapping original) {
      DiskMapping result = new DiskMapping();
      result.ssd = original.ssd;
      result.nonSsd = new ScsiDisk[original.nonSsd.length];

      for(int i = 0; i < result.nonSsd.length; ++i) {
         result.nonSsd[i] = original.nonSsd[i];
      }

      return result;
   }

   @TsService
   public void updateDiskClaimingMode(ManagedObjectReference clusterRef, DiskClaimingModeSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection conn = this.vcClient.getConnection(clusterRef.getServerGuid());

         try {
            List<VsanSystem> vsanSystems = this.getVsanSystems(clusterRef, conn);
            Iterator var8 = vsanSystems.iterator();

            while(var8.hasNext()) {
               VsanSystem vsanSystem = (VsanSystem)var8.next();
               ConfigInfo vsanConfig = new ConfigInfo();
               vsanConfig.storageInfo = new StorageInfo();
               vsanConfig.storageInfo.autoClaimStorage = spec.isAutoClaimMode;
               Throwable var10 = null;
               Object var11 = null;

               try {
                  VsanProfiler.Point p = _profiler.point("vsanSystem.update");

                  try {
                     vsanSystem.update(vsanConfig);
                  } finally {
                     if (p != null) {
                        p.close();
                     }

                  }
               } catch (Throwable var30) {
                  if (var10 == null) {
                     var10 = var30;
                  } else if (var10 != var30) {
                     var10.addSuppressed(var30);
                  }

                  throw var10;
               }
            }
         } finally {
            if (conn != null) {
               conn.close();
            }

         }

      } catch (Throwable var32) {
         if (var3 == null) {
            var3 = var32;
         } else if (var3 != var32) {
            var3.addSuppressed(var32);
         }

         throw var3;
      }
   }

   @TsService
   public void removeHost(ManagedObjectReference clusterRef, LeaveVsanClusterSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection conn = this.vcClient.getConnection(clusterRef.getServerGuid());

         try {
            List<VsanSystem> vsanSystems = this.getVsanSystems(clusterRef, conn);
            Iterator var8 = vsanSystems.iterator();

            while(var8.hasNext()) {
               VsanSystem vsanSystem = (VsanSystem)var8.next();
               ConfigInfo vsanConfig = new ConfigInfo();
               vsanConfig.enabled = false;
               Throwable var10 = null;
               Object var11 = null;

               try {
                  VsanProfiler.Point p = _profiler.point("vsanSystem.update");

                  try {
                     vsanSystem.update(vsanConfig);
                  } finally {
                     if (p != null) {
                        p.close();
                     }

                  }
               } catch (Throwable var30) {
                  if (var10 == null) {
                     var10 = var30;
                  } else if (var10 != var30) {
                     var10.addSuppressed(var30);
                  }

                  throw var10;
               }
            }
         } finally {
            if (conn != null) {
               conn.close();
            }

         }

      } catch (Throwable var32) {
         if (var3 == null) {
            var3 = var32;
         } else if (var3 != var32) {
            var3.addSuppressed(var32);
         }

         throw var3;
      }
   }

   private List<VsanSystem> getVsanSystems(ManagedObjectReference clusterRef, VcConnection conn) {
      ArrayList vsanSystems = new ArrayList();

      try {
         PropertyValue[] propValues = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "witnessHost", ClusterComputeResource.class.getSimpleName(), new String[]{"configManager.vsanSystem"}).getPropertyValues();
         PropertyValue[] var8 = propValues;
         int var7 = propValues.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            PropertyValue propValue = var8[var6];
            ManagedObjectReference vsanSystemRef = (ManagedObjectReference)propValue.value;
            VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(vsanSystemRef, conn);
            vsanSystems.add(vsanSystem);
         }
      } catch (Exception var11) {
         _logger.error("Could not retrieve witness hosts and their vsan system: ", var11);
      }

      return vsanSystems;
   }
}
