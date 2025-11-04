package com.vmware.vsan.client.services.common.data;

import com.vmware.vise.core.model.data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public enum StorageCompliance {
   outOfDate,
   compliant,
   nonCompliant,
   notApplicable,
   unknown;

   private static final Logger logger = LoggerFactory.getLogger(StorageCompliance.class);

   public static StorageCompliance fromName(String value) {
      try {
         return valueOf(value);
      } catch (Exception var1) {
         return null;
      }
   }
}
