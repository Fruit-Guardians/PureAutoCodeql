package com.vmware.vsphere.client.vsan.support;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Task;
import com.vmware.vim.binding.vim.TaskInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanAttachToSrOperation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanSupportPropertyProvider {
   private static final Log _logger = LogFactory.getLog(VsanSupportPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanSupportPropertyProvider.class);
   private final VcClient _vcClient;

   public VsanSupportPropertyProvider(VcClient vcClient) {
      this._vcClient = vcClient;
   }

   @TsService
   public VsanAttachToSrOperation getVsanSRLastOperation(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      VsanAttachToSrOperation[] history = null;
      Throwable var4 = null;
      VsanAttachToSrOperation sr = null;

      try {
         VsanProfiler.Point p = _profiler.point("healthSystem.queryAttachToSrHistory");

         try {
            history = healthSystem.queryAttachToSrHistory(clusterRef, 10, (String)null);
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var44) {
         if (var4 == null) {
            var4 = var44;
         } else if (var4 != var44) {
            var4.addSuppressed(var44);
         }

         throw var4;
      }

      if (history != null && history.length > 0) {
         for(int itr = history.length - 1; itr >= 0; --itr) {
            sr = history[itr];
            String srNumber = sr.getSrNumber();
            if (!StringUtils.isBlank(srNumber) && !srNumber.toLowerCase().contains("pr")) {
               if (sr.task != null) {
                  sr.task.setServerGuid(clusterRef.getServerGuid());
               }

               Throwable var7 = null;
               Object var8 = null;

               try {
                  VcConnection vcConnection = this._vcClient.getConnection(clusterRef.getServerGuid());

                  Throwable var10000;
                  label666: {
                     boolean var10001;
                     VsanAttachToSrOperation var50;
                     try {
                        Task task = (Task)vcConnection.createStub(Task.class, sr.task);
                        if (task != null) {
                           try {
                              TaskInfo taskInfo = task.getInfo();
                              if (taskInfo != null && taskInfo.progress == 100) {
                                 sr.task = null;
                              }
                           } catch (Exception var42) {
                              sr.task = null;
                           }
                        }

                        var50 = sr;
                     } catch (Throwable var46) {
                        var10000 = var46;
                        var10001 = false;
                        break label666;
                     }

                     if (vcConnection != null) {
                        vcConnection.close();
                     }

                     label636:
                     try {
                        return var50;
                     } catch (Throwable var45) {
                        var10000 = var45;
                        var10001 = false;
                        break label636;
                     }
                  }

                  var7 = var10000;
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  throw var7;
               } catch (Throwable var47) {
                  if (var7 == null) {
                     var7 = var47;
                  } else if (var7 != var47) {
                     var7.addSuppressed(var47);
                  }

                  throw var7;
               }
            }
         }
      }

      _logger.info("There is no sr uploaded for this cluster");
      return null;
   }
}
