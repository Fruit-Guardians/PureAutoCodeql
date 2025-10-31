package com.vmware.vsphere.client.vsan.health;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.VsanUpgradeSystemEx;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthConfigs;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultKeyValuePair;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterMgmtInternalSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterTelemetryProxyConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanDiskFormatConversionSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

public class VsanHealthMutationProvider {
   private static final VsanProfiler _profiler = new VsanProfiler(VsanHealthMutationProvider.class);

   @TsService
   public ManagedObjectReference prepareCluster(VsanHealthServiceSpec spec) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.prepareCluster");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var20;
               try {
                  Validate.notNull(spec);
                  Validate.notNull(spec.clusterRef);
                  ManagedObjectReference clusterRef = spec.clusterRef;
                  VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
                  ManagedObjectReference taskRef = healthSystem.prepareCluster(clusterRef, (String)null);
                  if (taskRef == null) {
                     break label217;
                  }

                  var20 = VsanHealthUtil.buildTaskMor(taskRef.getValue(), clusterRef.getServerGuid());
               } catch (Throwable var18) {
                  var10000 = var18;
                  var10001 = false;
                  break label219;
               }

               if (p != null) {
                  p.close();
               }

               label203:
               try {
                  return var20;
               } catch (Throwable var17) {
                  var10000 = var17;
                  var10001 = false;
                  break label203;
               }
            }

            var2 = var10000;
            if (p != null) {
               p.close();
            }

            throw var2;
         }

         if (p != null) {
            p.close();
         }

         return null;
      } catch (Throwable var19) {
         if (var2 == null) {
            var2 = var19;
         } else if (var2 != var19) {
            var2.addSuppressed(var19);
         }

         throw var2;
      }
   }

   @TsService
   public ManagedObjectReference uninstallCluster(ManagedObjectReference entity, VsanHealthServiceSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.uninstallCluster");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var21;
               try {
                  Validate.notNull(spec);
                  Validate.notNull(spec.clusterRef);
                  ManagedObjectReference clusterRef = spec.clusterRef;
                  VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
                  ManagedObjectReference taskRef = healthSystem.uninstallCluster(clusterRef, (String)null);
                  if (taskRef == null) {
                     break label217;
                  }

                  var21 = VsanHealthUtil.buildTaskMor(taskRef.getValue(), clusterRef.getServerGuid());
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label219;
               }

               if (p != null) {
                  p.close();
               }

               label203:
               try {
                  return var21;
               } catch (Throwable var18) {
                  var10000 = var18;
                  var10001 = false;
                  break label203;
               }
            }

            var3 = var10000;
            if (p != null) {
               p.close();
            }

            throw var3;
         }

         if (p != null) {
            p.close();
         }

         return null;
      } catch (Throwable var20) {
         if (var3 == null) {
            var3 = var20;
         } else if (var3 != var20) {
            var3.addSuppressed(var20);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference repairClusterObjectsImmediate(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.repairClusterObjectsImmediate");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var19;
            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               ManagedObjectReference taskRef = healthSystem.repairClusterObjectsImmediate(clusterRef, (String[])null);
               taskRef.setServerGuid(clusterRef.getServerGuid());
               var19 = taskRef;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
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

         var2 = var10000;
         if (p != null) {
            p.close();
         }

         throw var2;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }

   @TsService
   public void setTelementryConfig(ManagedObjectReference entity, ExternalProxySettingsConfig config) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.setVsanClusterTelemetryConfig");

         try {
            Validate.notNull(entity);
            Validate.notNull(config);
            VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(entity);
            VsanClusterHealthConfigs newConfigs = new VsanClusterHealthConfigs();
            if (!StringUtils.isBlank(config.hostName) && !StringUtils.isBlank(String.valueOf(config.port))) {
               VsanClusterTelemetryProxyConfig vsanTelemetryProxy = new VsanClusterTelemetryProxyConfig();
               vsanTelemetryProxy.setHost(config.hostName);
               vsanTelemetryProxy.setPort(config.port);
               vsanTelemetryProxy.setUser(config.userName);
               vsanTelemetryProxy.setPassword(config.password);
               newConfigs.setVsanTelemetryProxy(vsanTelemetryProxy);
            }

            newConfigs.setConfigs(new VsanClusterHealthResultKeyValuePair[]{new VsanClusterHealthResultKeyValuePair("enableInternetAccess", String.valueOf(Boolean.TRUE.equals(config.enableInternetAccess)))});
            healthSystem.setVsanClusterTelemetryConfig(entity, newConfigs);
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var14) {
         if (var3 == null) {
            var3 = var14;
         } else if (var3 != var14) {
            var3.addSuppressed(var14);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference rebalanceCluster(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.rebalanceCluster");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var19;
            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               ManagedObjectReference taskRef = healthSystem.rebalanceCluster(clusterRef, (ManagedObjectReference[])null);
               VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
               var19 = taskRef;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
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

         var2 = var10000;
         if (p != null) {
            p.close();
         }

         throw var2;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }

   @TsService
   public ManagedObjectReference stopRebalanceCluster(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.stopRebalanceCluster");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var19;
            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               ManagedObjectReference taskRef = healthSystem.stopRebalanceCluster(clusterRef, (ManagedObjectReference[])null);
               VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
               var19 = taskRef;
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
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

         var2 = var10000;
         if (p != null) {
            p.close();
         }

         throw var2;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }

   @TsService
   public void clearTelementryConfig(ManagedObjectReference entity, ExternalProxySettingsConfig spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.setVsanClusterTelemetryConfig");

         try {
            Validate.notNull(entity);
            VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(entity);
            VsanClusterHealthConfigs newConfigs = new VsanClusterHealthConfigs();
            newConfigs.setVsanTelemetryProxy(new VsanClusterTelemetryProxyConfig());
            healthSystem.setVsanClusterTelemetryConfig(entity, newConfigs);
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var13) {
         if (var3 == null) {
            var3 = var13;
         } else if (var3 != var13) {
            var3.addSuppressed(var13);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference performUpgrade(ManagedObjectReference entity, VsanConvertDiskFormatSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("upgradeSystemEx.performUpgrade");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var20;
            try {
               VsanUpgradeSystemEx upgradeSystem = VsanProviderUtils.getVsanUpgradeSystemEx(entity);
               ManagedObjectReference taskRef = upgradeSystem.performUpgrade(entity, (Boolean)null, (Boolean)null, spec.allowReducedRedundancy, (ManagedObjectReference[])null, (VsanDiskFormatConversionSpec)null);
               VmodlHelper.assignServerGuid(taskRef, entity.getServerGuid());
               var20 = taskRef;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
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
         if (p != null) {
            p.close();
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
   public ManagedObjectReference remediateCluster(ManagedObjectReference entity) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = _profiler.point("VsanClusterMgmtInternalSystem.remediateVsanCluster");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var19;
               try {
                  VsanClusterMgmtInternalSystem system = VsanProviderUtils.getVsanClusterMgmtInternalSystem(entity);
                  ManagedObjectReference taskRef = system.remediateVsanCluster(entity);
                  if (taskRef == null) {
                     break label217;
                  }

                  var19 = VsanHealthUtil.buildTaskMor(taskRef.getValue(), entity.getServerGuid());
               } catch (Throwable var17) {
                  var10000 = var17;
                  var10001 = false;
                  break label219;
               }

               if (p != null) {
                  p.close();
               }

               label203:
               try {
                  return var19;
               } catch (Throwable var16) {
                  var10000 = var16;
                  var10001 = false;
                  break label203;
               }
            }

            var2 = var10000;
            if (p != null) {
               p.close();
            }

            throw var2;
         }

         if (p != null) {
            p.close();
         }

         return null;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }
}
