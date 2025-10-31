package com.vmware.vsan.client.services.cns.model;

import com.vmware.vise.core.model.data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public enum CnsDatastoreAccessibilityStatus {
   accessible,
   notAccessible,
   partiallyAccessible;

   private static final Logger logger = LoggerFactory.getLogger(CnsDatastoreAccessibilityStatus.class);

   public static CnsDatastoreAccessibilityStatus fromName(String value) {
      try {
         return valueOf(value);
      } catch (Exception var2) {
         logger.warn("Unable to parse '" + value + "' status.", var2);
         return null;
      }
   }
}
