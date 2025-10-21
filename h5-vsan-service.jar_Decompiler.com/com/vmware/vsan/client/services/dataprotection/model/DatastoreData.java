package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;

@data
public class DatastoreData {
   public ManagedObjectReference mor;
   public String name;
   public String primaryIconId;
   public DatastoreData.Type type;
   public String capacity;
   public String freeSpace;
   public String url;

   @data
   public static enum Type {
      NFS_3("NFS"),
      NFS_41("NFS41"),
      VMFS("VMFS"),
      VVOL("VVOL"),
      VSAN("vsan");

      private String name;

      private Type(String name) {
         this.name = name;
      }

      public String toString() {
         return this.name;
      }

      public static DatastoreData.Type fromString(String type) {
         DatastoreData.Type[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            DatastoreData.Type typeEnum = var4[var2];
            if (typeEnum.name.equals(type)) {
               return typeEnum;
            }
         }

         return null;
      }
   }
}
