package com.vmware.vsan.client.services.common.data;

// $FF: synthetic class
class ConnectionState$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$binding$vim$HostSystem$ConnectionState = new int[com.vmware.vim.binding.vim.HostSystem.ConnectionState.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$binding$vim$HostSystem$ConnectionState[com.vmware.vim.binding.vim.HostSystem.ConnectionState.connected.ordinal()] = 1;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$binding$vim$HostSystem$ConnectionState[com.vmware.vim.binding.vim.HostSystem.ConnectionState.notResponding.ordinal()] = 2;
      } catch (NoSuchFieldError var1) {
      }

   }
}
