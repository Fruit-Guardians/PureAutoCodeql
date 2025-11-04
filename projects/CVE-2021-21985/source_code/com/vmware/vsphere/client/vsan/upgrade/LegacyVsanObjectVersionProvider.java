package com.vmware.vsphere.client.vsan.upgrade;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.base.util.multithreading.VsanAsyncQueryUtils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LegacyVsanObjectVersionProvider {
   public static final String HAS_OLD_VSAN_OBJECT = "hasOldVsanObject";
   private static final Log _logger = LogFactory.getLog(LegacyVsanObjectVersionProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(LegacyVsanObjectVersionProvider.class);
   private final VcClient _vcClient;

   public LegacyVsanObjectVersionProvider(VcClient vcClient) {
      this._vcClient = vcClient;
   }

   public boolean getHasOldObject(ManagedObjectReference clusterRef) throws Exception {
      ResultSet resultSet = null;

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            label243: {
               VcConnection vcConnection = this._vcClient.getConnection(clusterRef.getServerGuid());

               try {
                  ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
                  ManagedObjectReference[] hosts = cluster.getHost();
                  if (!ArrayUtils.isEmpty(hosts)) {
                     HashMap<ManagedObjectReference, VsanInternalSystem> vsanInternalSystems = this.getVsanInternalSystems(hosts, vcConnection);
                     List<Callable<VsanAsyncQueryUtils.RequestResult>> requestTasks = getRequestTasks(hosts, vsanInternalSystems);
                     resultSet = VsanAsyncQueryUtils.getProperties(requestTasks);
                     break label243;
                  }
               } finally {
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

               }

               return false;
            }
         } catch (Throwable var18) {
            if (var3 == null) {
               var3 = var18;
            } else if (var3 != var18) {
               var3.addSuppressed(var18);
            }

            throw var3;
         }
      } catch (Exception var19) {
         _logger.error("Failed to retrieve properties from DS. ", var19);
         resultSet = new ResultSet();
         resultSet.error = var19;
         return false;
      }

      if (resultSet != null && ArrayUtils.isNotEmpty(resultSet.items)) {
         ResultItem[] var23;
         int var22 = (var23 = resultSet.items).length;

         for(int var21 = 0; var21 < var22; ++var21) {
            ResultItem item = var23[var21];
            PropertyValue[] var10;
            int var26 = (var10 = item.properties).length;

            for(int var25 = 0; var25 < var26; ++var25) {
               PropertyValue prop = var10[var25];
               if (prop.propertyName == "hasOldVsanObject" && (Boolean)prop.value) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private HashMap<ManagedObjectReference, VsanInternalSystem> getVsanInternalSystems(ManagedObjectReference[] allHosts, VcConnection vcConnection) throws Exception {
      HashMap<ManagedObjectReference, VsanInternalSystem> result = new HashMap();
      if (allHosts != null && allHosts.length != 0) {
         ManagedObjectReference[] var7 = allHosts;
         int var6 = allHosts.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            ManagedObjectReference host = var7[var5];
            VsanInternalSystem vsanInternalSystem = VsanProviderUtils.getVsanInternalSystem(host, vcConnection);
            result.put(host, vsanInternalSystem);
         }

         return result;
      } else {
         return result;
      }
   }

   private static List<Callable<VsanAsyncQueryUtils.RequestResult>> getRequestTasks(ManagedObjectReference[] hosts, HashMap<ManagedObjectReference, VsanInternalSystem> vsanInternalSystems) {
      List<Callable<VsanAsyncQueryUtils.RequestResult>> result = new ArrayList();
      ManagedObjectReference[] var6 = hosts;
      int var5 = hosts.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         final ManagedObjectReference hostRef = var6[var4];
         Callable<VsanAsyncQueryUtils.RequestResult> requestTask = new Callable<VsanAsyncQueryUtils.RequestResult>(vsanInternalSystems) {
            private final VsanInternalSystem vsanInternalSystem;

            {
               this.vsanInternalSystem = (VsanInternalSystem)var1.get(hostRef);
            }

            public VsanAsyncQueryUtils.RequestResult call() {
               return LegacyVsanObjectVersionProvider.executeRequest(hostRef, this.vsanInternalSystem);
            }
         };
         result.add(requestTask);
      }

      return result;
   }

   private static VsanAsyncQueryUtils.RequestResult executeRequest(ManagedObjectReference host, VsanInternalSystem vsanInternalSystem) {
      Exception error = null;
      Boolean result = null;

      try {
         result = getHasOldVsanObjectData(vsanInternalSystem);
      } catch (Exception var5) {
         error = var5;
      }

      if (error != null) {
         _logger.error("Request for DiskMapping for " + host.toString() + " failed.", error);
      }

      return new VsanAsyncQueryUtils.RequestResult(result, error, host, "hasOldVsanObject");
   }

   private static Boolean getHasOldVsanObjectData(VsanInternalSystem vsanInternalSystem) throws Exception {
      if (vsanInternalSystem == null) {
         return false;
      } else {
         int latestVersion = 2;

         for(int version = 0; version < latestVersion; ++version) {
            if (version != 1) {
               String[] objects = null;
               Throwable var4 = null;
               Object var5 = null;

               try {
                  VsanProfiler.Point p = _profiler.point("vsanInternalSystem.queryVsanObjectUuidsByFilter");

                  try {
                     objects = vsanInternalSystem.queryVsanObjectUuidsByFilter((String[])null, 1, version);
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

               if (!ArrayUtils.isEmpty(objects)) {
                  return true;
               }
            }
         }

         return false;
      }
   }
}
