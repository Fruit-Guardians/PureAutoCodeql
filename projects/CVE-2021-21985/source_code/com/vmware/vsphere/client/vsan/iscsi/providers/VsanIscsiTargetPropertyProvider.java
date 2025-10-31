package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.fault.NoPermission;
import com.vmware.vim.binding.vim.fault.VsanFault;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.data.IscsiLun;
import com.vmware.vsphere.client.vsan.base.data.IscsiTarget;
import com.vmware.vsphere.client.vsan.base.impl.PbmDataProvider;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.base.util.multithreading.VsanAsyncQueryUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.VsanIscsiTargetProviderParameter;
import com.vmware.vsphere.client.vsan.iscsi.models.target.initiator.TargetInitiatorSpec;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanIscsiTargetPropertyProvider {
   @Autowired
   PbmDataProvider pbmDataProvider;
   private static final Log _logger = LogFactory.getLog(VsanIscsiTargetPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiTargetPropertyProvider.class);
   private static final String HOST_TYPE = "HostSystem";
   private static final String MANAGED_OBJECT_PREFIX = "urn:vmomi:";
   private static final String COLON = ":";
   private static final String NAMESPACE_CAPABILITY_METADATA = "namespaceCapabilityMetadata";

   @TsService
   public IscsiTarget getIscsiTarget(ManagedObjectReference clusterRef, String targetAlias) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiTarget target = null;
         Throwable var5 = null;
         IscsiTarget result = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiTarget");

            try {
               target = vsanIscsiSystem.getIscsiTarget(clusterRef, targetAlias);
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

         if (target != null) {
            target.ioOwnerHost = this.buildHostMor(target.ioOwnerHost, clusterRef.getServerGuid());
         }

         Map<String, String> profiles = this.pbmDataProvider.getStoragePolicyIdNameMap(clusterRef);
         result = new IscsiTarget(target, (List)null, profiles, (Object)null);
         return result;
      }
   }

   @TsService
   public IscsiLun[] getVsanIscsiTargetLunList(ManagedObjectReference clusterRef, String targetAlias) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiLUN[] iscsiLuns = null;

         Exception ex;
         try {
            Throwable var5 = null;
            ex = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiLUNs");

               try {
                  iscsiLuns = vsanIscsiSystem.getIscsiLUNs(clusterRef, new String[]{targetAlias});
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
         } catch (VsanFault var20) {
            ex = new Exception(var20.getLocalizedMessage(), var20.getCause());
            throw ex;
         }

         if (iscsiLuns == null) {
            return null;
         } else {
            int i = 0;
            IscsiLun[] luns = new IscsiLun[iscsiLuns.length];
            Map<String, String> profiles = this.pbmDataProvider.getStoragePolicyIdNameMap(clusterRef);
            VsanIscsiLUN[] var11 = iscsiLuns;
            int var10 = iscsiLuns.length;

            for(int var9 = 0; var9 < var10; ++var9) {
               VsanIscsiLUN lun = var11[var9];
               luns[i++] = new IscsiLun(lun, profiles);
            }

            return luns;
         }
      }
   }

   @TsService
   public TargetInitiatorSpec[] getVsanIscsiTargetInitiatorList(ManagedObjectReference clusterRef, String targetIqn) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
         VsanIscsiTarget vsanIscsiTarget = null;
         Throwable var5 = null;
         String[] initiators = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiTarget");

            try {
               vsanIscsiTarget = vsanIscsiSystem.getIscsiTarget(clusterRef, targetIqn);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var18) {
            if (var5 == null) {
               var5 = var18;
            } else if (var5 != var18) {
               var5.addSuppressed(var18);
            }

            throw var5;
         }

         List<TargetInitiatorSpec> targetInitiatorList = new ArrayList();
         if (vsanIscsiTarget != null) {
            initiators = vsanIscsiTarget.getInitiators();
            int var9;
            if (initiators != null) {
               String[] var10 = initiators;
               var9 = initiators.length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  String initiator = var10[var8];
                  TargetInitiatorSpec TargetInitiatorSpec = new TargetInitiatorSpec();
                  TargetInitiatorSpec.name = initiator;
                  targetInitiatorList.add(TargetInitiatorSpec);
               }
            }

            String[] initiatorGroups = vsanIscsiTarget.getInitiatorGroups();
            if (initiatorGroups != null) {
               String[] var24 = initiatorGroups;
               int var23 = initiatorGroups.length;

               for(var9 = 0; var9 < var23; ++var9) {
                  String initiatorGroup = var24[var9];
                  TargetInitiatorSpec TargetInitiatorSpec = new TargetInitiatorSpec();
                  TargetInitiatorSpec.name = initiatorGroup;
                  TargetInitiatorSpec.isGroup = true;
                  targetInitiatorList.add(TargetInitiatorSpec);
               }
            }
         }

         return (TargetInitiatorSpec[])targetInitiatorList.toArray(new TargetInitiatorSpec[0]);
      }
   }

   @TsService
   public IscsiTarget[] getIscsiTargets(ManagedObjectReference clusterRef, VsanIscsiTargetProviderParameter param) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return new IscsiTarget[0];
      } else {
         List<Callable<VsanAsyncQueryUtils.RequestResult>> requestTasks = new ArrayList();
         if (param == null || param.requestNamespaceCapabilityMetadata) {
            requestTasks.add(this.getNamespaceCapabilityMetadata(clusterRef));
         }

         Object storageProfiles;
         if (param != null && !param.requestStorageProfiles) {
            storageProfiles = new HashMap();
         } else {
            storageProfiles = this.pbmDataProvider.getStoragePolicyIdNameMap(clusterRef);
         }

         requestTasks.add(this.getIscsiObjects(clusterRef));
         ResultSet resultSet = VsanAsyncQueryUtils.getProperties(requestTasks);
         VsanIscsiTarget[] targets = new VsanIscsiTarget[0];
         Map<String, List<VsanIscsiLUN>> targetToLuns = new HashMap();
         Object namespaceMetadata = null;
         if (resultSet.error != null) {
            throw resultSet.error;
         } else {
            ResultItem[] var12;
            int var11 = (var12 = resultSet.items).length;

            int i;
            for(i = 0; i < var11; ++i) {
               ResultItem item = var12[i];
               PropertyValue[] var16;
               int var15 = (var16 = item.properties).length;

               for(int var14 = 0; var14 < var15; ++var14) {
                  PropertyValue prop = var16[var14];
                  String var17;
                  switch((var17 = prop.propertyName).hashCode()) {
                  case -1099694814:
                     if (var17.equals("namespaceCapabilityMetadata")) {
                        namespaceMetadata = prop.value;
                     }
                     break;
                  case -1063158413:
                     if (var17.equals("iscsiTargets")) {
                        VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult iscsiResult = (VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult)prop.value;
                        targets = iscsiResult.targets;
                        targetToLuns = iscsiResult.targetToLuns;
                     }
                  }
               }
            }

            IscsiTarget[] result = new IscsiTarget[targets.length];
            i = 0;
            VsanIscsiTarget[] var23 = targets;
            int var22 = targets.length;

            for(int var21 = 0; var21 < var22; ++var21) {
               VsanIscsiTarget target = var23[var21];
               IscsiTarget item = new IscsiTarget(target, (List)((Map)targetToLuns).get(target.alias), (Map)storageProfiles, namespaceMetadata);
               item.ioOwnerHost = this.buildHostMor(item.ioOwnerHost, clusterRef.getServerGuid());
               result[i++] = item;
            }

            return result;
         }
      }
   }

   private Callable<VsanAsyncQueryUtils.RequestResult> getIscsiObjects(final ManagedObjectReference clusterRef) {
      return new Callable<VsanAsyncQueryUtils.RequestResult>() {
         public VsanAsyncQueryUtils.RequestResult call() {
            Exception error = null;
            VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult iscsiResult = new VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult((VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult)null);

            try {
               iscsiResult = VsanIscsiTargetPropertyProvider.this.getIscsiTargetsResult(clusterRef);
            } catch (Exception var4) {
               error = var4;
            }

            return new VsanAsyncQueryUtils.RequestResult(iscsiResult, error != null ? error : iscsiResult.error, clusterRef, "iscsiTargets");
         }
      };
   }

   private Callable<VsanAsyncQueryUtils.RequestResult> getNamespaceCapabilityMetadata(final ManagedObjectReference clusterRef) {
      return new Callable<VsanAsyncQueryUtils.RequestResult>() {
         public VsanAsyncQueryUtils.RequestResult call() {
            Exception error = null;
            Object namespaceMetadata = null;

            try {
               PropertyValue[] resultset = QueryUtil.getProperties(clusterRef, new String[]{"namespaceCapabilityMetadata"}).getPropertyValues();
               PropertyValue[] var7 = resultset;
               int var6 = resultset.length;

               for(int var10 = 0; var10 < var6; ++var10) {
                  PropertyValue propVal = var7[var10];
                  namespaceMetadata = propVal.value;
               }
            } catch (Exception var8) {
               Throwable cause = var8;
               boolean isNoPermission = false;

               do {
                  if (cause instanceof NoPermission) {
                     isNoPermission = true;
                     break;
                  }

                  cause = ((Throwable)cause).getCause();
               } while(cause != null);

               if (!isNoPermission) {
                  error = Utils.getMethodFault(var8);
               }
            }

            return new VsanAsyncQueryUtils.RequestResult(namespaceMetadata, error, clusterRef, "namespaceCapabilityMetadata");
         }
      };
   }

   private VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult getIscsiTargetsResult(ManagedObjectReference clusterRef) throws Exception {
      VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult iscsiResult = new VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult((VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult)null);
      VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
      VsanIscsiTarget[] targets = null;

      Exception ex;
      VsanProfiler.Point p;
      try {
         Throwable var5 = null;
         ex = null;

         try {
            p = _profiler.point("vsanIscsiSystem.getIscsiTargets");

            try {
               targets = vsanIscsiSystem.getIscsiTargets(clusterRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var35) {
            if (var5 == null) {
               var5 = var35;
            } else if (var5 != var35) {
               var5.addSuppressed(var35);
            }

            throw var5;
         }
      } catch (Exception var36) {
         ex = new Exception(var36.getLocalizedMessage(), var36);
         throw ex;
      }

      if (ArrayUtils.isEmpty(targets)) {
         return iscsiResult;
      } else {
         iscsiResult.targets = targets;
         VsanIscsiLUN[] luns = new VsanIscsiLUN[0];

         try {
            Throwable var41 = null;
            p = null;

            try {
               VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getIscsiLUNs");

               try {
                  luns = vsanIscsiSystem.getIscsiLUNs(clusterRef, (String[])null);
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var38) {
               if (var41 == null) {
                  var41 = var38;
               } else if (var41 != var38) {
                  var41.addSuppressed(var38);
               }

               throw var41;
            }
         } catch (VsanFault var39) {
            iscsiResult.error = new Exception(var39.getLocalizedMessage(), var39.getCause());
            _logger.warn("Cannot get iSCSI LUNs: " + var39.getLocalizedMessage());
         }

         if (luns != null) {
            VsanIscsiLUN[] var9 = luns;
            int var44 = luns.length;

            for(int var42 = 0; var42 < var44; ++var42) {
               VsanIscsiLUN lun = var9[var42];
               if (!iscsiResult.targetToLuns.containsKey(lun.targetAlias)) {
                  iscsiResult.targetToLuns.put(lun.targetAlias, new ArrayList());
               }

               ((List)iscsiResult.targetToLuns.get(lun.targetAlias)).add(lun);
            }
         }

         return iscsiResult;
      }
   }

   private String buildHostMor(String hostStr, String vcGuid) {
      if (!StringUtils.isEmpty(hostStr) && hostStr.split(":").length != 1) {
         String[] values = hostStr.split(":");
         String hostValue = values[values.length - 1];
         return "urn:vmomi:HostSystem:" + hostValue + ":" + vcGuid;
      } else {
         return null;
      }
   }

   private static class IscsiTargetsTaskResult {
      public VsanIscsiTarget[] targets;
      public Map<String, List<VsanIscsiLUN>> targetToLuns;
      public Exception error;

      private IscsiTargetsTaskResult() {
         this.targets = new VsanIscsiTarget[0];
         this.targetToLuns = new HashMap();
      }

      // $FF: synthetic method
      IscsiTargetsTaskResult(VsanIscsiTargetPropertyProvider.IscsiTargetsTaskResult var1) {
         this();
      }
   }
}
