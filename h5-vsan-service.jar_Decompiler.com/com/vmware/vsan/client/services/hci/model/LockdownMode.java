package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;

@data
public enum LockdownMode {
   DISABLED(com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode.lockdownDisabled),
   NORMAL(com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode.lockdownNormal),
   STRICT(com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode.lockdownStrict);

   private com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode vmodlLockdownMode;

   private LockdownMode(com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode lockdownMode) {
      this.vmodlLockdownMode = lockdownMode;
   }

   public com.vmware.vim.binding.vim.host.HostAccessManager.LockdownMode getVmodlLockdownMode() {
      return this.vmodlLockdownMode;
   }
}
