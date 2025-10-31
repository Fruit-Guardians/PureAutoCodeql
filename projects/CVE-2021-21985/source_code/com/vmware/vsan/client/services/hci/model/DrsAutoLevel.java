package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public enum DrsAutoLevel {
   FULLY_AUTOMATED("fullyAutomated"),
   MANUAL("manual"),
   PARTIALLY_AUTOMATED("partiallyAutomated");

   private static final Log logger = LogFactory.getLog(DrsAutoLevel.class);
   private String text;

   private DrsAutoLevel(String text) {
      this.text = text;
   }

   public String valueOf() {
      return this.text;
   }

   public static DrsAutoLevel fromString(String text) throws Exception {
      if (!StringUtils.isBlank(text)) {
         DrsAutoLevel[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            DrsAutoLevel level = var4[var2];
            if (text.equals(level.valueOf())) {
               return level;
            }
         }
      }

      throw new IllegalArgumentException("Unsupported automation level " + text);
   }
}
