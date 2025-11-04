package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vise.core.model.data;

@data
public enum VsanFileServiceShareNetPermissionType {
   READ_ONLY("READ_ONLY"),
   READ_WRITE("READ_WRITE");

   public final String value;

   private VsanFileServiceShareNetPermissionType(String value) {
      this.value = value;
   }

   public static VsanFileServiceShareNetPermissionType parse(String value) {
      VsanFileServiceShareNetPermissionType[] var4;
      int var3 = (var4 = values()).length;

      for(int var2 = 0; var2 < var3; ++var2) {
         VsanFileServiceShareNetPermissionType type = var4[var2];
         if (type.value.equals(value)) {
            return type;
         }
      }

      throw new IllegalArgumentException("Unknonw VsanFileServiceSharePermissionType: " + value);
   }
}
