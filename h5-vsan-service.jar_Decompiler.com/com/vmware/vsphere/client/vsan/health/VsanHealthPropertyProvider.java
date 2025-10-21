package com.vmware.vsphere.client.vsan.health;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.ManagedEntity.Status;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.VsanPhoneHomeSystem;
import com.vmware.vim.vsan.binding.vim.VsanVcPrecheckerSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthConfigs;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultKeyValuePair;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthSummary;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthSystemVersionResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthTest;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterTelemetryProxyConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterVMsHealthOverallResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterVMsHealthSummaryResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHealthExtMgmtPreCheckResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanHostHealthSystemVersionResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanPhysicalDiskHealth;
import com.vmware.vim.vsan.binding.vim.host.VsanPhysicalDiskHealthSummary;
import com.vmware.vim.vsan.binding.vim.vsan.VsanDiskComplianceResourceCheck;
import com.vmware.vim.vsan.binding.vim.vsan.VsanDiskGroupComplianceResourceCheck;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFaultDomainComplianceResourceCheck;
import com.vmware.vim.vsan.binding.vim.vsan.VsanHealthPerspective;
import com.vmware.vim.vsan.binding.vim.vsan.VsanHostComplianceResourceCheck;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.base.util.VsphereHealthProviderUtils;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanHealthPropertyProvider {
   private static final Log _logger = LogFactory.getLog(VsanHealthPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanHealthPropertyProvider.class);

   @TsService
   public VsanHealthServiceStatus getVsanHealthServiceStatus(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      String clusterStatus = null;
      Throwable var4 = null;
      VsanClusterHealthSystemVersionResult versionResult = null;

      try {
         VsanProfiler.Point point = _profiler.point("mgmtSystem.getClusterStatus");

         try {
            clusterStatus = healthSystem.getClusterStatus(clusterRef, (String)null);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var26) {
         if (var4 == null) {
            var4 = var26;
         } else if (var4 != var26) {
            var4.addSuppressed(var26);
         }

         throw var4;
      }

      VsanHealthServiceStatus vhss = VsanHealthUtil.getVsanHealthServiceStatus(clusterStatus);
      versionResult = null;
      Throwable var30 = null;
      Object var7 = null;

      try {
         VsanProfiler.Point point = _profiler.point("mgmtSystem.queryVerifyClusterHealthSystemVersions");

         try {
            versionResult = healthSystem.queryVerifyClusterHealthSystemVersions(clusterRef);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var28) {
         if (var30 == null) {
            var30 = var28;
         } else if (var30 != var28) {
            var30.addSuppressed(var28);
         }

         throw var30;
      }

      this.addVersionCheckResult(vhss, versionResult);
      if (vhss != null) {
         return vhss;
      } else {
         throw new Exception(Utils.getLocalizedString("vsan.health.status.error"));
      }
   }

   private void addVersionCheckResult(VsanHealthServiceStatus vhss, VsanClusterHealthSystemVersionResult versionResult) {
      if (versionResult != null) {
         vhss.versionCheck = new VsanHealthServiceVersionCheck();
         vhss.versionCheck.latestVersiobNumber = versionResult.vcVersion;
         String hostVersionStr = "";
         boolean upgradePossible = versionResult.upgradePossible == null ? false : versionResult.upgradePossible;
         if (versionResult.hostResults != null) {
            vhss.versionCheck.canBeUpgraded = upgradePossible;
            VsanHostHealthSystemVersionResult[] var8;
            int var7 = (var8 = versionResult.hostResults).length;

            for(int var6 = 0; var6 < var7; ++var6) {
               VsanHostHealthSystemVersionResult hostVersion = var8[var6];
               if (hostVersion != null && hostVersion.version != null && !hostVersion.version.equals("0.0")) {
                  String currentHostVersionStr = hostVersion.version;
                  if (!hostVersionStr.isEmpty() && !hostVersionStr.equals(currentHostVersionStr)) {
                     hostVersionStr = Utils.getLocalizedString("vsan.health.service.version.mixed");
                  } else {
                     hostVersionStr = currentHostVersionStr;
                  }
               }
            }
         }

         if (hostVersionStr.isEmpty()) {
            vhss.versionCheck.canBeUpgraded = upgradePossible;
         }

         if (versionResult.hostResults == null || versionResult.hostResults.length == 0) {
            vhss.versionCheck.canBeUpgraded = false;
         }

         vhss.versionCheck.versionNumber = hostVersionStr;
      }

   }

   @TsService
   public VsanHealthData getVsanHealthFromCache(ManagedObjectReference clusterRef, Boolean isDefaultPerspective, boolean isVsphereHealth) throws Exception {
      boolean includeObjUuid = true;
      boolean useCache = true;
      return this.getClusterHealthSummary(clusterRef, includeObjUuid, useCache, isDefaultPerspective, isVsphereHealth);
   }

   @TsService
   public VsanHealthData getVsanHealth(ManagedObjectReference clusterRef, Boolean isDefaultPerspective, boolean isVsphereHealth) throws Exception {
      boolean includeObjUuid = true;
      boolean useCache = false;
      return this.getClusterHealthSummary(clusterRef, includeObjUuid, useCache, isDefaultPerspective, isVsphereHealth);
   }

   @TsService
   public boolean isSilentCheckSupported(ManagedObjectReference clusterRef) throws Exception {
      return VsanCapabilityUtils.isSilentCheckSupportedOnVc(clusterRef);
   }

   @TsService
   public List<String> getVsanSilentChecks(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      List<String> silentChecks = new ArrayList();
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("healthSystem.getVsanClusterSilentChecks");

         try {
            String[] checks = healthSystem.getVsanClusterSilentChecks(clusterRef);
            if (!ArrayUtils.isEmpty(checks)) {
               silentChecks = Arrays.asList(checks);
            }
         } finally {
            if (point != null) {
               point.close();
            }

         }

         return (List)silentChecks;
      } catch (Throwable var13) {
         if (var4 == null) {
            var4 = var13;
         } else if (var4 != var13) {
            var4.addSuppressed(var13);
         }

         throw var4;
      }
   }

   @TsService
   public void setVsanSilentChecks(ManagedObjectReference clusterRef, List<String> addedSilenceChecks, List<String> removedSilenceChecks) throws Exception {
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.setVsanClusterSilentChecks");

         try {
            VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
            healthSystem.setVsanClusterSilentChecks(clusterRef, (String[])addedSilenceChecks.toArray(new String[0]), (String[])removedSilenceChecks.toArray(new String[0]));
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var13) {
         if (var4 == null) {
            var4 = var13;
         } else if (var4 != var13) {
            var4.addSuppressed(var13);
         }

         throw var4;
      }
   }

   @TsService
   public VsanHealthData getVsanHealthSummaryFromCache(ManagedObjectReference clusterRef) throws Exception {
      boolean includeObjUuid = false;
      boolean useCache = true;
      return this.getClusterHealthSummary(clusterRef, includeObjUuid, useCache, true, false);
   }

   private VsanHealthData getClusterHealthSummary(ManagedObjectReference clusterRef, boolean includeObjUuids, boolean fetchFromCache, Boolean isDefaultPerspective, boolean isVsphereHealth) throws Exception {
      String[] requiredFields = new String[]{"groups", "timestamp"};
      VsanVcClusterHealthSystem healthSystem = null;
      String perspective;
      Boolean includeDataProtection;
      if (isVsphereHealth) {
         healthSystem = VsphereHealthProviderUtils.getVsphereHealthSystem(clusterRef);
         includeDataProtection = null;
         perspective = null;
      } else {
         healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
         includeDataProtection = VsanCapabilityUtils.isLocalDataProtectionSupportedOnVc(clusterRef) ? true : null;
         perspective = isDefaultPerspective ? VsanHealthPerspective.defaultView.toString() : VsanHealthPerspective.deployAssist.toString();
      }

      VsanClusterHealthSummary healthSummary = null;
      Throwable var11 = null;
      VsanClusterHealthGroup healthGroup = null;

      try {
         VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterHealthSummary");

         try {
            healthSummary = healthSystem.queryClusterHealthSummary(clusterRef, (Integer)null, (String[])null, includeObjUuids, requiredFields, fetchFromCache, perspective, (ManagedObjectReference[])null, includeDataProtection);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var21) {
         if (var11 == null) {
            var11 = var21;
         } else if (var11 != var21) {
            var11.addSuppressed(var21);
         }

         throw var11;
      }

      Set<ManagedObjectReference> allMoRefs = new HashSet();
      if (healthSummary != null && healthSummary.groups != null) {
         VsanClusterHealthGroup[] var15;
         int var14 = (var15 = healthSummary.groups).length;

         for(int var24 = 0; var24 < var14; ++var24) {
            healthGroup = var15[var24];
            VsanHealthUtil.addToTestMoRefs(healthGroup, allMoRefs, clusterRef.getServerGuid());
         }
      }

      VsanHealthData healthData = VsanHealthUtil.getVsanHealthData(healthSummary, VsanHealthUtil.getNamesForMoRefs(allMoRefs), false);
      healthData.timeStamp = healthSummary.getTimestamp();
      return healthData;
   }

   @TsService
   public AggregatedVsanHealthSummary getCachedClusterHealthSummary(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      VsanClusterHealthSummary summary = null;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterHealthSummary");

         try {
            Boolean includeDataProtection = VsanCapabilityUtils.isLocalDataProtectionSupportedOnVc(clusterRef) ? true : null;
            summary = healthSystem.queryClusterHealthSummary(clusterRef, (Integer)null, (String[])null, true, (String[])null, true, VsanHealthPerspective.defaultView.toString(), (ManagedObjectReference[])null, includeDataProtection);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var23) {
         if (var4 == null) {
            var4 = var23;
         } else if (var4 != var23) {
            var4.addSuppressed(var23);
         }

         throw var4;
      }

      HardwareOverallHealth physicalDisksHealth = new HardwareOverallHealth();
      int total = 0;
      int redCount = 0;
      int yellowCount = 0;
      VsanPhysicalDiskHealthSummary[] results = summary.physicalDisksHealth;
      ArrayList<String> statusList = new ArrayList();
      VsanPhysicalDiskHealthSummary[] var13 = results;
      int var12 = results.length;

      int var15;
      int var16;
      for(int var11 = 0; var11 < var12; ++var11) {
         VsanPhysicalDiskHealthSummary pdSummary = var13[var11];
         statusList.add(pdSummary.overallHealth);
         total += pdSummary.disks == null ? 0 : pdSummary.disks.length;
         if (pdSummary.disks != null) {
            VsanPhysicalDiskHealth[] var17;
            var16 = (var17 = pdSummary.disks).length;

            for(var15 = 0; var15 < var16; ++var15) {
               VsanPhysicalDiskHealth h = var17[var15];
               if (h.summaryHealth.equals(VsanHealthStatus.red.toString())) {
                  ++redCount;
               } else if (h.summaryHealth.equals(VsanHealthStatus.yellow.toString())) {
                  ++yellowCount;
               }
            }
         }
      }

      physicalDisksHealth.total = total;
      if (statusList.contains(VsanHealthStatus.red.toString())) {
         physicalDisksHealth.overallStatus = VsanHealthStatus.red.toString();
         physicalDisksHealth.issueCount = redCount;
      } else if (statusList.contains(VsanHealthStatus.yellow.toString())) {
         physicalDisksHealth.overallStatus = VsanHealthStatus.yellow.toString();
         physicalDisksHealth.issueCount = yellowCount;
      } else {
         physicalDisksHealth.overallStatus = VsanHealthStatus.green.toString();
         physicalDisksHealth.issueCount = 0;
      }

      HardwareOverallHealth hostsHealth = new HardwareOverallHealth();
      ManagedObjectReference[] hosts = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host", (Object)null);
      hostsHealth.total = hosts != null ? hosts.length : 0;
      yellowCount = 0;
      redCount = 0;
      if (hosts != null) {
         ManagedObjectReference[] var37 = hosts;
         int var34 = hosts.length;

         for(int var32 = 0; var32 < var34; ++var32) {
            ManagedObjectReference h = var37[var32];
            ConnectionState state = (ConnectionState)QueryUtil.getProperty(h, "runtime.connectionState", (Object)null);
            Status status = (Status)QueryUtil.getProperty(h, "overallStatus", (Object)null);
            if (ConnectionState.connected.equals(state)) {
               if (Status.red.equals(status)) {
                  ++redCount;
                  hostsHealth.overallStatus = VsanHealthStatus.red.toString();
               } else if (Status.yellow.equals(status)) {
                  ++yellowCount;
                  if (!VsanHealthStatus.red.toString().equals(hostsHealth.overallStatus)) {
                     hostsHealth.overallStatus = VsanHealthStatus.yellow.toString();
                  }
               }
            } else {
               ++redCount;
               hostsHealth.overallStatus = VsanHealthStatus.red.toString();
            }
         }
      }

      if (VsanHealthStatus.red.toString().equals(hostsHealth.overallStatus)) {
         hostsHealth.issueCount = redCount;
      } else if (VsanHealthStatus.yellow.toString().equals(hostsHealth.overallStatus)) {
         hostsHealth.issueCount = yellowCount;
      } else {
         hostsHealth.overallStatus = VsanHealthStatus.green.toString();
         hostsHealth.issueCount = 0;
      }

      total = 0;
      yellowCount = 0;
      redCount = 0;
      VsanClusterVMsHealthOverallResult vmResult = summary.getVmHealth();
      HardwareOverallHealth vmsHealth = new HardwareOverallHealth();
      vmsHealth.overallStatus = vmResult.overallHealthState;
      if (vmResult.healthStateList != null) {
         VsanClusterVMsHealthSummaryResult[] var40;
         var16 = (var40 = vmResult.healthStateList).length;

         for(var15 = 0; var15 < var16; ++var15) {
            VsanClusterVMsHealthSummaryResult r = var40[var15];
            total += r.numVMs;
            if (r.state.equalsIgnoreCase(VsanHealthStatus.red.toString())) {
               redCount += r.numVMs;
            } else if (r.state.equalsIgnoreCase(VsanHealthStatus.yellow.toString())) {
               yellowCount += r.numVMs;
            }
         }
      }

      vmsHealth.total = total;
      if (VsanHealthStatus.red.toString().equalsIgnoreCase(vmsHealth.overallStatus)) {
         vmsHealth.issueCount = redCount;
      } else if (VsanHealthStatus.yellow.toString().equalsIgnoreCase(vmsHealth.overallStatus)) {
         vmsHealth.issueCount = yellowCount;
      }

      AggregatedVsanHealthSummary aggregatedSummary = new AggregatedVsanHealthSummary();
      aggregatedSummary.hostSummary = hostsHealth;
      aggregatedSummary.physicalDiskSummary = physicalDisksHealth;
      aggregatedSummary.networkIssueDetected = summary.networkHealth.issueFound;
      aggregatedSummary.vmSummary = vmsHealth;
      return aggregatedSummary;
   }

   @TsService
   public boolean getIsCloudHealthSupported(ManagedObjectReference clusterRef) {
      return VsanCapabilityUtils.isCloudHealthSupportedOnVc(clusterRef);
   }

   @TsService
   public ManagedObjectReference getCloudHealthCheckResult(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference taskMoRef = null;
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point point = _profiler.point("phoneHomeSystem.vsanPerformOnlineHealthCheck");

         try {
            VsanPhoneHomeSystem phoneHomeSystem = VsanProviderUtils.getVsanPhoneHomeSystem(clusterRef);
            taskMoRef = phoneHomeSystem.vsanPerformOnlineHealthCheck(clusterRef);
            taskMoRef = VsanHealthUtil.buildTaskMor(taskMoRef.getValue(), clusterRef.getServerGuid());
         } finally {
            if (point != null) {
               point.close();
            }

         }

         return taskMoRef;
      } catch (Throwable var12) {
         if (var3 == null) {
            var3 = var12;
         } else if (var3 != var12) {
            var3.addSuppressed(var12);
         }

         throw var3;
      }
   }

   @TsService
   public ExternalProxySettingsConfig getExternalProxySettings(ManagedObjectReference clusterRef) throws VsanUiLocalizableException {
      if (clusterRef == null) {
         throw new VsanUiLocalizableException("vsan.internet.error.nocluster");
      } else {
         VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
         VsanClusterHealthConfigs configs = null;

         Throwable proxy;
         ExternalProxySettingsConfig result;
         try {
            proxy = null;
            result = null;

            try {
               VsanProfiler.Point point = _profiler.point("mgmtSystem.queryVsanClusterHealthConfig");

               try {
                  configs = healthSystem.queryVsanClusterHealthConfig(clusterRef);
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var18) {
               if (proxy == null) {
                  proxy = var18;
               } else if (proxy != var18) {
                  proxy.addSuppressed(var18);
               }

               throw proxy;
            }
         } catch (Exception var19) {
            _logger.error(var19);
            throw new VsanUiLocalizableException("vsan.internet.error.remotecall");
         }

         proxy = null;
         if (configs == null) {
            return null;
         } else {
            VsanClusterTelemetryProxyConfig proxy = configs.getVsanTelemetryProxy();
            result = new ExternalProxySettingsConfig();
            VsanClusterHealthResultKeyValuePair[] pairs = configs.getConfigs();
            if (pairs != null && pairs.length > 0) {
               VsanClusterHealthResultKeyValuePair[] var10;
               int var9 = (var10 = configs.getConfigs()).length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  VsanClusterHealthResultKeyValuePair pair = var10[var8];
                  if ("enableInternetAccess".equalsIgnoreCase(pair.getKey())) {
                     result.enableInternetAccess = "true".equalsIgnoreCase(pair.getValue());
                  }
               }
            }

            if (proxy != null && proxy.host != null && !proxy.host.isEmpty()) {
               result.isAutoDiscovered = proxy.autoDiscovered != null ? proxy.autoDiscovered : false;
               result.hostName = proxy.getHost();
               result.port = proxy.getPort();
               result.userName = proxy.getUser();
               result.password = proxy.getPassword();
            }

            return result;
         }
      }
   }

   private VsanHealthServicePreCheckResult getPreFlightCheckResult(ManagedObjectReference clusterRef, boolean enable) throws Exception {
      if (clusterRef == null) {
         return null;
      } else {
         VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
         VsanHealthExtMgmtPreCheckResult result = null;
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point point = _profiler.point("mgmtSystem.preCheckClusterForManageExtension");

            try {
               result = healthSystem.preCheckClusterForManageExtension(clusterRef, enable, "health");
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var13) {
            if (var5 == null) {
               var5 = var13;
            } else if (var5 != var13) {
               var5.addSuppressed(var13);
            }

            throw var5;
         }

         return this.getPreCheckResult(result);
      }
   }

   private VsanHealthServicePreCheckResult getPreCheckResult(VsanHealthExtMgmtPreCheckResult preCheckResult) {
      if (preCheckResult == null) {
         return null;
      } else {
         VsanHealthServicePreCheckResult result = new VsanHealthServicePreCheckResult();
         result.passed = preCheckResult.overallResult;
         if (preCheckResult.vumRegistered != null) {
            result.vumRegistered = preCheckResult.vumRegistered;
         }

         result.testsData = new ArrayList();
         VsanClusterHealthTest[] var6;
         int var5 = (var6 = preCheckResult.results).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthTest test = var6[var4];
            result.testsData.add(new VsanTestData(test, (Map)null));
         }

         return result;
      }
   }

   @TsService
   public ManagedObjectReference getCompliancePrecheckTask(ManagedObjectReference clusterRef) throws Exception {
      VsanVcPrecheckerSystem precheckerSystem = VsanProviderUtils.getVsanPrecheckerSystem(clusterRef);
      ManagedObjectReference taskMoRef = null;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("precheckerSystem.queryComplianceResourceCheckAsync");

         try {
            taskMoRef = precheckerSystem.queryComplianceResourceCheckAsync(clusterRef);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var12) {
         if (var4 == null) {
            var4 = var12;
         } else if (var4 != var12) {
            var4.addSuppressed(var12);
         }

         throw var4;
      }

      if (taskMoRef != null) {
         taskMoRef.setServerGuid(clusterRef.getServerGuid());
      }

      return taskMoRef;
   }

   @TsService
   public ComplianceCheckResultData getCompliancePrecheckResult(ManagedObjectReference param1) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private ComplianceCheckResultObj transformHostData(VsanHostComplianceResourceCheck host) {
      ComplianceCheckResultObj hostDataObj = new ComplianceCheckResultObj();
      hostDataObj.objectType = "host";
      hostDataObj.uuid = host.uuid;
      hostDataObj.name = host.host == null ? null : host.host.getValue();
      hostDataObj.isNew = host.isNew;
      hostDataObj.hasChanged = hostDataObj.isNew;
      if (!ArrayUtils.isEmpty(host.diskGroups)) {
         List<ComplianceCheckResultObj> diskGroupList = new ArrayList();
         VsanDiskGroupComplianceResourceCheck[] var7;
         int var6 = (var7 = host.diskGroups).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VsanDiskGroupComplianceResourceCheck diskgroup = var7[var5];
            ComplianceCheckResultObj diskGroupDataObj = this.transformDiskGroupData(diskgroup);
            diskGroupList.add(diskGroupDataObj);
            hostDataObj.finalCacheCapacity += diskGroupDataObj.finalCacheCapacity;
            hostDataObj.originalCacheCapacity += diskGroupDataObj.originalCacheCapacity;
            hostDataObj.initCacheCapacity += diskGroupDataObj.initCacheCapacity;
            hostDataObj.finalUsedCacheCapacity += diskGroupDataObj.finalUsedCacheCapacity;
            hostDataObj.finalUsedCapacity += diskGroupDataObj.finalUsedCapacity;
            hostDataObj.initCapacity += diskGroupDataObj.initCapacity;
            hostDataObj.originalCapacity += diskGroupDataObj.originalCapacity;
            hostDataObj.finalCapacity += diskGroupDataObj.finalCapacity;
            if (!hostDataObj.hasChanged && diskGroupDataObj.hasChanged) {
               hostDataObj.hasChanged = diskGroupDataObj.hasChanged;
            }
         }

         hostDataObj.childDevices = this.parseListToArray(diskGroupList);
      }

      return hostDataObj;
   }

   private ComplianceCheckResultObj transformDiskData(VsanDiskComplianceResourceCheck disk) {
      ComplianceCheckResultObj diskDataObj = new ComplianceCheckResultObj();
      diskDataObj.objectType = "Disk";
      diskDataObj.uuid = disk.uuid;
      diskDataObj.name = disk.deviceName;
      diskDataObj.initCapacity = disk.initCapacity;
      diskDataObj.finalUsedCapacity = disk.finalCapacity;
      diskDataObj.isNew = disk.isNew;
      diskDataObj.hasChanged = diskDataObj.isNew || diskDataObj.initCapacity != diskDataObj.finalUsedCapacity;
      if (!diskDataObj.isNew) {
         diskDataObj.originalCapacity = disk.capacity;
      }

      diskDataObj.finalCapacity = disk.capacity;
      return diskDataObj;
   }

   private ComplianceCheckResultObj transformDiskGroupData(VsanDiskGroupComplianceResourceCheck diskgroup) {
      ComplianceCheckResultObj diskGroupDataObj = new ComplianceCheckResultObj();
      diskGroupDataObj.objectType = "Diskgroup";
      List<ComplianceCheckResultObj> diskList = new ArrayList();
      if (diskgroup.ssd != null) {
         ComplianceCheckResultObj ssdObj = new ComplianceCheckResultObj();
         ssdObj.objectType = "SSD";
         ssdObj.uuid = diskgroup.ssd.uuid;
         ssdObj.name = diskgroup.ssd.deviceName;
         ssdObj.initCacheCapacity = diskgroup.ssd.initCapacity;
         ssdObj.finalUsedCacheCapacity = diskgroup.ssd.finalCapacity;
         ssdObj.isNew = diskgroup.ssd.isNew;
         ssdObj.hasChanged = ssdObj.isNew || ssdObj.initCacheCapacity != ssdObj.finalCacheCapacity;
         if (!ssdObj.isNew) {
            ssdObj.originalCacheCapacity = diskgroup.ssd.capacity;
         }

         ssdObj.finalCacheCapacity = diskgroup.ssd.capacity;
         diskList.add(ssdObj);
         diskGroupDataObj.finalUsedCacheCapacity = diskgroup.ssd.finalCapacity;
         diskGroupDataObj.initCacheCapacity = diskgroup.ssd.initCapacity;
         diskGroupDataObj.isNew = diskgroup.ssd.isNew;
         diskGroupDataObj.hasChanged = ssdObj.hasChanged;
         if (!diskGroupDataObj.isNew) {
            diskGroupDataObj.originalCacheCapacity = diskgroup.ssd.capacity;
         }

         diskGroupDataObj.finalCacheCapacity = diskgroup.ssd.capacity;
      }

      if (!ArrayUtils.isEmpty(diskgroup.capacityDevices)) {
         VsanDiskComplianceResourceCheck[] var7;
         int var6 = (var7 = diskgroup.capacityDevices).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VsanDiskComplianceResourceCheck disk = var7[var5];
            ComplianceCheckResultObj diskDataObj = this.transformDiskData(disk);
            diskList.add(diskDataObj);
            diskGroupDataObj.originalCapacity += diskDataObj.originalCapacity;
            diskGroupDataObj.finalCapacity += diskDataObj.finalCapacity;
            diskGroupDataObj.initCapacity += diskDataObj.initCapacity;
            diskGroupDataObj.finalUsedCapacity += diskDataObj.finalUsedCapacity;
            if (!diskGroupDataObj.hasChanged && diskDataObj.hasChanged) {
               diskGroupDataObj.hasChanged = diskDataObj.hasChanged;
            }
         }
      }

      if (diskList.size() > 0) {
         diskGroupDataObj.childDevices = this.parseListToArray(diskList);
      }

      return diskGroupDataObj;
   }

   private ComplianceCheckSummary parseComplianceCheckSummary(List<ComplianceCheckResultObj> fdList) {
      ComplianceCheckSummary resultSummary = new ComplianceCheckSummary();
      if (!CollectionUtils.isEmpty(fdList)) {
         Iterator var4 = fdList.iterator();

         while(var4.hasNext()) {
            ComplianceCheckResultObj fd = (ComplianceCheckResultObj)var4.next();
            resultSummary.newFinalTotalCapacity += fd.finalCapacity;
            resultSummary.newFinalUsedCapacity += fd.finalUsedCapacity;
            resultSummary.originalTotalCapacity += fd.originalCapacity;
            resultSummary.originalUsedCapacity += fd.finalUsedCapacity;
            if (fd.isNew) {
               ++resultSummary.newFaultDomainCount;
            } else {
               ++resultSummary.originalFaultDomainCount;
            }

            if (ArrayUtils.isEmpty(fd.childDevices)) {
               return resultSummary;
            }

            ComplianceCheckResultObj[] var8;
            int var7 = (var8 = fd.childDevices).length;

            for(int var6 = 0; var6 < var7; ++var6) {
               ComplianceCheckResultObj host = var8[var6];
               if (host.isNew) {
                  ++resultSummary.newHostCount;
               } else {
                  ++resultSummary.originalHostCount;
               }

               if (!ArrayUtils.isEmpty(host.childDevices)) {
                  ComplianceCheckResultObj[] var12;
                  int var11 = (var12 = host.childDevices).length;

                  for(int var10 = 0; var10 < var11; ++var10) {
                     ComplianceCheckResultObj diskgroup = var12[var10];
                     if (diskgroup.isNew) {
                        ++resultSummary.newDiskGroupCount;
                        ++resultSummary.newSSDCount;
                     } else {
                        ++resultSummary.originalDiskGroupCount;
                        ++resultSummary.originalSSDCount;
                     }

                     if (!ArrayUtils.isEmpty(diskgroup.childDevices)) {
                        ComplianceCheckResultObj[] var16;
                        int var15 = (var16 = diskgroup.childDevices).length;

                        for(int var14 = 0; var14 < var15; ++var14) {
                           ComplianceCheckResultObj disk = var16[var14];
                           if (!disk.objectType.equals("SSD")) {
                              if (disk.isNew) {
                                 ++resultSummary.newCapacityDeviceCount;
                              } else {
                                 ++resultSummary.originalCapacityDeviceCount;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return resultSummary;
   }

   private ComplianceCheckResultData parseComplianceCheck(VsanFaultDomainComplianceResourceCheck[] faultDomains) {
      if (ArrayUtils.isEmpty(faultDomains)) {
         return null;
      } else {
         List<ComplianceCheckResultObj> fdList = new ArrayList();
         VsanFaultDomainComplianceResourceCheck[] var6 = faultDomains;
         int var5 = faultDomains.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanFaultDomainComplianceResourceCheck faultDomain = var6[var4];
            ComplianceCheckResultObj faultDomainDataObj = this.transformFaultDomainData(faultDomain);
            fdList.add(faultDomainDataObj);
         }

         ComplianceCheckResultData result = new ComplianceCheckResultData();
         result.summary = this.parseComplianceCheckSummary(fdList);
         result.details = this.parseListToArray(fdList);
         return result;
      }
   }

   private ComplianceCheckResultObj transformFaultDomainData(VsanFaultDomainComplianceResourceCheck faultDomain) {
      ComplianceCheckResultObj faultDomainDataObj = new ComplianceCheckResultObj();
      faultDomainDataObj.objectType = "FaultDomain";
      faultDomainDataObj.uuid = faultDomain.uuid;
      faultDomainDataObj.name = faultDomain.fdName;
      faultDomainDataObj.isNew = faultDomain.isNew;
      faultDomainDataObj.hasChanged = faultDomainDataObj.isNew;
      if (!ArrayUtils.isEmpty(faultDomain.hosts)) {
         List<ComplianceCheckResultObj> hostList = new ArrayList();
         VsanHostComplianceResourceCheck[] var7;
         int var6 = (var7 = faultDomain.hosts).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VsanHostComplianceResourceCheck host = var7[var5];
            ComplianceCheckResultObj hostDataObj = this.transformHostData(host);
            hostList.add(hostDataObj);
            faultDomainDataObj.originalCacheCapacity += hostDataObj.originalCacheCapacity;
            faultDomainDataObj.finalCacheCapacity += hostDataObj.finalCacheCapacity;
            faultDomainDataObj.initCacheCapacity += hostDataObj.initCacheCapacity;
            faultDomainDataObj.finalUsedCacheCapacity += hostDataObj.finalUsedCacheCapacity;
            faultDomainDataObj.finalUsedCapacity += hostDataObj.finalUsedCapacity;
            faultDomainDataObj.initCapacity += hostDataObj.initCapacity;
            faultDomainDataObj.originalCapacity += hostDataObj.originalCapacity;
            faultDomainDataObj.finalCapacity += hostDataObj.finalCapacity;
            if (!faultDomainDataObj.hasChanged && hostDataObj.hasChanged) {
               faultDomainDataObj.hasChanged = hostDataObj.hasChanged;
            }
         }

         faultDomainDataObj.childDevices = this.parseListToArray(hostList);
      }

      return faultDomainDataObj;
   }

   private ComplianceCheckResultObj[] parseListToArray(List<ComplianceCheckResultObj> dataList) {
      if (CollectionUtils.isEmpty(dataList)) {
         return null;
      } else {
         ComplianceCheckResultObj[] arr = new ComplianceCheckResultObj[dataList.size()];
         return (ComplianceCheckResultObj[])dataList.toArray(arr);
      }
   }
}
