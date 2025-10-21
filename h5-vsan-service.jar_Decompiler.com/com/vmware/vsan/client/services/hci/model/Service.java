package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import org.apache.commons.lang.StringUtils;

@data
public enum Service {
   MANAGEMENT("management"),
   VMOTION("vmotion"),
   VSAN("vsan");

   private String text;

   private Service(String text) {
      this.text = text;
   }

   public String getText() {
      return this.text;
   }

   public static Service fromString(String text) throws Exception {
      if (!StringUtils.isBlank(text)) {
         Service[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            Service level = var4[var2];
            if (text.equals(level.getText())) {
               return level;
            }
         }
      }

      throw new IllegalArgumentException("Unsupported service " + text);
   }
}
