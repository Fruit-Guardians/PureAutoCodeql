package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiInitiatorGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.InitiatorGroupAdditionSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.initiator.InitiatorGroupInitiatorAdditionSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.initiator.InitiatorGroupInitiatorRemoveSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.target.InitiatorGroupTargetAdditionSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.target.InitiatorGroupTargetRemoveSpec;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanIscsiInitiatorGroupMutationProvider {
   private static final Log _logger = LogFactory.getLog(VsanIscsiInitiatorGroupMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiInitiatorGroupMutationProvider.class);

   @TsService
   public void createInitiatorGroup(ManagedObjectReference clusterRef, InitiatorGroupAdditionSpec spec) throws Exception {
      Validate.notNull(spec);
      Validate.notEmpty(spec.initiatorGroupName);
      VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      VsanProfiler.Point p;
      try {
         p = _profiler.point("vsanIscsiSystem.addIscsiInitiatorGroup");

         try {
            vsanIscsiSystem.addIscsiInitiatorGroup(clusterRef, spec.initiatorGroupName);
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var24) {
         if (var4 == null) {
            var4 = var24;
         } else if (var4 != var24) {
            var4.addSuppressed(var24);
         }

         throw var4;
      }

      if (ArrayUtils.isNotEmpty(spec.initiatorNames)) {
         var4 = null;
         var5 = null;

         try {
            p = _profiler.point("vsanIscsiSystem.addIscsiInitiatorsToGroup");

            try {
               vsanIscsiSystem.addIscsiInitiatorsToGroup(clusterRef, spec.initiatorGroupName, spec.initiatorNames);
            } finally {
               if (p != null) {
                  p.close();
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
      }

   }

   @TsService
   public void removeInitiatorGroup(ManagedObjectReference clusterRef, String name) throws Exception {
      Validate.notEmpty(name);
      VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
      VsanIscsiInitiatorGroup vsanIscsiInitiatorGroup = null;
      Throwable var5 = null;
      Throwable var6 = null;

      VsanProfiler.Point p;
      try {
         p = _profiler.point("vsanIscsiSystem.getIscsiInitiatorGroup");

         try {
            vsanIscsiInitiatorGroup = vsanIscsiSystem.getIscsiInitiatorGroup(clusterRef, name);
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var48) {
         if (var5 == null) {
            var5 = var48;
         } else if (var5 != var48) {
            var5.addSuppressed(var48);
         }

         throw var5;
      }

      if (vsanIscsiInitiatorGroup != null) {
         String[] initiatorIqns = vsanIscsiInitiatorGroup.getInitiators();
         if (ArrayUtils.isNotEmpty(initiatorIqns)) {
            var6 = null;
            p = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.removeIscsiInitiatorsFromGroup");

               try {
                  vsanIscsiSystem.removeIscsiInitiatorsFromGroup(clusterRef, name, initiatorIqns);
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var46) {
               if (var6 == null) {
                  var6 = var46;
               } else if (var6 != var46) {
                  var6.addSuppressed(var46);
               }

               throw var6;
            }
         }
      }

      var5 = null;
      var6 = null;

      try {
         p = _profiler.point("vsanIscsiSystem.removeIscsiInitiatorGroup");

         try {
            vsanIscsiSystem.removeIscsiInitiatorGroup(clusterRef, name);
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var50) {
         if (var5 == null) {
            var5 = var50;
         } else if (var5 != var50) {
            var5.addSuppressed(var50);
         }

         throw var5;
      }
   }

   @TsService
   public void addInitiators(ManagedObjectReference clusterRef, InitiatorGroupInitiatorAdditionSpec spec) throws Exception {
      Validate.notNull(spec);
      Validate.notEmpty(spec.initiatorNames);
      Validate.notEmpty(spec.initiatorGroupName);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.addIscsiInitiatorsToGroup");

         try {
            VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
            vsanIscsiSystem.addIscsiInitiatorsToGroup(clusterRef, spec.initiatorGroupName, spec.initiatorNames);
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

   @TsService
   public void removeInitiator(ManagedObjectReference clusterRef, InitiatorGroupInitiatorRemoveSpec spec) throws Exception {
      if (VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         Validate.notNull(spec);
         Validate.notEmpty(spec.initiatorGroupName);
         Validate.notEmpty(spec.initiatorName);
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.removeIscsiInitiatorsFromGroup");

            try {
               VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
               vsanIscsiSystem.removeIscsiInitiatorsFromGroup(clusterRef, spec.initiatorGroupName, new String[]{spec.initiatorName});
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

   @TsService
   public void addTarget(ManagedObjectReference clusterRef, InitiatorGroupTargetAdditionSpec spec) throws Exception {
      if (VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         Validate.notNull(spec);
         Validate.notNull(spec.targetAliases);
         Validate.notEmpty(spec.initiatorGroupName);
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.addIscsiTargetToGroup");

            try {
               VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
               String initiatorGroupIqn = spec.initiatorGroupName;
               String[] var11;
               int var10 = (var11 = spec.targetAliases).length;

               for(int var9 = 0; var9 < var10; ++var9) {
                  String targetAlias = var11[var9];
                  vsanIscsiSystem.addIscsiTargetToGroup(clusterRef, initiatorGroupIqn, targetAlias);
               }
            } finally {
               if (p != null) {
                  p.close();
               }

            }

         } catch (Throwable var17) {
            if (var3 == null) {
               var3 = var17;
            } else if (var3 != var17) {
               var3.addSuppressed(var17);
            }

            throw var3;
         }
      }
   }

   @TsService
   public void removeTarget(ManagedObjectReference clusterRef, InitiatorGroupTargetRemoveSpec spec) throws Exception {
      if (VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         Validate.notNull(spec);
         Validate.notEmpty(spec.initiatorGroupName);
         Validate.notEmpty(spec.targetAlias);
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.removeIscsiTargetFromGroup");

            try {
               VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
               vsanIscsiSystem.removeIscsiTargetFromGroup(clusterRef, spec.initiatorGroupName, spec.targetAlias);
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
}
