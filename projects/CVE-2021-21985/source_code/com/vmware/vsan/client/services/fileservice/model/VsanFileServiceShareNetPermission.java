package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.vsan.binding.vim.vsan.FileShareNetPermission;
import com.vmware.vise.core.model.data;

@data
public class VsanFileServiceShareNetPermission {
   public String ipAddress;
   public boolean isWriteAllowed;
   public boolean isRootAllowed;

   public static VsanFileServiceShareNetPermission fromVmodl(FileShareNetPermission vmodl) {
      if (vmodl == null) {
         return null;
      } else {
         VsanFileServiceShareNetPermission sharePermission = new VsanFileServiceShareNetPermission();
         sharePermission.ipAddress = vmodl.ips;
         if (VsanFileServiceShareNetPermissionType.READ_WRITE.value.equals(vmodl.permissions)) {
            sharePermission.isWriteAllowed = true;
         }

         sharePermission.isRootAllowed = vmodl.allowRoot != null && vmodl.allowRoot;
         return sharePermission;
      }
   }

   public FileShareNetPermission toVmodl() {
      FileShareNetPermission vmodl = new FileShareNetPermission();
      vmodl.ips = this.ipAddress;
      vmodl.allowRoot = this.isRootAllowed;
      vmodl.permissions = this.isWriteAllowed ? VsanFileServiceShareNetPermissionType.READ_WRITE.value : VsanFileServiceShareNetPermissionType.READ_ONLY.value;
      return vmodl;
   }
}
