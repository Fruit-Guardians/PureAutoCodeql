package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vise.core.model.data;

@data
public enum VsanFileServiceSharePermissionType {
   READ("READ"),
   WRITE("WRITE"),
   EXECUTE("EXECUTE");

   public final String value;

   private VsanFileServiceSharePermissionType(String value) {
      this.value = value;
   }

   public static VsanFileServiceSharePermissionType parse(String value) {
      VsanFileServiceSharePermissionType[] var4;
      int var3 = (var4 = values()).length;

      for(int var2 = 0; var2 < var3; ++var2) {
         VsanFileServiceSharePermissionType type = var4[var2];
         if (type.value.equals(value)) {
            return type;
         }
      }

      throw new IllegalArgumentException("Unknonw VsanFileServiceSharePermissionType: " + value);
   }
}
