package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.vm.DefinedProfileSpec;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetAuthSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetServiceDefaultConfigSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetServiceSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.config.VsanIscsiConfigEditSpec;
import com.vmware.vsphere.client.vsan.iscsi.utils.VsanIscsiUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanIscsiMutationProvider {
   private static final Log _logger = LogFactory.getLog(VsanIscsiMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiMutationProvider.class);

   @TsService
   public ManagedObjectReference editIscsiConfig(ManagedObjectReference clusterRef, VsanIscsiConfigEditSpec spec) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         Validate.notNull(spec);
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point point = _profiler.point("vsanConfigSystem.reconfigureEx");

            Throwable var10000;
            label262: {
               boolean var10001;
               ManagedObjectReference var22;
               label259: {
                  try {
                     VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
                     VsanIscsiTargetServiceSpec serviceSpec = new VsanIscsiTargetServiceSpec();
                     serviceSpec.setEnabled(spec.enableIscsiTargetService);
                     if (spec.enableIscsiTargetService) {
                        VsanIscsiTargetServiceDefaultConfigSpec serviceConfig = new VsanIscsiTargetServiceDefaultConfigSpec();
                        serviceConfig.setNetworkInterface(spec.network);
                        serviceConfig.setPort(spec.port);
                        serviceConfig.setIscsiTargetAuthSpec(this.createIscsiAuthSpec(spec));
                        serviceSpec.setDefaultConfig(serviceConfig);
                        serviceSpec.setHomeObjectStoragePolicy(this.createPofileSpec(spec));
                     }

                     ReconfigSpec reconfigSpec = new ReconfigSpec();
                     reconfigSpec.iscsiSpec = serviceSpec;
                     ManagedObjectReference taskRef = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
                     if (taskRef != null) {
                        var22 = VsanIscsiUtil.buildTaskMor(taskRef.getValue(), clusterRef.getServerGuid());
                        break label259;
                     }
                  } catch (Throwable var20) {
                     var10000 = var20;
                     var10001 = false;
                     break label262;
                  }

                  if (point != null) {
                     point.close();
                  }

                  return null;
               }

               if (point != null) {
                  point.close();
               }

               label239:
               try {
                  return var22;
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label239;
               }
            }

            var3 = var10000;
            if (point != null) {
               point.close();
            }

            throw var3;
         } catch (Throwable var21) {
            if (var3 == null) {
               var3 = var21;
            } else if (var3 != var21) {
               var3.addSuppressed(var21);
            }

            throw var3;
         }
      }
   }

   private DefinedProfileSpec createPofileSpec(VsanIscsiConfigEditSpec spec) {
      DefinedProfileSpec profileSpec = null;
      if (spec.policy != null) {
         profileSpec = new DefinedProfileSpec();
         profileSpec.setProfileId(spec.policy.id);
      }

      return profileSpec;
   }

   private VsanIscsiTargetAuthSpec createIscsiAuthSpec(VsanIscsiConfigEditSpec spec) {
      if (spec.authSpec == null) {
         return null;
      } else {
         VsanIscsiTargetAuthSpec authSpec = new VsanIscsiTargetAuthSpec();
         authSpec.setAuthType(spec.authSpec.authType);
         authSpec.setUserNameAttachToInitiator(spec.authSpec.initiatorUsername);
         authSpec.setUserNameAttachToTarget(spec.authSpec.targetUsername);
         authSpec.setUserSecretAttachToInitiator(spec.authSpec.initiatorSecret);
         authSpec.setUserSecretAttachToTarget(spec.authSpec.targetSecret);
         return authSpec;
      }
   }
}
