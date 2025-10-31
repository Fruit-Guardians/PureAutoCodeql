package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vise.core.model.data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public enum RecoveryState {
   notReady,
   ready,
   testFailoverInProgress,
   testFailoverCompleted,
   testCleanupInProgress,
   failoverInProgress,
   reprotectReady,
   reprotectInProgress,
   deactivateReady,
   deactivateInProgress;

   private static final Logger logger = LoggerFactory.getLogger(RecoveryState.class);

   public static RecoveryState forName(String name) {
      try {
         return valueOf(name);
      } catch (Exception var2) {
         logger.warn("Unable to parse '" + name + "' recovery state.", var2);
         return null;
      }
   }
}
