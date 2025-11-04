package com.vmware.vsphere.client.vsan.whatif;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.VsanExtendedConfig;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanWhatIfEvacDetail;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanWhatIfEvacResult;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsService;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.FormatUtil;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class WhatIfPropertyProvider {
   public static final String IS_WHAT_IF_SUPPORTED = "isWhatIfSupported";
   @Autowired
   private VirtualObjectsService _virtualObjectsService;
   private static final VsanProfiler _profiler = new VsanProfiler(WhatIfPropertyProvider.class);
   private static final Log _logger = LogFactory.getLog(WhatIfPropertyProvider.class);

   @TsService
   public boolean getIsWhatIfSupported(ManagedObjectReference host) {
      return VsanCapabilityUtils.isWhatIfSupported(host);
   }

   @TsService
   public WhatIfResult getWhatIfResult(ManagedObjectReference hostRef, WhatIfSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         Measure measure = new Measure("Collect WhatIfResult");

         Throwable var10000;
         label637: {
            WhatIfResult var47;
            boolean var10001;
            try {
               VsanSystemEx vsanSystemEx = VsanProviderUtils.getVsanSystemEx(hostRef);
               if (StringUtils.isEmpty(spec.entityUuid)) {
                  spec.entityUuid = (String)QueryUtil.getProperty(hostRef, "config.vsanHostConfig.clusterInfo.nodeUuid", (Object)null);
               }

               WhatIfResult result = new WhatIfResult();
               if (VsanCapabilityUtils.isWhatIfSupported(hostRef)) {
                  Throwable var9 = null;
                  HashSet vsanObjectUuids = null;

                  VsanWhatIfEvacResult whatIfEvacResult;
                  try {
                     Measure whatIfCollect = measure.start("WhatIfEvacResult");

                     try {
                        whatIfEvacResult = vsanSystemEx.queryWhatIfEvacuationResult(spec.entityUuid);
                     } finally {
                        if (whatIfCollect != null) {
                           whatIfCollect.close();
                        }

                     }
                  } catch (Throwable var41) {
                     if (var9 == null) {
                        var9 = var41;
                     } else if (var9 != var41) {
                        var9.addSuppressed(var41);
                     }

                     throw var9;
                  }

                  List<VirtualObjectModel> vsanObjects = Collections.EMPTY_LIST;
                  if (spec.detailed && this.hasDataObjects(whatIfEvacResult)) {
                     vsanObjectUuids = new HashSet();
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.ensureAccess.inaccessibleObjects));
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.ensureAccess.incompliantObjects));
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.evacAllData.inaccessibleObjects));
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.evacAllData.incompliantObjects));
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.noAction.inaccessibleObjects));
                     vsanObjectUuids.addAll(Utils.arrayToList(whatIfEvacResult.noAction.incompliantObjects));
                     vsanObjects = this._virtualObjectsService.listVirtualObjects(spec.clusterRef);
                  }

                  long repairTime = 0L;
                  if (spec.clusterRef != null && (!ArrayUtils.isEmpty(whatIfEvacResult.ensureAccess.incompliantObjects) || !ArrayUtils.isEmpty(whatIfEvacResult.evacAllData.incompliantObjects) || !ArrayUtils.isEmpty(whatIfEvacResult.noAction.incompliantObjects))) {
                     repairTime = this.getClusterRepairTime(spec.clusterRef);
                  }

                  WhatIfData ensureAccessibilityData = this.getWhatIfData(whatIfEvacResult.ensureAccess, spec.detailed, vsanObjects, false, repairTime);
                  WhatIfData fullDataMigrationData = this.getWhatIfData(whatIfEvacResult.evacAllData, spec.detailed, vsanObjects, false, repairTime);
                  WhatIfData noDataMigrationData = this.getWhatIfData(whatIfEvacResult.noAction, spec.detailed, vsanObjects, true, repairTime);
                  result.ensureAccessibility = ensureAccessibilityData;
                  result.fullDataMigration = fullDataMigrationData;
                  result.noDataMigration = noDataMigrationData;
                  result.isWhatIfSupported = true;
               } else {
                  result.isWhatIfSupported = false;
               }

               var47 = result;
            } catch (Throwable var43) {
               var10000 = var43;
               var10001 = false;
               break label637;
            }

            if (measure != null) {
               measure.close();
            }

            label622:
            try {
               return var47;
            } catch (Throwable var42) {
               var10000 = var42;
               var10001 = false;
               break label622;
            }
         }

         var3 = var10000;
         if (measure != null) {
            measure.close();
         }

         throw var3;
      } catch (Throwable var44) {
         if (var3 == null) {
            var3 = var44;
         } else if (var3 != var44) {
            var3.addSuppressed(var44);
         }

         throw var3;
      }
   }

   private boolean hasDataObjects(VsanWhatIfEvacResult whatIfEvacResult) {
      return this.hasDataObjects(whatIfEvacResult.evacAllData) || this.hasDataObjects(whatIfEvacResult.ensureAccess) || this.hasDataObjects(whatIfEvacResult.noAction);
   }

   private boolean hasDataObjects(VsanWhatIfEvacDetail whatIfEvacDetail) {
      return whatIfEvacDetail.incompliantObjects != null && whatIfEvacDetail.incompliantObjects.length > 0 || whatIfEvacDetail.inaccessibleObjects != null && whatIfEvacDetail.inaccessibleObjects.length > 0;
   }

   @TsService
   public long getClusterRepairTime(ManagedObjectReference clusterRef) {
      Long objectRepairTime = null;

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point point = _profiler.point("VsanVcClusterConfigSystem.getConfigInfoEx");

            try {
               VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
               ConfigInfoEx configInfoEx = vsanConfigSystem != null ? vsanConfigSystem.getConfigInfoEx(clusterRef) : null;
               if (configInfoEx != null) {
                  VsanExtendedConfig extendedConfig = configInfoEx.getExtendedConfig();
                  objectRepairTime = extendedConfig != null ? extendedConfig.getObjectRepairTimer() : null;
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var16) {
            if (var3 == null) {
               var3 = var16;
            } else if (var3 != var16) {
               var3.addSuppressed(var16);
            }

            throw var3;
         }
      } catch (Exception var17) {
         _logger.error("Failed to retrieve repair time value on cluster.", var17);
         throw new VsanUiLocalizableException("vsan.whatIf.repairTimer.getClusterRepairTimeFailed", var17);
      }

      return objectRepairTime != null ? objectRepairTime : 0L;
   }

   private WhatIfData getWhatIfData(VsanWhatIfEvacDetail whatIfEvacDetail, Boolean detailed, List<VirtualObjectModel> virtualObjects, boolean isForNoAction, long repairTime) {
      WhatIfData result = new WhatIfData();
      result.success = whatIfEvacDetail.success;
      result.successWithoutDataLoss = whatIfEvacDetail.success && ArrayUtils.isEmpty(whatIfEvacDetail.inaccessibleObjects);
      result.bytesToSync = whatIfEvacDetail.bytesToSync == null ? 0L : whatIfEvacDetail.bytesToSync;
      result.extraSpaceNeeded = whatIfEvacDetail.extraSpaceNeeded == null ? 0L : whatIfEvacDetail.extraSpaceNeeded;
      result.failedDueToInaccessibleObjects = whatIfEvacDetail.failedDueToInaccessibleObjects == null ? false : whatIfEvacDetail.failedDueToInaccessibleObjects;
      result.successWithInaccessibleOrNonCompliantObjects = whatIfEvacDetail.success && !ArrayUtils.isEmpty(whatIfEvacDetail.inaccessibleObjects) || !ArrayUtils.isEmpty(whatIfEvacDetail.incompliantObjects);
      if (detailed) {
         result.objects = new ArrayList();
         result.objects.addAll(this.getVsanObjects(whatIfEvacDetail.inaccessibleObjects, virtualObjects, VsanWhatIfComplianceStatus.INACCESSIBLE));
         result.objects.addAll(this.getVsanObjects(whatIfEvacDetail.incompliantObjects, virtualObjects, VsanWhatIfComplianceStatus.NOT_COMPLIANT));
      }

      result.summary = this.getSummary(whatIfEvacDetail, isForNoAction);
      if (ArrayUtils.isNotEmpty(whatIfEvacDetail.incompliantObjects)) {
         result.repairTime = repairTime;
      }

      return result;
   }

   public List<VirtualObjectModel> getVsanObjects(String[] objectUUIDs, List<VirtualObjectModel> virtualObjects, VsanWhatIfComplianceStatus status) {
      List<VirtualObjectModel> result = new ArrayList();
      if (objectUUIDs == null) {
         return result;
      } else {
         Set<String> uuids = new HashSet(Arrays.asList(objectUUIDs));
         Iterator var7 = virtualObjects.iterator();

         while(true) {
            while(var7.hasNext()) {
               VirtualObjectModel virtualObject = (VirtualObjectModel)var7.next();
               if (ArrayUtils.isEmpty(virtualObject.children)) {
                  if (uuids.contains(virtualObject.uid)) {
                     virtualObject.whatIfComplianceStatus = status;
                     result.add(virtualObject.cloneWithoutChildren());
                  }
               } else {
                  if (virtualObject.vmRef != null) {
                     virtualObject.healthState = null;
                     virtualObject.storagePolicy = null;
                  }

                  List<VirtualObjectModel> children = new ArrayList();
                  VirtualObjectModel[] var12;
                  int var11 = (var12 = virtualObject.children).length;

                  VirtualObjectModel clone;
                  for(int var10 = 0; var10 < var11; ++var10) {
                     clone = var12[var10];
                     if (uuids.contains(clone.uid)) {
                        clone.whatIfComplianceStatus = status;
                        children.add(clone.cloneWithoutChildren());
                     }
                  }

                  clone = virtualObject.cloneWithoutChildren();
                  if (uuids.contains(clone.uid)) {
                     clone.whatIfComplianceStatus = status;
                  }

                  if (!children.isEmpty()) {
                     clone.children = (VirtualObjectModel[])children.toArray(new VirtualObjectModel[children.size()]);
                     result.add(clone);
                  } else if (uuids.contains(virtualObject.uid)) {
                     result.add(clone);
                  }
               }
            }

            return result;
         }
      }
   }

   private String getSummary(VsanWhatIfEvacDetail detail, Boolean isForNoAction) {
      String result = "";
      long bytesToSynch = detail.bytesToSync == null ? 0L : detail.bytesToSync;
      String formattedBytesToSynch = FormatUtil.getStorageFormatted(bytesToSynch, 1L, -1L);
      String incompliantObjectsCount;
      if (detail.success) {
         if (bytesToSynch == 0L) {
            result = Utils.getLocalizedString("vsan.whatIf.summary.common.noDataMoved", " ");
         } else if (!isForNoAction) {
            if (ArrayUtils.isEmpty(detail.incompliantObjects)) {
               result = Utils.getLocalizedString("vsan.whatIf.summary.common.sufficientCapacity", " ");
            }

            result = Utils.getLocalizedString("vsan.whatIf.summary.common.storageMoved", result, formattedBytesToSynch, " ");
         }

         if (!ArrayUtils.isEmpty(detail.inaccessibleObjects)) {
            incompliantObjectsCount = detail.inaccessibleObjects == null ? String.valueOf(0) : String.valueOf(detail.inaccessibleObjects.length);
            result = Utils.getLocalizedString("vsan.whatIf.summary.success.inaccessibleObjects", result, incompliantObjectsCount, " ");
         }

         if (!ArrayUtils.isEmpty(detail.incompliantObjects)) {
            incompliantObjectsCount = detail.incompliantObjects == null ? String.valueOf(0) : String.valueOf(detail.incompliantObjects.length);
            result = Utils.getLocalizedString("vsan.whatIf.summary.success.nonCompliant", result, incompliantObjectsCount, " ");
         }
      } else if (detail.extraSpaceNeeded != null && detail.extraSpaceNeeded > 0L) {
         incompliantObjectsCount = FormatUtil.getStorageFormatted(detail.extraSpaceNeeded, 1L, -1L);
         result = Utils.getLocalizedString("vsan.whatIf.summary.failure.extraStorageNeeded", incompliantObjectsCount);
      } else if (detail.failedDueToInaccessibleObjects != null && detail.failedDueToInaccessibleObjects) {
         result = Utils.getLocalizedString("vsan.whatIf.summary.failure.dueToInaccessibleObjects");
      }

      if (result.equals("")) {
         if (detail.success) {
            result = Utils.getLocalizedString("vsan.whatIf.summary.common.success");
         } else {
            result = Utils.getLocalizedString("vsan.whatIf.summary.common.error");
         }
      }

      return result;
   }
}
