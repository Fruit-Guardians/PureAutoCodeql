package com.vmware.vsphere.client.vsan.perf;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.DefinedProfileSpec;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfTimeRange;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerformanceManager;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfsvcConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.perf.model.PerfStatesObjSpec;
import com.vmware.vsphere.client.vsan.perf.model.PerfTimeRangeData;
import java.util.Calendar;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanPerfMutationProvider {
   private static final Log _logger = LogFactory.getLog(VsanPerfMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanPerfMutationProvider.class);

   @TsService
   public ManagedObjectReference disablePerfService(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference taskRef = null;
      boolean isPerfSvcAutoConfigSupported = VsanCapabilityUtils.isPerfSvcAutoConfigSupportedOnVc(clusterRef);
      Throwable var5;
      VsanProfiler.Point p;
      if (isPerfSvcAutoConfigSupported) {
         VsanVcClusterConfigSystem vsanClusterConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         var5 = null;
         p = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanClusterConfigSystem.reconfigureEx");

            try {
               ReconfigSpec spec = this.getClusterReconfigSpecForPerfService(clusterRef, (String)null, false);
               VsanCapabilityData vcCapabilities = VsanCapabilityUtils.getVcCapabilities(clusterRef);
               if (vcCapabilities.isPerfDiagnosticModeSupported) {
                  spec.perfsvcConfig.diagnosticMode = false;
               }

               if (vcCapabilities.isVerboseModeInClusterConfigurationSupported) {
                  spec.perfsvcConfig.verboseMode = false;
               } else if (vcCapabilities.isPerfVerboseModeSupported) {
                  this.toggleVerboseMode(clusterRef, false);
               }

               taskRef = vsanClusterConfigSystem.reconfigureEx(clusterRef, spec);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var29) {
            if (var5 == null) {
               var5 = var29;
            } else if (var5 != var29) {
               var5.addSuppressed(var29);
            }

            throw var5;
         }
      } else {
         Throwable var30 = null;
         var5 = null;

         try {
            p = _profiler.point("perfMgr.deleteStatsObjectTask");

            try {
               VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
               taskRef = perfMgr.deleteStatsObjectTask(clusterRef);
            } finally {
               if (p != null) {
                  p.close();
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

      return taskRef != null ? new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), clusterRef.getServerGuid()) : null;
   }

   @TsService
   public ManagedObjectReference enablePerfService(PerfStatesObjSpec spec) throws Exception {
      ManagedObjectReference taskRef = null;
      boolean isPerfSvcAutoConfigSupported = VsanCapabilityUtils.isPerfSvcAutoConfigSupportedOnVc(spec.clusterRef);
      Throwable var5;
      Object var6;
      VsanProfiler.Point p;
      if (isPerfSvcAutoConfigSupported) {
         VsanVcClusterConfigSystem vsanClusterConfigSystem = VsanProviderUtils.getVsanConfigSystem(spec.clusterRef);
         var5 = null;
         var6 = null;

         try {
            p = _profiler.point("vsanClusterConfigSystem.reconfigureEx");

            try {
               taskRef = vsanClusterConfigSystem.reconfigureEx(spec.clusterRef, this.getClusterReconfigSpecForPerfService(spec.clusterRef, spec.profileId, true));
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var28) {
            if (var5 == null) {
               var5 = var28;
            } else if (var5 != var28) {
               var5.addSuppressed(var28);
            }

            throw var5;
         }
      } else {
         VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(spec.clusterRef);
         var5 = null;
         var6 = null;

         try {
            p = _profiler.point("perfMgr.createStatsObjectTask");

            try {
               DefinedProfileSpec definedSpec = new DefinedProfileSpec();
               definedSpec.setProfileId(spec.profileId);
               taskRef = perfMgr.createStatsObjectTask(spec.clusterRef, definedSpec);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var26) {
            if (var5 == null) {
               var5 = var26;
            } else if (var5 != var26) {
               var5.addSuppressed(var26);
            }

            throw var5;
         }
      }

      return taskRef != null ? new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), spec.clusterRef.getServerGuid()) : null;
   }

   private ReconfigSpec getClusterReconfigSpecForPerfService(ManagedObjectReference clusterRef, String profileId, boolean enabled) throws Exception {
      DefinedProfileSpec definedSpec = new DefinedProfileSpec();
      definedSpec.setProfileId(profileId);
      DefinedProfileSpec profileSpec = StringUtils.isBlank(profileId) ? null : definedSpec;
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      VsanPerfsvcConfig perfConfig = null;
      Throwable var8 = null;
      Object var9 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanConfigSystem.getConfigInfoEx");

         try {
            perfConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).perfsvcConfig;
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var16) {
         if (var8 == null) {
            var8 = var16;
         } else if (var8 != var16) {
            var8.addSuppressed(var16);
         }

         throw var8;
      }

      if (perfConfig == null) {
         perfConfig = new VsanPerfsvcConfig();
      }

      perfConfig.profile = profileSpec;
      perfConfig.enabled = enabled;
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.perfsvcConfig = perfConfig;
      return reconfigSpec;
   }

   @TsService
   public ManagedObjectReference editPerfConfiguration(PerfStatesObjSpec spec) throws Exception {
      Validate.notNull(spec.clusterRef);
      ManagedObjectReference taskRef = null;
      boolean isPerfSvcAutoConfigSupported = VsanCapabilityUtils.isPerfSvcAutoConfigSupportedOnVc(spec.clusterRef);
      DefinedProfileSpec profileSpec;
      ReconfigSpec configSpec;
      if (isPerfSvcAutoConfigSupported) {
         VsanVcClusterConfigSystem vsanClusterConfigSystem = VsanProviderUtils.getVsanConfigSystem(spec.clusterRef);
         Throwable var5 = null;
         profileSpec = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanClusterConfigSystem.reconfigureEx");

            try {
               configSpec = this.getClusterReconfigSpecForPerfService(spec.clusterRef, spec.profileId, true);
               if (VsanCapabilityUtils.isPerfDiagnosticModeSupported(spec.clusterRef) || configSpec.perfsvcConfig.diagnosticMode != null) {
                  configSpec.perfsvcConfig.diagnosticMode = spec.isNetworkDiagnosticModeEnabled;
               }

               if (VsanCapabilityUtils.isVerboseModeInClusterConfigurationSupported(spec.clusterRef)) {
                  configSpec.perfsvcConfig.verboseMode = spec.isVerboseEnabled;
               } else {
                  this.toggleVerboseMode(spec.clusterRef, spec.isVerboseEnabled);
               }

               taskRef = vsanClusterConfigSystem.reconfigureEx(spec.clusterRef, configSpec);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var29) {
            if (var5 == null) {
               var5 = var29;
            } else if (var5 != var29) {
               var5.addSuppressed(var29);
            }

            throw var5;
         }
      } else {
         VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(spec.clusterRef);
         DefinedProfileSpec definedSpec = new DefinedProfileSpec();
         definedSpec.setProfileId(spec.profileId);
         profileSpec = spec.profileId == null ? null : definedSpec;
         Throwable var32 = null;
         configSpec = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.setStatsObjectPolicy");

            try {
               perfMgr.setStatsObjectPolicy(spec.clusterRef, profileSpec);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var27) {
            if (var32 == null) {
               var32 = var27;
            } else if (var32 != var27) {
               var32.addSuppressed(var27);
            }

            throw var32;
         }
      }

      return taskRef != null ? new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), spec.clusterRef.getServerGuid()) : null;
   }

   public void toggleVerboseMode(ManagedObjectReference clusterRef, boolean enableVerboseMode) throws Exception {
      if (VsanCapabilityUtils.isPerfVerboseModeSupportedOnVc(clusterRef)) {
         VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.toggleVerboseMode");

            try {
               perfMgr.toggleVerboseMode(clusterRef, enableVerboseMode);
            } finally {
               if (p != null) {
                  p.close();
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
      }

   }

   @TsService
   public void setTimeRanges(PerfTimeRangeData range) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("perfMgr.saveTimeRanges");

         try {
            VsanPerfTimeRange rangeObj = new VsanPerfTimeRange();
            rangeObj.name = range.name;
            rangeObj.startTime = Calendar.getInstance();
            BaseUtils.setUTCTimeZone(rangeObj.startTime);
            rangeObj.startTime.setTime(range.from);
            rangeObj.endTime = Calendar.getInstance();
            BaseUtils.setUTCTimeZone(rangeObj.endTime);
            rangeObj.endTime.setTime(range.to);
            VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(range.clusterRef);
            perfMgr.saveTimeRanges(range.clusterRef, new VsanPerfTimeRange[]{rangeObj});
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var12) {
         if (var2 == null) {
            var2 = var12;
         } else if (var2 != var12) {
            var2.addSuppressed(var12);
         }

         throw var2;
      }
   }

   @TsService
   public void deleteTimeRange(ManagedObjectReference clusterRef, PerfTimeRangeData range) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("perfMgr.deleteTimeRange");

         try {
            VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
            perfMgr.deleteTimeRange(clusterRef, range.name);
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var12) {
         if (var3 == null) {
            var3 = var12;
         } else if (var3 != var12) {
            var3.addSuppressed(var12);
         }

         throw var3;
      }
   }
}
