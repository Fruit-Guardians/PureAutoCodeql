package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.VsanUpgradeSystem;
import com.vmware.vim.binding.vim.VsanUpgradeSystem.NotEnoughFreeCapacityIssue;
import com.vmware.vim.binding.vim.VsanUpgradeSystem.PreflightCheckIssue;
import com.vmware.vim.binding.vim.VsanUpgradeSystem.UpgradeStatus;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.VsanUpgradeSystemEx;
import com.vmware.vim.vsan.binding.vim.cluster.VsanDiskFormatConversionSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanUpgradeStatusEx;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanObjectOverallHealth;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanUpgradePropertyProvider {
   private static final String PROP_OBJECT_HEALTH = "objectHealth";
   private static final Log _logger = LogFactory.getLog(VsanUpgradePropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanUpgradePropertyProvider.class);
   private final LegacyVsanObjectVersionProvider _legacyVsanObjectVersionProvider;

   public VsanUpgradePropertyProvider(LegacyVsanObjectVersionProvider legacyVsanObjectVersionProvider) {
      this._legacyVsanObjectVersionProvider = legacyVsanObjectVersionProvider;
   }

   @TsService
   public VsanUpgradeStatusData getVsanUpgradeStatus(ManagedObjectReference clusterRef) throws Exception {
      VsanUpgradeStatusData result = null;

      VsanUpgradeSystem upgradeSystem;
      VsanUpgradeSystemEx upgradeSystemEx;
      try {
         Throwable var39 = null;
         upgradeSystem = null;

         try {
            VsanProfiler.Point p = _profiler.point("upgradeSystemEx.queryUpgradeStatus");

            try {
               upgradeSystemEx = VsanProviderUtils.getVsanUpgradeSystemEx(clusterRef);
               VsanUpgradeStatusEx vsanUpgradeStatusEx = upgradeSystemEx.queryUpgradeStatus(clusterRef);
               if (!vsanUpgradeStatusEx.inProgress && vsanUpgradeStatusEx.aborted == null && vsanUpgradeStatusEx.completed == null) {
                  result = new VsanUpgradeStatusData(true);
               } else {
                  result = new VsanUpgradeStatusData(vsanUpgradeStatusEx);
               }
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var37) {
            if (var39 == null) {
               var39 = var37;
            } else if (var39 != var37) {
               var39.addSuppressed(var37);
            }

            throw var39;
         }
      } catch (Exception var38) {
         boolean isUpgradeSystem2Supported = VsanCapabilityUtils.isUpgradeSystem2SupportedOnVc(clusterRef);
         upgradeSystem = isUpgradeSystem2Supported ? VsanProviderUtils.getVsanUpgradeSystem(clusterRef) : VsanProviderUtils.getVsanLegacyUpgradeSystem(clusterRef);

         try {
            Throwable var5 = null;
            upgradeSystemEx = null;

            try {
               VsanProfiler.Point p = _profiler.point("upgradeSystem.queryUpgradeStatus");

               try {
                  UpgradeStatus upgradeStatus = upgradeSystem.queryUpgradeStatus(clusterRef);
                  if (!upgradeStatus.inProgress && upgradeStatus.aborted == null && upgradeStatus.completed == null) {
                     result = new VsanUpgradeStatusData(false);
                  } else {
                     result = new VsanUpgradeStatusData(upgradeStatus);
                  }
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var34) {
               if (var5 == null) {
                  var5 = var34;
               } else if (var5 != var34) {
                  var5.addSuppressed(var34);
               }

               throw var5;
            }
         } catch (Exception var35) {
            result = new VsanUpgradeStatusData(false);
         }
      }

      return result;
   }

   @TsService
   public VsanVersionInfoPerHost[] getVsanHostVersions(ManagedObjectReference clusterRef) throws Exception {
      List<VsanVersionInfoPerHost> result = new ArrayList();
      PropertyValue[] hostsVersionValues = QueryUtil.getPropertyForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), "vsanDiskVersionsData").getPropertyValues();
      PropertyValue[] var7 = hostsVersionValues;
      int var6 = hostsVersionValues.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue value = var7[var5];
         VsanDiskVersionData[] vsanDiskVersionData = (VsanDiskVersionData[])value.value;
         result.add(new VsanVersionInfoPerHost(vsanDiskVersionData));
      }

      return (VsanVersionInfoPerHost[])result.toArray(new VsanVersionInfoPerHost[result.size()]);
   }

   @TsService
   public VsanUpgradePreflightCheckIssue[] getVsanUpgradePreflightCheckResult(ManagedObjectReference clusterRef) throws Exception {
      boolean isUpgradeSystemExSupported = VsanCapabilityUtils.isUpgradeSystemExSupportedOnVc(clusterRef);
      PreflightCheckIssue[] result = null;
      Throwable var5;
      Object var6;
      VsanProfiler.Point p;
      if (isUpgradeSystemExSupported) {
         VsanUpgradeSystemEx upgradeSystem = VsanProviderUtils.getVsanUpgradeSystemEx(clusterRef);
         var5 = null;
         var6 = null;

         try {
            p = _profiler.point("upgradeSystemEx.performUpgradePreflightCheck");

            try {
               result = upgradeSystem.performUpgradePreflightCheck(clusterRef, (Boolean)null, (VsanDiskFormatConversionSpec)null).issues;
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var25) {
            if (var5 == null) {
               var5 = var25;
            } else if (var5 != var25) {
               var5.addSuppressed(var25);
            }

            throw var5;
         }
      } else {
         VsanUpgradeSystem upgradeSystem = VsanProviderUtils.getVsanLegacyUpgradeSystem(clusterRef);
         var5 = null;
         var6 = null;

         try {
            p = _profiler.point("upgradeSystem.performUpgradePreflightCheck");

            try {
               result = upgradeSystem.performUpgradePreflightCheck(clusterRef, false).issues;
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var27) {
            if (var5 == null) {
               var5 = var27;
            } else if (var5 != var27) {
               var5.addSuppressed(var27);
            }

            throw var5;
         }
      }

      return convertIssues(result);
   }

   private static VsanUpgradePreflightCheckIssue[] convertIssues(PreflightCheckIssue[] originalIssues) {
      if (ArrayUtils.isEmpty(originalIssues)) {
         return new VsanUpgradePreflightCheckIssue[0];
      } else {
         List<VsanUpgradePreflightCheckIssue> issues = new ArrayList(originalIssues.length);
         PreflightCheckIssue[] var5 = originalIssues;
         int var4 = originalIssues.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            PreflightCheckIssue originalIssue = var5[var3];
            VsanUpgradePreflightCheckIssue issue = new VsanUpgradePreflightCheckIssue();
            issue.message = originalIssue.msg;
            if (originalIssue instanceof NotEnoughFreeCapacityIssue) {
               NotEnoughFreeCapacityIssue nefcIssue = (NotEnoughFreeCapacityIssue)originalIssue;
               if (nefcIssue.reducedRedundancyUpgradePossible) {
                  issue.type = VsanUpgradePreflightCheckIssue.IssueType.WARNING;
               } else {
                  issue.type = VsanUpgradePreflightCheckIssue.IssueType.ERROR;
               }
            } else {
               issue.type = VsanUpgradePreflightCheckIssue.IssueType.ERROR;
            }

            issues.add(issue);
         }

         return (VsanUpgradePreflightCheckIssue[])issues.toArray(new VsanUpgradePreflightCheckIssue[issues.size()]);
      }
   }

   @TsService
   public int getLatestVsanVersion(ManagedObjectReference clusterRef) throws Exception {
      int latestVersion = 2;
      boolean isUpgradeSystemExSupported = VsanCapabilityUtils.isUpgradeSystemExSupportedOnVc(clusterRef);
      if (isUpgradeSystemExSupported) {
         try {
            Throwable var4 = null;
            Object var5 = null;

            try {
               VsanProfiler.Point p = _profiler.point("upgradeSystemEx.retrieveSupportedFormatVersion");

               try {
                  VsanUpgradeSystemEx upgradeSystem = VsanProviderUtils.getVsanUpgradeSystemEx(clusterRef);
                  latestVersion = upgradeSystem.retrieveSupportedFormatVersion(clusterRef);
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var15) {
               if (var4 == null) {
                  var4 = var15;
               } else if (var4 != var15) {
                  var4.addSuppressed(var15);
               }

               throw var4;
            }
         } catch (Exception var16) {
            _logger.error("Could not retrieve latest available disk format version", var16);
         }
      }

      return latestVersion;
   }

   @TsService
   public boolean getHasOldVsanObject(ManagedObjectReference clusterRef) throws Exception {
      Boolean hasOldVsanObject = null;

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = _profiler.point("upgradeSystemEx.queryObjectHealthSummary");

            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               VsanObjectOverallHealth summary = healthSystem.queryObjectHealthSummary(clusterRef, (String[])null, (Boolean)null, (Boolean)null);
               if (summary != null && summary.objectVersionCompliance != null) {
                  hasOldVsanObject = !summary.objectVersionCompliance;
               }
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var15) {
            if (var3 == null) {
               var3 = var15;
            } else if (var3 != var15) {
               var3.addSuppressed(var15);
            }

            throw var3;
         }
      } catch (Exception var16) {
         _logger.warn("Cannot retrieve object version compliance data from the health system.", var16);
      }

      if (hasOldVsanObject == null) {
         hasOldVsanObject = this.checkForOldVsanObjects(clusterRef);
      }

      return hasOldVsanObject;
   }

   private boolean checkForOldVsanObjects(ManagedObjectReference clusterRef) {
      boolean hasOldVsanObject = false;

      try {
         hasOldVsanObject = this._legacyVsanObjectVersionProvider.getHasOldObject(clusterRef);
      } catch (Exception var4) {
         _logger.warn("Cannot retrieve hasOldVsanObject property from hosts' VsanInternalSystems.", var4);
      }

      return hasOldVsanObject;
   }
}
