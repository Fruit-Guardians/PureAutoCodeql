package com.vmware.vsan.client.services.capacity;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.DefinedProfileSpec;
import com.vmware.vim.binding.vim.vm.ProfileSpec;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanDataProtectionSpaceUsageStats;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSpaceSummary;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceReportSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceUsage;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceUsageDetailResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanWhatifCapacity;
import com.vmware.vim.vsan.binding.vim.vsan.DataEfficiencyCapacityState;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.capacity.model.CapacityData;
import com.vmware.vsan.client.services.capacity.model.DedupCapacityData;
import com.vmware.vsan.client.services.capacity.model.SystemUsageCapacityData;
import com.vmware.vsan.client.services.capacity.model.UserObjectsCapacityData;
import com.vmware.vsan.client.services.capacity.model.VmCapacityData;
import com.vmware.vsan.client.services.capacity.model.WhatifCapacityData;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CapacityDataService {
   @Autowired
   VsanClient vsanClient;
   private static final Log logger = LogFactory.getLog(CapacityDataService.class);
   private static final VsanProfiler _profiler = new VsanProfiler(CapacityDataService.class);

   @TsService
   public CapacityData getSpaceUsage(ManagedObjectReference param1) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public WhatifCapacityData getWhatIfCapacity(ManagedObjectReference objectRef, String profileId) {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      if (!VsanCapabilityUtils.isWhatIfCapacitySupported(clusterRef)) {
         logger.error("Unable to get what-if capacity for cluster that doesn't support it!");
         throw new VsanUiLocalizableException("vsan.common.generic.error");
      } else {
         DefinedProfileSpec profileSpec = new DefinedProfileSpec();
         profileSpec.profileId = profileId;
         ProfileSpec[] profiles = new ProfileSpec[]{profileSpec};
         VsanSpaceReportSystem capacitySystem = VsanProviderUtils.getVsanSpaceReportSystem(clusterRef);
         VsanSpaceUsage spaceUsage = null;

         WhatifCapacityData result;
         try {
            Throwable var8 = null;
            result = null;

            try {
               VsanProfiler.Point p = _profiler.point("capacitySystem.querySpaceUsage");

               try {
                  spaceUsage = capacitySystem.querySpaceUsage(clusterRef, profiles, false);
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var18) {
               if (var8 == null) {
                  var8 = var18;
               } else if (var8 != var18) {
                  var8.addSuppressed(var18);
               }

               throw var8;
            }
         } catch (Exception var19) {
            logger.error("Unable to get what-if capacity: " + var19);
            throw new VsanUiLocalizableException("vsan.common.generic.error");
         }

         if (ArrayUtils.isEmpty(spaceUsage.getWhatifCapacities())) {
            return null;
         } else {
            VsanWhatifCapacity whatifCapacity = spaceUsage.getWhatifCapacities()[0];
            result = new WhatifCapacityData(whatifCapacity.totalWhatifCapacityB, whatifCapacity.freeWhatifCapacityB);
            return result;
         }
      }
   }

   private CapacityData buildCapacityOverview(VsanSpaceUsage spaceUsage, ManagedObjectReference clusterRef) {
      CapacityData result = new CapacityData();
      if (spaceUsage != null && spaceUsage.spaceOverview != null) {
         result.freeSpace = toLong(spaceUsage.freeCapacityB);
         result.totalSpace = spaceUsage.totalCapacityB;
         result.actuallyWrittenSpace = toLong(spaceUsage.totalCapacityB) - toLong(spaceUsage.spaceOverview.overReservedB) - toLong(spaceUsage.freeCapacityB);
         result.usedSpace = toLong(spaceUsage.totalCapacityB) - toLong(spaceUsage.freeCapacityB);
         result.overReservedSpace = toLong(spaceUsage.spaceOverview.overReservedB);
         result.vsanOverheadSpace = toLong(spaceUsage.spaceOverview.overheadB);
         result.dedupCapacity = this.buildDedupCapacityData(spaceUsage, clusterRef);
         return result;
      } else {
         return result;
      }
   }

   private DedupCapacityData buildDedupCapacityData(VsanSpaceUsage spaceUsage, ManagedObjectReference clusterRef) {
      boolean dedupEnabled = false;

      try {
         dedupEnabled = (Boolean)QueryUtil.getProperty(clusterRef, "dataEfficiencyStatus", (Object)null);
      } catch (Exception var62) {
         logger.error("Unable to get vSAN cluster dedup&compression status");
         return null;
      }

      if (!dedupEnabled) {
         return null;
      } else {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanConnection vsanConnection = this.vsanClient.getConnection(clusterRef.getServerGuid());

            Throwable var10000;
            label801: {
               label805: {
                  DataEfficiencyCapacityState dataEfficiencyCapacity;
                  boolean var10001;
                  try {
                     VsanVcDiskManagementSystem mgmtSystem = vsanConnection.getVsanDiskManagementSystem();
                     dataEfficiencyCapacity = null;

                     try {
                        Throwable var9 = null;
                        Object var10 = null;

                        try {
                           VsanProfiler.Point point = _profiler.point("VsanVcDiskManagementSystem.queryClusterDataEfficiencyCapacityState");

                           try {
                              dataEfficiencyCapacity = mgmtSystem.queryClusterDataEfficiencyCapacityState(clusterRef);
                           } finally {
                              if (point != null) {
                                 point.close();
                              }

                           }
                        } catch (Throwable var64) {
                           if (var9 == null) {
                              var9 = var64;
                           } else if (var9 != var64) {
                              var9.addSuppressed(var64);
                           }

                           throw var9;
                        }
                     } catch (Exception var65) {
                        logger.error("Unable to extract vSAN cluster dedup and compression savings data!", var65);
                        throw new VsanUiLocalizableException("vsan.cluster.monitor.capacity.dedup.error");
                     }

                     if (dataEfficiencyCapacity == null || dataEfficiencyCapacity.physicalCapacityUsed == null || dataEfficiencyCapacity.logicalCapacityUsed == null) {
                        break label805;
                     }
                  } catch (Throwable var68) {
                     var10000 = var68;
                     var10001 = false;
                     break label801;
                  }

                  DedupCapacityData var72;
                  try {
                     long usedSpaceAfterDedup = dataEfficiencyCapacity.physicalCapacityUsed;
                     long usedSpaceBeforeDedup = dataEfficiencyCapacity.logicalCapacityUsed;
                     DedupCapacityData dedupData = new DedupCapacityData();
                     if (usedSpaceBeforeDedup <= usedSpaceAfterDedup) {
                        logger.error("Invalid dedup data. Used before dedup: " + usedSpaceBeforeDedup + " is less than used after dedup: " + usedSpaceAfterDedup);
                        usedSpaceBeforeDedup = usedSpaceAfterDedup;
                     }

                     dedupData.usedSpaceAfterDedup = usedSpaceAfterDedup;
                     dedupData.usedSpaceBeforeDedup = usedSpaceBeforeDedup;
                     dedupData.dedupSavings = usedSpaceBeforeDedup - usedSpaceAfterDedup;
                     dedupData.dedupRatio = (double)usedSpaceBeforeDedup / (double)usedSpaceAfterDedup;
                     var72 = dedupData;
                  } catch (Throwable var67) {
                     var10000 = var67;
                     var10001 = false;
                     break label801;
                  }

                  if (vsanConnection != null) {
                     vsanConnection.close();
                  }

                  try {
                     return var72;
                  } catch (Throwable var66) {
                     var10000 = var66;
                     var10001 = false;
                     break label801;
                  }
               }

               if (vsanConnection != null) {
                  vsanConnection.close();
               }

               return null;
            }

            var4 = var10000;
            if (vsanConnection != null) {
               vsanConnection.close();
            }

            throw var4;
         } catch (Throwable var69) {
            if (var4 == null) {
               var4 = var69;
            } else if (var4 != var69) {
               var4.addSuppressed(var69);
            }

            throw var4;
         }
      }
   }

   public VmCapacityData buildVmCapacityData(Map<VsanObjectType, VsanObjectSpaceSummary> spaceUsageMap) {
      VmCapacityData result = new VmCapacityData();
      VsanObjectSpaceSummary vmdkUsageData = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.vdisk);
      if (vmdkUsageData != null) {
         result.vmdkPrimaryUsage = toLong(vmdkUsageData.primaryCapacityB);
         result.vmdkPolicyOverheadUsage = toLong(vmdkUsageData.overheadB) + toLong(vmdkUsageData.temporaryOverheadB);
         if (vmdkUsageData.dpSpaceUsageInfo != null) {
            this.updateDpCapacity(result, vmdkUsageData.dpSpaceUsageInfo);
            result.vmdkPolicyOverheadUsage -= vmdkUsageData.dpSpaceUsageInfo.overheadB;
         }
      }

      VsanObjectSpaceSummary namespaceUsageData = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.namespace);
      if (namespaceUsageData != null) {
         result.homeObjectsUsage = toLong(namespaceUsageData.usedB);
         if (namespaceUsageData.dpSpaceUsageInfo != null) {
            this.updateDpCapacity(result, namespaceUsageData.dpSpaceUsageInfo);
            result.homeObjectsUsage -= namespaceUsageData.dpSpaceUsageInfo.overheadB;
         }
      }

      VsanObjectSpaceSummary othersUsageData = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.other);
      if (othersUsageData != null && othersUsageData.dpSpaceUsageInfo != null) {
         this.updateDpCapacity(result, othersUsageData.dpSpaceUsageInfo);
      }

      VsanObjectSpaceSummary swapSpaceUsage = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.vmswap);
      if (swapSpaceUsage != null) {
         result.swapObjectsUsage = toLong(swapSpaceUsage.usedB);
      }

      VsanObjectSpaceSummary vmemSnapshotUsage = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.vmem);
      if (vmemSnapshotUsage != null) {
         result.vmMemorySnapshotUsage = toLong(vmemSnapshotUsage.usedB);
      }

      VsanObjectSpaceSummary blockPrimaryUsageSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.attachedCnsVolBlock);
      if (blockPrimaryUsageSummary != null) {
         result.blockContainerPrimaryDataUsage = toLong(blockPrimaryUsageSummary.primaryCapacityB);
         result.blockContainerPolicyOverheadUsage = toLong(blockPrimaryUsageSummary.overheadB) + toLong(blockPrimaryUsageSummary.temporaryOverheadB);
      }

      result.totalVmUsage = this.getTotalVmCapacityUsage(result);
      return result;
   }

   private void updateDpCapacity(VmCapacityData vmCapacityData, VsanDataProtectionSpaceUsageStats dpSpaceUsageInfo) {
      vmCapacityData.dataProtectionPrimaryUsage += dpSpaceUsageInfo.overheadB - dpSpaceUsageInfo.raidPolicyOverheadB;
      vmCapacityData.dataProtectionRaidOverhead += dpSpaceUsageInfo.raidPolicyOverheadB;
   }

   private long getTotalVmCapacityUsage(VmCapacityData vmCapacityData) {
      long result = 0L;
      result += vmCapacityData.vmdkPrimaryUsage;
      result += vmCapacityData.vmdkPolicyOverheadUsage;
      result += vmCapacityData.homeObjectsUsage;
      result += vmCapacityData.swapObjectsUsage;
      result += vmCapacityData.vmMemorySnapshotUsage;
      result += vmCapacityData.blockContainerPrimaryDataUsage;
      result += vmCapacityData.blockContainerPolicyOverheadUsage;
      result += vmCapacityData.dataProtectionPrimaryUsage;
      result += vmCapacityData.dataProtectionRaidOverhead;
      result += vmCapacityData.overReservedSpace;
      return result;
   }

   private UserObjectsCapacityData buildUserObjectsCapacityData(Map<VsanObjectType, VsanObjectSpaceSummary> spaceUsageMap) {
      UserObjectsCapacityData result = new UserObjectsCapacityData();
      VsanObjectSpaceSummary totalFcd = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.improvedVirtualDisk);
      VsanObjectSpaceSummary totalFileShares;
      if (totalFcd != null) {
         result.otherFcd = toLong(totalFcd.usedB);
         totalFileShares = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.detachedCnsVolBlock);
         if (totalFileShares != null) {
            result.blockContainerVolumes = toLong(totalFileShares.usedB);
         }
      }

      totalFileShares = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.fileShare);
      VsanObjectSpaceSummary iscsiTargetSummary;
      VsanObjectSpaceSummary iscsiLunSummary;
      if (totalFileShares != null) {
         result.nativeFileShares = toLong(totalFileShares.usedB);
         iscsiTargetSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.attachedCnsVolFile);
         if (iscsiTargetSummary != null) {
            result.fileContainerVolumesAttached = toLong(iscsiTargetSummary.usedB);
            result.nativeFileShares -= result.fileContainerVolumesAttached;
         }

         iscsiLunSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.detachedCnsVolFile);
         if (iscsiLunSummary != null) {
            result.fileContainerVolumesDetached = toLong(iscsiLunSummary.usedB);
            result.nativeFileShares -= result.fileContainerVolumesDetached;
         }
      }

      iscsiTargetSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.iscsiTarget);
      if (iscsiTargetSummary != null) {
         result.iSCSI = toLong(iscsiTargetSummary.usedB);
      }

      iscsiLunSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.iscsiLun);
      if (iscsiLunSummary != null) {
         result.iSCSI += toLong(iscsiLunSummary.usedB);
      }

      VsanObjectSpaceSummary othersSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.other);
      if (othersSummary != null) {
         result.other = toLong(othersSummary.usedB);
         if (othersSummary.dpSpaceUsageInfo != null) {
            long dpOtherOverhead = othersSummary.dpSpaceUsageInfo.overheadB;
            result.other -= dpOtherOverhead;
         }
      }

      result.totalUserObjectsUsage = this.getTotalUserObjectsCapacityUsage(result);
      return result;
   }

   private long getTotalUserObjectsCapacityUsage(UserObjectsCapacityData userObjectsCapacityData) {
      long result = 0L;
      result += userObjectsCapacityData.blockContainerVolumes;
      result += userObjectsCapacityData.otherFcd;
      result += userObjectsCapacityData.fileContainerVolumesAttached;
      result += userObjectsCapacityData.fileContainerVolumesDetached;
      result += userObjectsCapacityData.nativeFileShares;
      result += userObjectsCapacityData.iSCSI;
      result += userObjectsCapacityData.other;
      return result;
   }

   private SystemUsageCapacityData buildSystemUsageCapacityData(Map<VsanObjectType, VsanObjectSpaceSummary> spaceUsageMap) {
      SystemUsageCapacityData result = new SystemUsageCapacityData();
      VsanObjectSpaceSummary statsDbSummary = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.statsdb);
      if (statsDbSummary != null) {
         result.performanceMgmtObjects = toLong(statsDbSummary.usedB);
      }

      VsanObjectSpaceSummary fsOverhead = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.fileSystemOverhead);
      if (fsOverhead != null) {
         result.fileServiceOverhead = toLong(fsOverhead.usedB);
      }

      VsanObjectSpaceSummary checksumOverhead = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.checksumOverhead);
      if (checksumOverhead != null) {
         result.checksumOverhead = toLong(checksumOverhead.usedB);
      }

      VsanObjectSpaceSummary dedupOverhead = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.dedupOverhead);
      if (dedupOverhead != null) {
         result.dedupOverhead = toLong(dedupOverhead.usedB);
      }

      VsanObjectSpaceSummary transientSpace = (VsanObjectSpaceSummary)spaceUsageMap.get(VsanObjectType.transientSpace);
      if (transientSpace != null) {
         result.transientSpace = toLong(transientSpace.usedB);
      }

      result.totalSystemUsage = this.getTotalSystemCapacityUsage(result);
      return result;
   }

   private long getTotalSystemCapacityUsage(SystemUsageCapacityData systemUsageCapacityData) {
      long result = 0L;
      result += systemUsageCapacityData.performanceMgmtObjects;
      result += systemUsageCapacityData.fileServiceOverhead;
      result += systemUsageCapacityData.checksumOverhead;
      result += systemUsageCapacityData.dedupOverhead;
      result += systemUsageCapacityData.transientSpace;
      return result;
   }

   private Map<VsanObjectType, VsanObjectSpaceSummary> getSpaceUsageByObjectType(VsanSpaceUsageDetailResult spaceUsageDetails) {
      Map<VsanObjectType, VsanObjectSpaceSummary> result = new HashMap();
      VsanObjectSpaceSummary[] var6;
      int var5 = (var6 = spaceUsageDetails.spaceUsageByObjectType).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VsanObjectSpaceSummary spaceSummary = var6[var4];
         result.put(VsanObjectType.parse(spaceSummary.objType), spaceSummary);
      }

      return result;
   }

   private static long toLong(Long value) {
      return value != null ? value : 0L;
   }
}
