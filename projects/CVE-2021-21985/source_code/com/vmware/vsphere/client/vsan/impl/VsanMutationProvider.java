package com.vmware.vsphere.client.vsan.impl;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.cluster.ConfigSpecEx;
import com.vmware.vim.binding.vim.encryption.KeyProviderId;
import com.vmware.vim.binding.vim.host.MaintenanceSpec;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.vsan.cluster.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.host.DecommissionMode;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.vsan.DataEncryptionConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.ResyncIopsInfo;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMappingCreationSpec;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMappingCreationSpec.DiskMappingCreationType;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.diskGroups.data.RecreateDiskGroupSpec;
import com.vmware.vsan.client.services.encryption.EncryptionPropertyProvider;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.config.ResyncThrottlingSpec;
import com.vmware.vsphere.client.vsan.config.VsanSpec;
import com.vmware.vsphere.client.vsan.data.ClaimOption;
import com.vmware.vsphere.client.vsan.spec.VsanDiskMappingSpec;
import com.vmware.vsphere.client.vsan.spec.VsanFaultDomainSpec;
import com.vmware.vsphere.client.vsan.spec.VsanRemoveDataDiskSpec;
import com.vmware.vsphere.client.vsan.spec.VsanRemoveDiskGroupSpec;
import com.vmware.vsphere.client.vsan.spec.VsanSemiAutoDiskMappingsSpec;
import com.vmware.vsphere.client.vsan.spec.VsanSemiAutoDiskSpec;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanMutationProvider {
   private static final Log logger = LogFactory.getLog(VsanMutationProvider.class);
   private static final VsanProfiler profiler = new VsanProfiler(VsanMutationProvider.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private EncryptionPropertyProvider encryptionPropertyProvider;

   @TsService
   public ManagedObjectReference configure(ManagedObjectReference clusterRef, VsanSpec spec) throws Exception {
      Validate.notNull(clusterRef);
      Validate.notNull(spec);
      ManagedObjectReference configureClusterTask = null;
      if (VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         configureClusterTask = this.startReconfigureTask(clusterRef, spec);
      } else {
         configureClusterTask = this.startLegacyApiReconfigureTask(clusterRef, spec);
      }

      return configureClusterTask;
   }

   private ManagedObjectReference startReconfigureTask(ManagedObjectReference clusterRef, VsanSpec spec) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.setVsanClusterConfig(spec.toVmodlSpec());
      reconfigSpec.setDataEfficiencyConfig(spec.dataEfficiency.toVmodlSpec());
      reconfigSpec.setAllowReducedRedundancy(spec.allowReducedRedundancy);
      boolean hasEncryptioPermissions = this.encryptionPropertyProvider.getEncryptionPermissions(clusterRef);
      DataEncryptionConfig encryptionConfig;
      if (hasEncryptioPermissions) {
         encryptionConfig = new DataEncryptionConfig();
         encryptionConfig.encryptionEnabled = spec.isEncryptionEnabled;
         if (spec.isEncryptionEnabled) {
            if (!StringUtils.isEmpty(spec.kmipClusterId)) {
               encryptionConfig.kmsProviderId = new KeyProviderId();
               encryptionConfig.kmsProviderId.setId(spec.kmipClusterId);
            }

            encryptionConfig.eraseDisksBeforeUse = spec.eraseDisksBeforeUse;
         }

         reconfigSpec.setDataEncryptionConfig(encryptionConfig);
      }

      reconfigSpec.setModify(true);
      encryptionConfig = null;
      Throwable var7 = null;
      Object var8 = null;

      try {
         VsanProfiler.Point point = profiler.point("vsanConfigSystem.reconfigureEx");

         ManagedObjectReference configureClusterTask;
         try {
            configureClusterTask = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
            VmodlHelper.assignServerGuid(configureClusterTask, clusterRef.getServerGuid());
         } finally {
            if (point != null) {
               point.close();
            }

         }

         return configureClusterTask;
      } catch (Throwable var15) {
         if (var7 == null) {
            var7 = var15;
         } else if (var7 != var15) {
            var7.addSuppressed(var15);
         }

         throw var7;
      }
   }

   private ManagedObjectReference startLegacyApiReconfigureTask(ManagedObjectReference clusterRef, VsanSpec spec) {
      ConfigSpecEx clusterSpecEx = new ConfigSpecEx();
      clusterSpecEx.vsanConfig = new ConfigInfo();
      clusterSpecEx.vsanConfig.setEnabled(spec.isEnabled);
      clusterSpecEx.vsanConfig.setDefaultConfig(spec.toVmodlSpec().getDefaultConfig());
      Throwable var4 = null;
      Object var5 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

         Throwable var10000;
         label450: {
            ManagedObjectReference var42;
            boolean var10001;
            try {
               ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
               ManagedObjectReference configureClusterTask = null;
               Throwable var9 = null;
               Object var10 = null;

               try {
                  VsanProfiler.Point point = profiler.point("cluster.reconfigureEx");

                  try {
                     configureClusterTask = cluster.reconfigureEx(clusterSpecEx, true);
                  } finally {
                     if (point != null) {
                        point.close();
                     }

                  }
               } catch (Throwable var38) {
                  if (var9 == null) {
                     var9 = var38;
                  } else if (var9 != var38) {
                     var9.addSuppressed(var38);
                  }

                  throw var9;
               }

               var42 = configureClusterTask;
            } catch (Throwable var40) {
               var10000 = var40;
               var10001 = false;
               break label450;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label438:
            try {
               return var42;
            } catch (Throwable var39) {
               var10000 = var39;
               var10001 = false;
               break label438;
            }
         }

         var4 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var4;
      } catch (Throwable var41) {
         if (var4 == null) {
            var4 = var41;
         } else if (var4 != var41) {
            var4.addSuppressed(var41);
         }

         throw var4;
      }
   }

   @TsService
   public ManagedObjectReference turnOffVsan(ManagedObjectReference clusterRef) throws Exception {
      Validate.notNull(clusterRef);
      ManagedObjectReference configureClusterTask = null;
      ConfigInfo configInfo = new ConfigInfo();
      configInfo.enabled = false;
      Throwable var6;
      VcConnection vcConnection;
      if (VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         ReconfigSpec reconfigSpec = new ReconfigSpec();
         reconfigSpec.vsanClusterConfig = configInfo;
         reconfigSpec.modify = true;
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         var6 = null;
         vcConnection = null;

         try {
            VsanProfiler.Point point = profiler.point("vsanConfigSystem.reconfigureEx");

            try {
               configureClusterTask = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var49) {
            if (var6 == null) {
               var6 = var49;
            } else if (var6 != var49) {
               var6.addSuppressed(var49);
            }

            throw var6;
         }
      } else {
         ConfigSpecEx clusterSpecEx = new ConfigSpecEx();
         clusterSpecEx.vsanConfig = configInfo;
         Throwable var55 = null;
         var6 = null;

         try {
            vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

            try {
               ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
               Throwable var9 = null;
               Object var10 = null;

               try {
                  VsanProfiler.Point point = profiler.point("cluster.reconfigureEx");

                  try {
                     configureClusterTask = cluster.reconfigureEx(clusterSpecEx, true);
                  } finally {
                     if (point != null) {
                        point.close();
                     }

                  }
               } catch (Throwable var51) {
                  if (var9 == null) {
                     var9 = var51;
                  } else if (var9 != var51) {
                     var9.addSuppressed(var51);
                  }

                  throw var9;
               }
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }
         } catch (Throwable var53) {
            if (var55 == null) {
               var55 = var53;
            } else if (var55 != var53) {
               var55.addSuppressed(var53);
            }

            throw var55;
         }
      }

      VmodlHelper.assignServerGuid(configureClusterTask, clusterRef.getServerGuid());
      return configureClusterTask;
   }

   @TsService
   public ManagedObjectReference claimDisks(ManagedObjectReference hostRef, VsanDiskMappingSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         Throwable var10000;
         label988: {
            ManagedObjectReference var77;
            boolean var10001;
            try {
               VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, vcConnection);
               DiskMapping[] diskMappings = (DiskMapping[])Arrays.copyOf(spec.mappings, spec.mappings.length, DiskMapping[].class);
               VsanVcDiskManagementSystem diskManagement = VsanProviderUtils.getVcDiskManagementSystem(spec.clusterRef);
               ManagedObjectReference initializeDisksTask = null;
               DiskMapping[] var13 = diskMappings;
               int var12 = diskMappings.length;
               int var11 = 0;

               while(true) {
                  if (var11 >= var12) {
                     if (initializeDisksTask != null && initializeDisksTask.getServerGuid() == null) {
                        initializeDisksTask.setServerGuid(spec.clusterRef.getServerGuid());
                     }

                     var77 = initializeDisksTask;
                     break;
                  }

                  DiskMapping mapping = var13[var11];
                  if (ArrayUtils.isEmpty(mapping.nonSsd)) {
                     logger.error("No capacity disks selected!");
                     throw new IllegalArgumentException("No capacity disks selected!");
                  }

                  List<ScsiDisk> cache = Arrays.asList(mapping.ssd);
                  List<ScsiDisk> capacity = Arrays.asList(mapping.nonSsd);
                  boolean isAllFlashGroup = mapping.nonSsd[0].ssd;
                  DiskMappingCreationSpec createSpec = this.createDiskMappingCreationSpec(hostRef, cache, capacity, isAllFlashGroup);
                  Throwable var18;
                  Object var19;
                  VsanProfiler.Point point;
                  if (spec.isAllFlashSupported) {
                     var18 = null;
                     var19 = null;

                     try {
                        point = profiler.point("diskManagement.initializeDiskMappings");

                        try {
                           initializeDisksTask = diskManagement.initializeDiskMappings(createSpec);
                        } finally {
                           if (point != null) {
                              point.close();
                           }

                        }
                     } catch (Throwable var73) {
                        if (var18 == null) {
                           var18 = var73;
                        } else if (var18 != var73) {
                           var18.addSuppressed(var73);
                        }

                        throw var18;
                     }
                  } else {
                     var18 = null;
                     var19 = null;

                     try {
                        point = profiler.point("vsanSystem.initializeDisks");

                        try {
                           initializeDisksTask = vsanSystem.initializeDisks(diskMappings);
                        } finally {
                           if (point != null) {
                              point.close();
                           }

                        }
                     } catch (Throwable var71) {
                        if (var18 == null) {
                           var18 = var71;
                        } else if (var18 != var71) {
                           var18.addSuppressed(var71);
                        }

                        throw var18;
                     }
                  }

                  ++var11;
               }
            } catch (Throwable var75) {
               var10000 = var75;
               var10001 = false;
               break label988;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label976:
            try {
               return var77;
            } catch (Throwable var74) {
               var10000 = var74;
               var10001 = false;
               break label976;
            }
         }

         var3 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var3;
      } catch (Throwable var76) {
         if (var3 == null) {
            var3 = var76;
         } else if (var3 != var76) {
            var3.addSuppressed(var76);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference autoClaimDisks(ManagedObjectReference hostRef, VsanSemiAutoDiskMappingsSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         Throwable var10000;
         label1014: {
            ManagedObjectReference var75;
            boolean var10001;
            try {
               VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, vcConnection);
               VsanVcDiskManagementSystem diskManagement = VsanProviderUtils.getVcDiskManagementSystem(spec.hostRef);
               List<ScsiDisk> cacheDisksToAdd = new ArrayList();
               List<ScsiDisk> storageDisksToAdd = new ArrayList();
               boolean isAllFlash = false;
               VsanSemiAutoDiskSpec[] var14;
               int var13 = (var14 = spec.disks).length;

               for(int var12 = 0; var12 < var13; ++var12) {
                  VsanSemiAutoDiskSpec disk = var14[var12];
                  if (ClaimOption.ClaimForCache == disk.claimOption) {
                     cacheDisksToAdd.add(disk.disk);
                  } else if (ClaimOption.ClaimForStorage == disk.claimOption) {
                     storageDisksToAdd.add(disk.disk);
                     isAllFlash = disk.markedAsFlash;
                  }
               }

               DiskMappingCreationSpec createSpec = this.createDiskMappingCreationSpec(hostRef, cacheDisksToAdd, storageDisksToAdd, isAllFlash);
               ManagedObjectReference claimDisksTask = null;
               Throwable var74;
               VsanProfiler.Point point;
               if (spec.isAllFlashSupported) {
                  var74 = null;
                  var14 = null;

                  try {
                     point = profiler.point("diskManagement.initializeDiskMappings");

                     try {
                        claimDisksTask = diskManagement.initializeDiskMappings(createSpec);
                     } finally {
                        if (point != null) {
                           point.close();
                        }

                     }
                  } catch (Throwable var68) {
                     if (var74 == null) {
                        var74 = var68;
                     } else if (var74 != var68) {
                        var74.addSuppressed(var68);
                     }

                     throw var74;
                  }
               } else {
                  cacheDisksToAdd.addAll(storageDisksToAdd);
                  var74 = null;
                  var14 = null;

                  try {
                     point = profiler.point("vsanSystem.addDisks");

                     try {
                        claimDisksTask = vsanSystem.addDisks((ScsiDisk[])cacheDisksToAdd.toArray(new ScsiDisk[cacheDisksToAdd.size()]));
                     } finally {
                        if (point != null) {
                           point.close();
                        }

                     }
                  } catch (Throwable var66) {
                     if (var74 == null) {
                        var74 = var66;
                     } else if (var74 != var66) {
                        var74.addSuppressed(var66);
                     }

                     throw var74;
                  }
               }

               if (claimDisksTask != null && claimDisksTask.getServerGuid() == null) {
                  claimDisksTask.setServerGuid(spec.hostRef.getServerGuid());
               }

               var75 = claimDisksTask;
            } catch (Throwable var70) {
               var10000 = var70;
               var10001 = false;
               break label1014;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label1001:
            try {
               return var75;
            } catch (Throwable var69) {
               var10000 = var69;
               var10001 = false;
               break label1001;
            }
         }

         var3 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var3;
      } catch (Throwable var71) {
         if (var3 == null) {
            var3 = var71;
         } else if (var3 != var71) {
            var3.addSuppressed(var71);
         }

         throw var3;
      }
   }

   private DiskMappingCreationSpec createDiskMappingCreationSpec(ManagedObjectReference hostRef, List<ScsiDisk> cacheDisksToAdd, List<ScsiDisk> storageDisksToAdd, boolean isAllFlash) {
      DiskMappingCreationSpec createSpec = new DiskMappingCreationSpec();
      createSpec.host = hostRef;
      createSpec.cacheDisks = (ScsiDisk[])cacheDisksToAdd.toArray(new ScsiDisk[cacheDisksToAdd.size()]);
      createSpec.capacityDisks = (ScsiDisk[])storageDisksToAdd.toArray(new ScsiDisk[storageDisksToAdd.size()]);
      createSpec.creationType = isAllFlash ? DiskMappingCreationType.allFlash.toString() : DiskMappingCreationType.hybrid.toString();
      return createSpec;
   }

   @TsService
   public ManagedObjectReference removeDisk(ManagedObjectReference param1, VsanRemoveDataDiskSpec param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference removeDiskGroup(ManagedObjectReference param1, VsanRemoveDiskGroupSpec param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference updateFaultDomainInfo(ManagedObjectReference param1, VsanFaultDomainSpec param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference resyncThrottling(ManagedObjectReference clusterRef, ResyncThrottlingSpec spec) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.resyncIopsLimitConfig = new ResyncIopsInfo();
      reconfigSpec.resyncIopsLimitConfig.setResyncIops(spec.iopsLimit);
      reconfigSpec.setModify(true);
      Throwable var5 = null;
      Object var6 = null;

      try {
         VsanProfiler.Point point = profiler.point("vsanConfigSystem.reconfigureEx");

         Throwable var10000;
         label205: {
            boolean var10001;
            ManagedObjectReference var21;
            try {
               ManagedObjectReference configureClusterTask = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
               if (configureClusterTask != null && configureClusterTask.getServerGuid() == null) {
                  configureClusterTask.setServerGuid(clusterRef.getServerGuid());
               }

               var21 = configureClusterTask;
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label205;
            }

            if (point != null) {
               point.close();
            }

            label194:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label194;
            }
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

   @TsService
   public ManagedObjectReference recreateDiskGroup(ManagedObjectReference hostRef, RecreateDiskGroupSpec spec) throws Exception {
      VsanVcDiskManagementSystem diskManagement = VsanProviderUtils.getVcDiskManagementSystem(hostRef);
      MaintenanceSpec maintenanceSpec = new MaintenanceSpec();
      DecommissionMode mode = new DecommissionMode(spec.decommissionMode.toString());
      maintenanceSpec.setVsanMode(mode);
      ManagedObjectReference initializeDisksTask = null;
      Throwable var7 = null;
      Object var8 = null;

      try {
         VsanProfiler.Point point = profiler.point("diskManagement.rebuildDiskMapping");

         try {
            initializeDisksTask = diskManagement.rebuildDiskMapping(hostRef, spec.mapping.toVmodl(), maintenanceSpec);
         } finally {
            if (point != null) {
               point.close();
            }

         }

         return initializeDisksTask;
      } catch (Throwable var15) {
         if (var7 == null) {
            var7 = var15;
         } else if (var7 != var15) {
            var7.addSuppressed(var15);
         }

         throw var7;
      }
   }
}
