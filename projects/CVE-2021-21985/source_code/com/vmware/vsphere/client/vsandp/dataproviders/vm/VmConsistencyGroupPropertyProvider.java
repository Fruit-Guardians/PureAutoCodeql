package com.vmware.vsphere.client.vsandp.dataproviders.vm;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vmomi.core.impl.BlockingFuture;
import com.vmware.vim.vsandp.binding.vim.vsandp.ArchivalStorageLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.RemoteVsanLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.InstanceQuerySpecBase.InstanceSet;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.CgInfoQuery;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.TargetFilterSpec;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.CgInfoQuery.Spec;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Result.SeriesEntry;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec.Entry;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.logging.VsanDpTimingAspect;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.DpClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp.DpConnection;
import org.apache.commons.lang.ArrayUtils;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.runtime.internal.AroundClosure;
import org.aspectj.runtime.reflect.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmConsistencyGroupPropertyProvider {
   private static final Logger logger;
   @Autowired
   private DpClient dpClient;
   // $FF: synthetic field
   private static final StaticPart ajc$tjp_0;
   // $FF: synthetic field
   private static final StaticPart ajc$tjp_1;
   // $FF: synthetic field
   private static final StaticPart ajc$tjp_2;

   static {
      ajc$preClinit();
      logger = LoggerFactory.getLogger(VmConsistencyGroupPropertyProvider.class);
   }

   public CgInfo getCgInfo(ManagedObjectReference vmRef, ManagedObjectReference clusterRef) throws Exception {
      if (clusterRef != null && VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
         CgInfoQuery cgInfoResult = (CgInfoQuery)this.getCgInfoAsync(vmRef, clusterRef).get();
         if (ArrayUtils.isEmpty(cgInfoResult.getResult())) {
            logger.error("Incorrect result was received when CgInfo was queried: {}", cgInfoResult);
            throw new VsanUiLocalizableException("dataproviders.vm.cg.cgInfoQueryFault");
         } else {
            return cgInfoResult.getResult()[0];
         }
      } else {
         return null;
      }
   }

   public CgInfo getCgInfo(ManagedObjectReference clusterRef, String cgKey) {
      Spec spec = this.buildCgInfoQuerySpec(clusterRef, cgKey);
      CgInfoQuery result = null;

      try {
         Throwable var5 = null;
         Object var6 = null;

         try {
            DpConnection dpConnection = this.dpClient.getConnection(clusterRef);

            try {
               ProtectionService protectionService = dpConnection.getProtectionService();
               Throwable var9 = null;
               Object var10 = null;

               try {
                  Measure measure = new Measure("FailoverWorkflowBacking.getCgInfo");

                  try {
                     CgInfoQuery cgInfoResult = (CgInfoQuery)queryCgInfo_aroundBody1$advice(this, protectionService, spec, VsanDpTimingAspect.aspectOf(), (AroundClosure)null, ajc$tjp_0);
                     result = cgInfoResult;
                  } finally {
                     if (measure != null) {
                        measure.close();
                     }

                  }
               } catch (Throwable var36) {
                  if (var9 == null) {
                     var9 = var36;
                  } else if (var9 != var36) {
                     var9.addSuppressed(var36);
                  }

                  throw var9;
               }
            } finally {
               if (dpConnection != null) {
                  dpConnection.close();
               }

            }
         } catch (Throwable var38) {
            if (var5 == null) {
               var5 = var38;
            } else if (var5 != var38) {
               var5.addSuppressed(var38);
            }

            throw var5;
         }
      } catch (Exception var39) {
         throw new VsanUiLocalizableException("vsan.failover.validation.replicas.retrieve.error", var39);
      }

      if (ArrayUtils.isEmpty(result.getResult())) {
         logger.error("No CG object is found for key " + cgKey);
         throw new VsanUiLocalizableException("vsan.failover.validation.replicas.not.found");
      } else {
         return result.getResult()[0];
      }
   }

   public Future<CgInfoQuery> getCgInfoAsync(ManagedObjectReference vmRef, ManagedObjectReference clusterRef) throws Exception {
      CgMemberQuery cgBasicInfoResult = this.queryCgByObject(vmRef, clusterRef);
      if (cgBasicInfoResult == null) {
         return new BlockingFuture<CgInfoQuery>() {
            public CgInfoQuery get() {
               return null;
            }
         };
      } else {
         Throwable var4 = null;
         Object var5 = null;

         try {
            DpConnection dpConnnection = this.dpClient.getConnection(clusterRef);

            Throwable var10000;
            label194: {
               boolean var10001;
               BlockingFuture var26;
               try {
                  String cgId = this.getVmCgId(cgBasicInfoResult, vmRef);
                  ProtectionService protectionService = dpConnnection.getProtectionService();
                  Spec protectionSpec = new Spec();
                  protectionSpec.setCluster(clusterRef);
                  protectionSpec.setCg(new String[]{cgId});
                  Future result = new BlockingFuture();
                  queryCgInfo_aroundBody3$advice(this, protectionService, protectionSpec, result, VsanDpTimingAspect.aspectOf(), (AroundClosure)null, ajc$tjp_1);
                  var26 = result;
               } catch (Throwable var24) {
                  var10000 = var24;
                  var10001 = false;
                  break label194;
               }

               if (dpConnnection != null) {
                  dpConnnection.close();
               }

               label179:
               try {
                  return var26;
               } catch (Throwable var23) {
                  var10000 = var23;
                  var10001 = false;
                  break label179;
               }
            }

            var4 = var10000;
            if (dpConnnection != null) {
               dpConnnection.close();
            }

            throw var4;
         } catch (Throwable var25) {
            if (var4 == null) {
               var4 = var25;
            } else if (var4 != var25) {
               var4.addSuppressed(var25);
            }

            throw var4;
         }
      }
   }

   public CgMemberQuery queryCgByObject(ManagedObjectReference vmRef, ManagedObjectReference clusterRef) throws Exception {
      String vmStorageObjectId = this.getVmStorageObjectId(vmRef);
      if (vmStorageObjectId == null) {
         return null;
      } else {
         Throwable var4 = null;
         Object var5 = null;

         try {
            DpConnection dpConnnection = this.dpClient.getConnection(clusterRef);

            Throwable var10000;
            label194: {
               boolean var10001;
               CgMemberQuery var23;
               try {
                  InventoryService inventoryService = dpConnnection.getInventoryService();
                  com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery.Spec inventorySpec = new com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery.Spec();
                  inventorySpec.setCluster(clusterRef);
                  inventorySpec.setObject(new String[]{vmStorageObjectId});
                  var23 = (CgMemberQuery)queryCgByObject_aroundBody5$advice(this, inventoryService, inventorySpec, VsanDpTimingAspect.aspectOf(), (AroundClosure)null, ajc$tjp_2);
               } catch (Throwable var21) {
                  var10000 = var21;
                  var10001 = false;
                  break label194;
               }

               if (dpConnnection != null) {
                  dpConnnection.close();
               }

               label179:
               try {
                  return var23;
               } catch (Throwable var20) {
                  var10000 = var20;
                  var10001 = false;
                  break label179;
               }
            }

            var4 = var10000;
            if (dpConnnection != null) {
               dpConnnection.close();
            }

            throw var4;
         } catch (Throwable var22) {
            if (var4 == null) {
               var4 = var22;
            } else if (var4 != var22) {
               var4.addSuppressed(var22);
            }

            throw var4;
         }
      }
   }

   private Spec buildCgInfoQuerySpec(ManagedObjectReference clusterRef, String cgKey) {
      Spec protectionSpec = new Spec();
      protectionSpec.setCluster(clusterRef);
      protectionSpec.setCg(new String[]{cgKey});
      protectionSpec.setTargetFilter(new TargetFilterSpec(false, false, (RemoteVsanLocation[])null, true, false, (ArchivalStorageLocation[])null));
      return protectionSpec;
   }

   private String getVmStorageObjectId(ManagedObjectReference vmRef) throws Exception {
      try {
         return (String)QueryUtil.getProperty(vmRef, "config.vmStorageObjectId", (Object)null);
      } catch (Exception var3) {
         logger.error("Unable to determine the VM's storage object ID for {}", vmRef);
         throw var3;
      }
   }

   private String getVmCgId(CgMemberQuery cgBasicInfoResult, ManagedObjectReference vmRef) throws Exception {
      if (ArrayUtils.isNotEmpty(cgBasicInfoResult.getError())) {
         logger.error("Unable to find consistency groups for VM {} due to :\n{}", vmRef, cgBasicInfoResult.getError()[0].fault.getMessage());
         throw cgBasicInfoResult.getError()[0].fault;
      } else if (!ArrayUtils.isEmpty(cgBasicInfoResult.getResult()) && cgBasicInfoResult.getResult()[0].getCg() != null) {
         logger.debug("Found {} consistency groups for VM {}", cgBasicInfoResult.getResult().length, vmRef);
         return cgBasicInfoResult.getResult()[0].getCg().getKey();
      } else {
         logger.error("No protection data found for VM: {}", vmRef);
         throw new VsanUiLocalizableException("dataproviders.vm.cg.cgInfoQueryFault");
      }
   }

   public SeriesEntry[] getRemoteSeries(ManagedObjectReference clusterRef, String cgKey) {
      Throwable var3 = null;
      Object var4 = null;

      try {
         DpConnection dpConnnection = this.dpClient.getConnection(clusterRef);

         Throwable var10000;
         label297: {
            InstanceQuery queryResult;
            boolean var10001;
            label296: {
               try {
                  ProtectionService protectionService = dpConnnection.getProtectionService();
                  com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec querySpec = this.getProtectionServiceQuerySpec(clusterRef, cgKey, this.getRemoteTargetFilterSpec());
                  queryResult = protectionService.queryInstances(querySpec);
                  if (!ArrayUtils.isEmpty(queryResult.getResult())) {
                     break label296;
                  }

                  logger.info("No remote PITs found for cgKey: {}, result: {}", cgKey, queryResult);
               } catch (Throwable var27) {
                  var10000 = var27;
                  var10001 = false;
                  break label297;
               }

               if (dpConnnection != null) {
                  dpConnnection.close();
               }

               return null;
            }

            SeriesEntry[] var29;
            try {
               var29 = queryResult.getResult()[0].getSeries();
            } catch (Throwable var26) {
               var10000 = var26;
               var10001 = false;
               break label297;
            }

            if (dpConnnection != null) {
               dpConnnection.close();
            }

            label278:
            try {
               return var29;
            } catch (Throwable var25) {
               var10000 = var25;
               var10001 = false;
               break label278;
            }
         }

         var3 = var10000;
         if (dpConnnection != null) {
            dpConnnection.close();
         }

         throw var3;
      } catch (Throwable var28) {
         if (var3 == null) {
            var3 = var28;
         } else if (var3 != var28) {
            var3.addSuppressed(var28);
         }

         throw var3;
      }
   }

   public SeriesEntry[] getArchivalSeries(ManagedObjectReference clusterRef, String cgKey) {
      try {
         InstanceQuery queryResult = (InstanceQuery)this.getArchivalSeriesAsync(clusterRef, cgKey).get();
         if (ArrayUtils.isEmpty(queryResult.getResult())) {
            logger.info("No archival PITs found for cgKey: {}, result: {}", cgKey, queryResult);
            return null;
         } else {
            return queryResult.getResult()[0].getSeries();
         }
      } catch (Exception var4) {
         logger.error("Unable to get archive series for CG: {}", cgKey);
         return null;
      }
   }

   public Future<InstanceQuery> getArchivalSeriesAsync(ManagedObjectReference clusterRef, String cgKey) {
      Throwable var3 = null;
      Object var4 = null;

      try {
         DpConnection dpConnnection = this.dpClient.getConnection(clusterRef);

         Throwable var10000;
         label173: {
            boolean var10001;
            BlockingFuture var21;
            try {
               ProtectionService protectionService = dpConnnection.getProtectionService();
               com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec querySpec = this.getProtectionServiceQuerySpec(clusterRef, cgKey, this.getArchiveTargetFilterSpec());
               Future result = new BlockingFuture();
               protectionService.queryInstances(querySpec, result);
               var21 = result;
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label173;
            }

            if (dpConnnection != null) {
               dpConnnection.close();
            }

            label162:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (dpConnnection != null) {
            dpConnnection.close();
         }

         throw var3;
      } catch (Throwable var20) {
         if (var3 == null) {
            var3 = var20;
         } else if (var3 != var20) {
            var3.addSuppressed(var20);
         }

         throw var3;
      }
   }

   private com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec getProtectionServiceQuerySpec(ManagedObjectReference clusterRef, String cgInfoKey, TargetFilterSpec targetFilterSpec) {
      Entry entry = new Entry();
      entry.setCg(cgInfoKey);
      entry.setTargetFilter(targetFilterSpec);
      entry.setInstancesSpec(new InstanceSet());
      com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec spec = new com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Spec();
      spec.setCluster(clusterRef);
      spec.setEntry(new Entry[]{entry});
      return spec;
   }

   private TargetFilterSpec getArchiveTargetFilterSpec() {
      TargetFilterSpec filterSpec = new TargetFilterSpec();
      filterSpec.setArchiveRequested(true);
      return filterSpec;
   }

   private TargetFilterSpec getRemoteTargetFilterSpec() {
      TargetFilterSpec filterSpec = new TargetFilterSpec();
      filterSpec.setRemoteRequested(true);
      return filterSpec;
   }

   // $FF: synthetic method
   private static final CgInfoQuery queryCgInfo_aroundBody0(VmConsistencyGroupPropertyProvider var0, ProtectionService var1, Spec var2) {
      return var1.queryCgInfo(var2);
   }

   // $FF: synthetic method
   private static final Object queryCgInfo_aroundBody1$advice(VmConsistencyGroupPropertyProvider ajc$this, ProtectionService target, Spec arg0, VsanDpTimingAspect ajc$aspectInstance, AroundClosure ajc$aroundClosure, StaticPart thisJoinPointStaticPart) {
      long startTimeMs = System.currentTimeMillis();
      Object result = queryCgInfo_aroundBody0(ajc$this, target, arg0);
      long endTimeMs = System.currentTimeMillis();
      long execTimeMs = endTimeMs - startTimeMs;
      String name;
      String msg;
      if (execTimeMs > 500L) {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took too long: " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().warn(msg);
      } else {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took : " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().debug(msg);
      }

      return result;
   }

   // $FF: synthetic method
   private static final void queryCgInfo_aroundBody2(VmConsistencyGroupPropertyProvider var0, ProtectionService var1, Spec var2, Future var3) {
      var1.queryCgInfo(var2, var3);
   }

   // $FF: synthetic method
   private static final Object queryCgInfo_aroundBody3$advice(VmConsistencyGroupPropertyProvider ajc$this, ProtectionService target, Spec arg0, Future arg1, VsanDpTimingAspect ajc$aspectInstance, AroundClosure ajc$aroundClosure, StaticPart thisJoinPointStaticPart) {
      long startTimeMs = System.currentTimeMillis();
      queryCgInfo_aroundBody2(ajc$this, target, arg0, arg1);
      Object result = null;
      long endTimeMs = System.currentTimeMillis();
      long execTimeMs = endTimeMs - startTimeMs;
      String name;
      String msg;
      if (execTimeMs > 500L) {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took too long: " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().warn(msg);
      } else {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took : " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().debug(msg);
      }

      return result;
   }

   // $FF: synthetic method
   private static final CgMemberQuery queryCgByObject_aroundBody4(VmConsistencyGroupPropertyProvider var0, InventoryService var1, com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery.Spec var2) {
      return var1.queryCgByObject(var2);
   }

   // $FF: synthetic method
   private static final Object queryCgByObject_aroundBody5$advice(VmConsistencyGroupPropertyProvider ajc$this, InventoryService target, com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery.Spec arg0, VsanDpTimingAspect ajc$aspectInstance, AroundClosure ajc$aroundClosure, StaticPart thisJoinPointStaticPart) {
      long startTimeMs = System.currentTimeMillis();
      Object result = queryCgByObject_aroundBody4(ajc$this, target, arg0);
      long endTimeMs = System.currentTimeMillis();
      long execTimeMs = endTimeMs - startTimeMs;
      String name;
      String msg;
      if (execTimeMs > 500L) {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took too long: " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().warn(msg);
      } else {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took : " + execTimeMs + " ms.";
         VsanDpTimingAspect.ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().debug(msg);
      }

      return result;
   }

   // $FF: synthetic method
   private static void ajc$preClinit() {
      Factory var0 = new Factory("VmConsistencyGroupPropertyProvider.java", VmConsistencyGroupPropertyProvider.class);
      ajc$tjp_0 = var0.makeSJP("method-call", var0.makeMethodSig("401", "queryCgInfo", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService$CgInfoQuery$Spec", "arg0", "com.vmware.vim.vsandp.binding.vim.vsandp.fault.VsanClusterNotFound:com.vmware.vim.vsandp.binding.vim.vsandp.fault.NoHostFound:com.vmware.vim.vsandp.binding.vim.vsandp.fault.RequestKeyLimitExceeded:com.vmware.vim.vsandp.binding.vim.vsandp.fault.QueryLimitExceeded", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService$CgInfoQuery"), 55);
      ajc$tjp_1 = var0.makeSJP("method-call", var0.makeMethodSig("401", "queryCgInfo", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService$CgInfoQuery$Spec:com.vmware.vim.vmomi.core.Future", "arg0:arg1", "", "void"), 95);
      ajc$tjp_2 = var0.makeSJP("method-call", var0.makeMethodSig("401", "queryCgByObject", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService$CgMemberQuery$Spec", "arg0", "com.vmware.vim.vsandp.binding.vim.vsandp.fault.VsanClusterNotFound:com.vmware.vim.vsandp.binding.vim.vsandp.fault.NoHostFound:com.vmware.vim.vsandp.binding.vim.vsandp.fault.RequestKeyLimitExceeded", "com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService$CgMemberQuery"), 113);
   }
}
