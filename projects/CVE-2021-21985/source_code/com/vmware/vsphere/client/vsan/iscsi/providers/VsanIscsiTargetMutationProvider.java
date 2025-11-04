package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.fault.VsanFault;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.target.TargetOperatoinSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.target.initiator.TargetInitiatorEditSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.target.initiator.TargetInitiatorRemoveSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.target.lun.LunOperationSpec;
import com.vmware.vsphere.client.vsan.iscsi.models.target.lun.TargetLunRemoveSpec;
import com.vmware.vsphere.client.vsan.iscsi.utils.VsanIscsiUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanIscsiTargetMutationProvider {
   private static final Log _logger = LogFactory.getLog(VsanIscsiTargetMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiTargetMutationProvider.class);

   @TsService
   public ManagedObjectReference addTarget(ManagedObjectReference clusterRef, TargetOperatoinSpec spec) throws Exception {
      Validate.notNull(spec);
      this.validateTargetIQN(clusterRef, spec.iqn);
      this.validateTargetAlias(clusterRef, spec.alias);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.addIscsiTarget");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var20;
               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  ManagedObjectReference taskRef = vsanIscsiSystem.addIscsiTarget(clusterRef, spec.toVmodlVsanIscsiTargetSpec());
                  if (taskRef == null) {
                     break label217;
                  }

                  VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
                  var20 = taskRef;
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
      } catch (Throwable var19) {
         if (var3 == null) {
            var3 = var19;
         } else if (var3 != var19) {
            var3.addSuppressed(var19);
         }

         throw var3;
      }
   }

   private void validateTargetIQN(ManagedObjectReference clusterRef, String iqn) throws Exception {
      if (!StringUtils.isEmpty(StringUtils.trim(iqn))) {
         VsanIscsiTarget[] targets = null;

         try {
            Throwable var4 = null;
            Object var5 = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiTargets");

               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  targets = vsanIscsiSystem.getIscsiTargets(clusterRef);
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
            _logger.error("Failed to get the vSAN iSCSI target list.", var16);
            throw new Exception(var16.getLocalizedMessage(), var16);
         }

         if (targets != null) {
            VsanIscsiTarget[] var20 = targets;
            int var19 = targets.length;

            for(int var18 = 0; var18 < var19; ++var18) {
               VsanIscsiTarget existingTarget = var20[var18];
               if (existingTarget != null && iqn.equalsIgnoreCase(existingTarget.iqn)) {
                  throw new Exception(Utils.getLocalizedString("error.target.iqn.duplicated", iqn));
               }
            }
         }

      }
   }

   private void validateTargetAlias(ManagedObjectReference clusterRef, String newTargetAlias) throws Exception {
      VsanIscsiTarget target = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiTarget");

            try {
               VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
               target = vsanIscsiSystem.getIscsiTarget(clusterRef, newTargetAlias);
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
      }

      if (target != null) {
         throw new Exception(Utils.getLocalizedString("error.target.alias.duplicated", newTargetAlias));
      }
   }

   @TsService
   public ManagedObjectReference editTarget(ManagedObjectReference clusterRef, TargetOperatoinSpec spec) throws Exception {
      Validate.notNull(spec);
      if (StringUtils.isNotEmpty(StringUtils.trim(spec.newAlias))) {
         this.validateTargetAlias(clusterRef, spec.newAlias);
      }

      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.editIscsiTarget");

         Throwable var10000;
         label237: {
            label239: {
               boolean var10001;
               ManagedObjectReference var20;
               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  ManagedObjectReference taskRef = vsanIscsiSystem.editIscsiTarget(clusterRef, spec.toVmodlVsanIscsiTargetSpec());
                  if (taskRef == null) {
                     break label239;
                  }

                  VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
                  var20 = taskRef;
               } catch (Throwable var18) {
                  var10000 = var18;
                  var10001 = false;
                  break label237;
               }

               if (p != null) {
                  p.close();
               }

               try {
                  return var20;
               } catch (Throwable var17) {
                  var10000 = var17;
                  var10001 = false;
                  break label237;
               }
            }

            if (p != null) {
               p.close();
            }

            return null;
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
   public List<ManagedObjectReference> batchPolicyReapply(ManagedObjectReference clusterRef, TargetOperatoinSpec[] specs) throws Exception {
      Validate.notEmpty(specs);
      List<ManagedObjectReference> tasksList = new ArrayList();
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.editIscsiTarget");

         try {
            VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
            TargetOperatoinSpec[] var11 = specs;
            int var10 = specs.length;

            for(int var9 = 0; var9 < var10; ++var9) {
               TargetOperatoinSpec spec = var11[var9];

               try {
                  ManagedObjectReference taskRef = vsanIscsiSystem.editIscsiTarget(clusterRef, spec.toVmodlVsanIscsiTargetSpec());
                  if (taskRef != null) {
                     tasksList.add(VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid()));
                  }
               } catch (Exception var19) {
                  _logger.error(var19);
               }
            }
         } finally {
            if (p != null) {
               p.close();
            }

         }

         return tasksList;
      } catch (Throwable var21) {
         if (var4 == null) {
            var4 = var21;
         } else if (var4 != var21) {
            var4.addSuppressed(var21);
         }

         throw var4;
      }
   }

   @TsService
   public ManagedObjectReference removeTarget(ManagedObjectReference clusterRef, String targetAlias) throws Exception {
      Validate.notEmpty(targetAlias);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("Remove an existing vSAN iSCSI target.");

         Throwable var10000;
         label569: {
            ManagedObjectReference var48;
            boolean var10001;
            label567: {
               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  VsanIscsiLUN[] luns = null;

                  Exception ex;
                  try {
                     Throwable var8 = null;
                     ex = null;

                     try {
                        VsanProfiler.Point sp = _profiler.point("vsanIscsiSystem.getIscsiLUNs");

                        try {
                           luns = vsanIscsiSystem.getIscsiLUNs(clusterRef, new String[]{targetAlias});
                        } finally {
                           if (sp != null) {
                              sp.close();
                           }

                        }
                     } catch (Throwable var42) {
                        if (var8 == null) {
                           var8 = var42;
                        } else if (var8 != var42) {
                           var8.addSuppressed(var42);
                        }

                        throw var8;
                     }
                  } catch (VsanFault var43) {
                     ex = new Exception(var43.getLocalizedMessage(), var43.getCause());
                     throw ex;
                  }

                  if (luns != null) {
                     throw new Exception(VsanIscsiUtil.getLocalizedString("error.target.delete.fail"));
                  }

                  ManagedObjectReference taskRef = vsanIscsiSystem.removeIscsiTarget(clusterRef, targetAlias);
                  if (taskRef != null) {
                     VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
                     var48 = taskRef;
                     break label567;
                  }
               } catch (Throwable var45) {
                  var10000 = var45;
                  var10001 = false;
                  break label569;
               }

               if (p != null) {
                  p.close();
               }

               return null;
            }

            if (p != null) {
               p.close();
            }

            label550:
            try {
               return var48;
            } catch (Throwable var44) {
               var10000 = var44;
               var10001 = false;
               break label550;
            }
         }

         var3 = var10000;
         if (p != null) {
            p.close();
         }

         throw var3;
      } catch (Throwable var46) {
         if (var3 == null) {
            var3 = var46;
         } else if (var3 != var46) {
            var3.addSuppressed(var46);
         }

         throw var3;
      }
   }

   @TsService
   public ManagedObjectReference createLun(ManagedObjectReference clusterRef, LunOperationSpec spec) throws Exception {
      this.validate(clusterRef, spec, spec.targetAlias);
      this.validateLunId(clusterRef, spec.targetAlias, spec.lunId);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.addIscsiLUN");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var20;
               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  ManagedObjectReference taskRef = vsanIscsiSystem.addIscsiLUN(clusterRef, spec.targetAlias, spec.toVmodlVsanIscsiLUNSpec());
                  if (taskRef == null) {
                     break label217;
                  }

                  VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
                  var20 = taskRef;
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
   public ManagedObjectReference editLun(ManagedObjectReference clusterRef, LunOperationSpec spec) throws Exception {
      this.validate(clusterRef, spec, spec.targetAlias);
      this.validateLunId(clusterRef, spec.targetAlias, spec.newLunId);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.editIscsiLUN");

         label217: {
            Throwable var10000;
            label219: {
               boolean var10001;
               ManagedObjectReference var20;
               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  ManagedObjectReference taskRef = vsanIscsiSystem.editIscsiLUN(clusterRef, spec.targetAlias, spec.toVmodlVsanIscsiLUNSpec());
                  if (taskRef == null) {
                     break label217;
                  }

                  VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
                  var20 = taskRef;
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
      } catch (Throwable var19) {
         if (var3 == null) {
            var3 = var19;
         } else if (var3 != var19) {
            var3.addSuppressed(var19);
         }

         throw var3;
      }
   }

   private void validateLunId(ManagedObjectReference clusterRef, String targetAlias, int newId) throws VsanUiLocalizableException {
      Validate.notEmpty(String.valueOf(newId));
      VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
      VsanIscsiLUN lun = null;

      try {
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiLUN");

            try {
               lun = vsanIscsiSystem.getIscsiLUN(clusterRef, targetAlias, newId);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var16) {
            if (var6 == null) {
               var6 = var16;
            } else if (var6 != var16) {
               var6.addSuppressed(var16);
            }

            throw var6;
         }
      } catch (Exception var17) {
      }

      if (lun != null) {
         throw new VsanUiLocalizableException("error.lun.id.duplicated", new Object[]{newId});
      }
   }

   @TsService
   public ManagedObjectReference[] removeLun(ManagedObjectReference clusterRef, TargetLunRemoveSpec spec) throws Exception {
      this.validate(clusterRef, spec, spec.targetAlias);
      Validate.notEmpty(spec.targetLunIds);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p;
         Throwable var10000;
         label297: {
            boolean var10001;
            ManagedObjectReference[] var25;
            label302: {
               p = _profiler.point("vsanIscsiSystem.removeIscsiLUN");

               try {
                  VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
                  List<ManagedObjectReference> tasksList = new ArrayList();
                  Integer[] var11;
                  int var10 = (var11 = spec.targetLunIds).length;

                  for(int var9 = 0; var9 < var10; ++var9) {
                     int lunId = var11[var9];
                     ManagedObjectReference taskRef = vsanIscsiSystem.removeIscsiLUN(clusterRef, spec.targetAlias, lunId);
                     if (taskRef != null) {
                        tasksList.add(VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid()));
                     }
                  }

                  if (tasksList.size() > 0) {
                     var25 = (ManagedObjectReference[])tasksList.toArray(new ManagedObjectReference[tasksList.size()]);
                     break label302;
                  }
               } catch (Throwable var23) {
                  var10000 = var23;
                  var10001 = false;
                  break label297;
               }

               if (p != null) {
                  p.close();
               }

               return new ManagedObjectReference[0];
            }

            if (p != null) {
               p.close();
            }

            label266:
            try {
               return var25;
            } catch (Throwable var22) {
               var10000 = var22;
               var10001 = false;
               break label266;
            }
         }

         var3 = var10000;
         if (p != null) {
            p.close();
         }

         throw var3;
      } catch (Throwable var24) {
         if (var3 == null) {
            var3 = var24;
         } else if (var3 != var24) {
            var3.addSuppressed(var24);
         }

         throw var3;
      }
   }

   @TsService
   public void allowIniatorAccess(ManagedObjectReference clusterRef, TargetInitiatorEditSpec spec) throws Exception {
      this.validate(clusterRef, spec, spec.targetAlias);
      Validate.notEmpty(spec.targetInitiatorNames);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.addIscsiInitiatorsToTarget");

         try {
            VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
            vsanIscsiSystem.addIscsiInitiatorsToTarget(clusterRef, spec.targetAlias, spec.targetInitiatorNames);
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
   public void disallowInitiatorAccess(ManagedObjectReference clusterRef, TargetInitiatorRemoveSpec spec) throws Exception {
      this.validate(clusterRef, spec, spec.targetAlias);
      Validate.notEmpty(spec.targetInitiatorNames);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.removeIscsiInitiatorsFromTarget");

         try {
            VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
            vsanIscsiSystem.removeIscsiInitiatorsFromTarget(clusterRef, spec.targetAlias, spec.targetInitiatorNames);
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

   private void validate(ManagedObjectReference clusterRef, Object spec, String targetAlias) throws Exception {
      Validate.notNull(spec);
      Validate.notEmpty(targetAlias);
   }
}
