package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vise.core.model.data;
import org.apache.commons.lang.StringUtils;

@data
public enum VsanFileServiceSecurityMode {
   AUTH_SYS("AUTH_SYS"),
   KERBEROS("AUTH_KERB_ALL");

   private final String vmodl;

   private VsanFileServiceSecurityMode(String vmodl) {
      this.vmodl = vmodl;
   }

   public static VsanFileServiceSecurityMode parse(String vmodl) {
      if (StringUtils.isEmpty(vmodl)) {
         return null;
      } else {
         VsanFileServiceSecurityMode[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            VsanFileServiceSecurityMode mode = var4[var2];
            if (mode.vmodl.equals(vmodl)) {
               return mode;
            }
         }

         throw new IllegalArgumentException("Unsupported security mode: " + vmodl);
      }
   }

   public String toVmodl() {
      return this.vmodl;
   }
}
