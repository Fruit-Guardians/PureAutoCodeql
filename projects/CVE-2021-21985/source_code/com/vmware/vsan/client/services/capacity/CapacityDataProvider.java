package com.vmware.vsan.client.services.capacity;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.DefinedProfileSpec;
import com.vmware.vim.binding.vim.vm.ProfileSpec;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSpaceSummary;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceReportSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceUsage;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.vsan.DataEfficiencyCapacityState;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.health.CapacityOverviewData;
import com.vmware.vsphere.client.vsan.health.VsanDataEfficiencyData;
import com.vmware.vsphere.client.vsan.health.VsanObjectSpaceSummaryDataModel;
import com.vmware.vsphere.client.vsan.health.VsanSpaceUsageDataModel;
import com.vmware.vsphere.client.vsan.health.VsanWhatIfCapacityModel;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class CapacityDataProvider {
   private static final Log _logger = LogFactory.getLog(CapacityDataProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(CapacityDataProvider.class);

   @TsService
   public VsanSpaceUsageDataModel getSpaceUsage(ManagedObjectReference objectRef) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      VsanSpaceReportSystem capacitySystem = VsanProviderUtils.getVsanSpaceReportSystem(clusterRef);
      VsanSpaceUsage spaceUsage = null;
      Throwable var5 = null;
      VsanObjectSpaceSummary summary = null;

      try {
         VsanProfiler.Point p = _profiler.point("capacitySystem.querySpaceUsage");

         try {
            spaceUsage = capacitySystem.querySpaceUsage(clusterRef, (ProfileSpec[])null, false);
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var19) {
         if (var5 == null) {
            var5 = var19;
         } else if (var5 != var19) {
            var5.addSuppressed(var19);
         }

         throw var5;
      }

      if (spaceUsage == null) {
         return new VsanSpaceUsageDataModel();
      } else {
         VsanSpaceUsageDataModel usage = new VsanSpaceUsageDataModel();
         summary = spaceUsage.spaceOverview;
         CapacityOverviewData overviewData = new CapacityOverviewData();
         overviewData.totalSpace = usage.totalCapacityB = spaceUsage.getTotalCapacityB();
         overviewData.usedSpace = usage.totalUsedB = toLong(summary.usedB);
         overviewData.provisionedSpace = toLong(summary.provisionCapacityB);
         overviewData.physicalUsedSpace = toLong(summary.physicalUsedB);
         overviewData.reservedSpace = toLong(summary.reservedCapacityB);
         overviewData.vsanOverheadSpace = spaceUsage.spaceDetail != null ? this.getVsanSystemOverhead(spaceUsage.spaceDetail.spaceUsageByObjectType) : 0L;
         overviewData.overReservedSpace = toLong(summary.overReservedB);
         overviewData.freeSpace = toLong(spaceUsage.freeCapacityB);
         if (summary.dpSpaceUsageInfo != null) {
            overviewData.vsanDpOverheadSpace = summary.dpSpaceUsageInfo.overheadB + summary.dpSpaceUsageInfo.fragmentationOverheadB;
         } else {
            overviewData.vsanDpOverheadSpace = 0L;
         }

         usage.overview = overviewData;
         VsanObjectSpaceSummary[] objectSpaceSummaries = spaceUsage.spaceDetail == null ? null : spaceUsage.spaceDetail.spaceUsageByObjectType;
         usage.spaceDetail = new ArrayList();
         if (ArrayUtils.isNotEmpty(objectSpaceSummaries)) {
            VsanObjectSpaceSummary[] var12 = objectSpaceSummaries;
            int var11 = objectSpaceSummaries.length;

            for(int var10 = 0; var10 < var11; ++var10) {
               VsanObjectSpaceSummary objectSpace = var12[var10];
               VsanObjectSpaceSummaryDataModel detailObject = new VsanObjectSpaceSummaryDataModel();
               detailObject.objectType = objectSpace.objType;
               detailObject.overheadSpace = objectSpace.overheadB;
               detailObject.tempOverheadSpace = objectSpace.temporaryOverheadB;
               detailObject.physicalUsedSpace = objectSpace.usedB;
               detailObject.primaryCapacitySpace = objectSpace.primaryCapacityB;
               detailObject.reservedSpace = objectSpace.reservedCapacityB;
               if (objectSpace.dpSpaceUsageInfo != null) {
                  detailObject.vsanDpOverheadSpace = objectSpace.dpSpaceUsageInfo.overheadB + objectSpace.dpSpaceUsageInfo.fragmentationOverheadB;
               } else {
                  detailObject.vsanDpOverheadSpace = 0L;
               }

               usage.spaceDetail.add(detailObject);
            }
         }

         return usage;
      }
   }

   @TsService
   public VsanWhatIfCapacityModel getWhatIfCapacity(ManagedObjectReference objectRef, String profileId) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      VsanWhatIfCapacityModel whatIfCapacity = new VsanWhatIfCapacityModel();
      whatIfCapacity.isWhatIfCapacitySupported = VsanCapabilityUtils.isWhatIfCapacitySupported(clusterRef);
      if (whatIfCapacity.isWhatIfCapacitySupported) {
         DefinedProfileSpec profileSpec = new DefinedProfileSpec();
         profileSpec.profileId = profileId;
         ProfileSpec[] profiles = new ProfileSpec[]{profileSpec};
         VsanSpaceUsage spaceUsage = null;
         VsanSpaceReportSystem capacitySystem = VsanProviderUtils.getVsanSpaceReportSystem(clusterRef);

         try {
            Throwable var9 = null;
            Object var10 = null;

            try {
               VsanProfiler.Point p = _profiler.point("capacitySystem.querySpaceUsage");

               try {
                  spaceUsage = capacitySystem.querySpaceUsage(clusterRef, profiles, false);
                  whatIfCapacity.freeWhatifCapacityB = ArrayUtils.isEmpty(spaceUsage.getWhatifCapacities()) ? 0L : spaceUsage.getWhatifCapacities()[0].freeWhatifCapacityB;
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var19) {
               if (var9 == null) {
                  var9 = var19;
               } else if (var9 != var19) {
                  var9.addSuppressed(var19);
               }

               throw var9;
            }
         } catch (Exception var20) {
            _logger.error("Unable to get what-if capacity: " + var20);
         }
      }

      return whatIfCapacity;
   }

   @TsService
   public VsanDataEfficiencyData getClusterDataEfficiency(ManagedObjectReference objectRef) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      boolean dedupEnabled = (Boolean)QueryUtil.getProperty(clusterRef, "dataEfficiencyStatus", (Object)null);
      VsanDataEfficiencyData efficiency = new VsanDataEfficiencyData();
      efficiency.dedupEnabled = dedupEnabled;
      if (dedupEnabled) {
         VsanVcDiskManagementSystem mgmtSystem = VsanProviderUtils.getVcDiskManagementSystem(clusterRef);
         DataEfficiencyCapacityState state = null;
         Throwable var7 = null;
         Object var8 = null;

         try {
            VsanProfiler.Point point = _profiler.point("mgmtSystem.queryClusterDataEfficiencyCapacityState");

            try {
               state = mgmtSystem.queryClusterDataEfficiencyCapacityState(clusterRef);
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var15) {
            if (var7 == null) {
               var7 = var15;
            } else if (var7 != var15) {
               var7.addSuppressed(var15);
            }

            throw var7;
         }

         if (state != null && state.physicalCapacityUsed != null && state.logicalCapacityUsed != null) {
            efficiency.actualUsedSize = state.physicalCapacityUsed;
            efficiency.originalUsedSize = state.logicalCapacityUsed;
         }
      }

      return efficiency;
   }

   private long getVsanSystemOverhead(VsanObjectSpaceSummary[] objectsByType) {
      List<String> requiredKeys = new ArrayList(Arrays.asList("fileSystemOverhead", "checksumOverhead", "dedupOverhead"));
      if (objectsByType != null && objectsByType.length > 0) {
         long vsanSystemOverhead = 0L;
         VsanObjectSpaceSummary[] var8 = objectsByType;
         int var7 = objectsByType.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VsanObjectSpaceSummary object = var8[var6];
            if (requiredKeys.contains(object.objType)) {
               vsanSystemOverhead += object.overheadB == null ? 0L : object.overheadB;
            }
         }

         return vsanSystemOverhead;
      } else {
         return 0L;
      }
   }

   private static long toLong(Long value) {
      return value != null ? value : 0L;
   }
}
