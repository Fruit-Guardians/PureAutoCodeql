package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiInitiatorGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetBasicInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.InitiatorGroup;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.initiator.InitiatorGroupInitiator;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanIscsiInitiatorGroupPropertyProvider {
   private static final Log _logger = LogFactory.getLog(VsanIscsiInitiatorGroupPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiInitiatorGroupPropertyProvider.class);

   @TsService
   public InitiatorGroup[] getVsanIscsiInitiatorGroupList(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiInitiatorGroup[] initiatorGroups = null;
         ArrayList groups = new ArrayList();

         String errorMsg;
         try {
            Throwable var5 = null;
            errorMsg = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiInitiatorGroups");

               try {
                  initiatorGroups = vsanIscsiSystem.getIscsiInitiatorGroups(clusterRef);
                  if (ArrayUtils.isNotEmpty(initiatorGroups)) {
                     VsanIscsiInitiatorGroup[] var11 = initiatorGroups;
                     int var10 = initiatorGroups.length;

                     for(int var9 = 0; var9 < var10; ++var9) {
                        VsanIscsiInitiatorGroup group = var11[var9];
                        groups.add(new InitiatorGroup(group));
                     }
                  }
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
         } catch (Exception var20) {
            errorMsg = Utils.getMethodFault(var20).getMessage();
            if (!StringUtils.isBlank(errorMsg) && errorMsg.indexOf("vSAN iSCSI Target Service is not enabled or the enable task is in progress.") == -1) {
               Exception ex = new Exception(var20.getLocalizedMessage(), var20);
               throw ex;
            }

            _logger.info("iscsi targets service enabling in progress, ignore the error");
         }

         return (InitiatorGroup[])groups.toArray(new InitiatorGroup[0]);
      }
   }

   @TsService
   public VsanIscsiInitiatorGroup getVsanIscsiInitiatorGroup(ManagedObjectReference clusterRef, String name) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiInitiatorGroup initiatorGroup = null;
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiInitiatorGroup");

            try {
               initiatorGroup = vsanIscsiSystem.getIscsiInitiatorGroup(clusterRef, name);
            } finally {
               if (p != null) {
                  p.close();
               }

            }

            return initiatorGroup;
         } catch (Throwable var13) {
            if (var5 == null) {
               var5 = var13;
            } else if (var5 != var13) {
               var5.addSuppressed(var13);
            }

            throw var5;
         }
      }
   }

   @TsService
   public InitiatorGroupInitiator[] getInitiatorGroupInitiatorList(ManagedObjectReference clusterRef, String initiatorGroupIqn) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiInitiatorGroup vsanIscsiInitiatorGroup = null;
         Throwable var5 = null;
         String[] initiatorIqns = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiInitiatorGroup");

            try {
               vsanIscsiInitiatorGroup = vsanIscsiSystem.getIscsiInitiatorGroup(clusterRef, initiatorGroupIqn);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var14) {
            if (var5 == null) {
               var5 = var14;
            } else if (var5 != var14) {
               var5.addSuppressed(var14);
            }

            throw var5;
         }

         InitiatorGroupInitiator[] initiators = null;
         if (vsanIscsiInitiatorGroup != null) {
            initiatorIqns = vsanIscsiInitiatorGroup.getInitiators();
            if (ArrayUtils.isNotEmpty(initiatorIqns)) {
               initiators = new InitiatorGroupInitiator[initiatorIqns.length];

               for(int i = 0; i < initiatorIqns.length; ++i) {
                  InitiatorGroupInitiator initiator = new InitiatorGroupInitiator();
                  initiator.name = initiatorIqns[i];
                  initiators[i] = initiator;
               }
            }
         }

         return initiators;
      }
   }

   @TsService
   public VsanIscsiTargetBasicInfo[] getVsanIscsiInitiatorGroupTargetList(ManagedObjectReference clusterRef, String initiatorGroupIqn) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiInitiatorGroup vsanIscsiInitiatorGroup = null;
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiInitiatorGroup");

            try {
               vsanIscsiInitiatorGroup = vsanIscsiSystem.getIscsiInitiatorGroup(clusterRef, initiatorGroupIqn);
            } finally {
               if (p != null) {
                  p.close();
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

         return vsanIscsiInitiatorGroup.getTargets();
      }
   }
}
