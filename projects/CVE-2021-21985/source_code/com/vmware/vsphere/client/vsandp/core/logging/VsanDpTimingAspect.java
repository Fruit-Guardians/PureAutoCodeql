package com.vmware.vsphere.client.vsandp.core.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.NoAspectBoundException;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.runtime.internal.AroundClosure;

@Aspect
public class VsanDpTimingAspect {
   private static final Log _logger;
   private static final int THRESHOLD = 500;
   // $FF: synthetic field
   private static Throwable ajc$initFailureCause;
   // $FF: synthetic field
   public static final VsanDpTimingAspect ajc$perSingletonInstance;

   static {
      try {
         _logger = LogFactory.getLog(VsanDpTimingAspect.class);
         ajc$postClinit();
      } catch (Throwable var1) {
         ajc$initFailureCause = var1;
      }

   }

   // $FF: synthetic method
   @Pointcut(
      value = "(call(* com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.queryCgInfo(..)) || call(* com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.queryCgByObject(..)))",
      argNames = ""
   )
   void ajc$pointcut$$vsanDpVmodlInvocation$1c3() {
   }

   @Around(
      value = "vsanDpVmodlInvocation()",
      argNames = "ajc$aroundClosure"
   )
   public Object ajc$around$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$1$966e0d9b(AroundClosure ajc$aroundClosure, StaticPart thisJoinPointStaticPart) {
      long startTimeMs = System.currentTimeMillis();
      Object result = ajc$around$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$1$966e0d9bproceed(ajc$aroundClosure);
      long endTimeMs = System.currentTimeMillis();
      long execTimeMs = endTimeMs - startTimeMs;
      String name;
      String msg;
      if (execTimeMs > 500L) {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took too long: " + execTimeMs + " ms.";
         ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().warn(msg);
      } else {
         name = thisJoinPointStaticPart.getSignature().toString();
         msg = "Executing " + name + " took : " + execTimeMs + " ms.";
         ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger().debug(msg);
      }

      return result;
   }

   // $FF: synthetic method
   static Object ajc$around$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$1$966e0d9bproceed(AroundClosure var0) throws Throwable {
      return (Object)var0.run(new Object[0]);
   }

   public static VsanDpTimingAspect aspectOf() {
      if (ajc$perSingletonInstance == null) {
         throw new NoAspectBoundException("com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect", ajc$initFailureCause);
      } else {
         return ajc$perSingletonInstance;
      }
   }

   public static boolean hasAspect() {
      return ajc$perSingletonInstance != null;
   }

   // $FF: synthetic method
   private static void ajc$postClinit() {
      ajc$perSingletonInstance = new VsanDpTimingAspect();
   }

   // $FF: synthetic method
   public static Log ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger() {
      return _logger;
   }

   // $FF: synthetic method
   public static void ajc$inlineAccessFieldSet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$_logger(Log var0) {
      _logger = var0;
   }

   // $FF: synthetic method
   public static int ajc$inlineAccessFieldGet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$THRESHOLD() {
      return THRESHOLD;
   }

   // $FF: synthetic method
   public static void ajc$inlineAccessFieldSet$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$com_vmware_vsphere_client_vsandp_core_logging_VsanDpTimingAspect$THRESHOLD(int var0) {
      THRESHOLD = var0;
   }
}
