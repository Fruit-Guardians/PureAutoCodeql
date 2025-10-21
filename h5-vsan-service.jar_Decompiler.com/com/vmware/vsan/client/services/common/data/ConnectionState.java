package com.vmware.vsan.client.services.common.data;

import com.vmware.vise.core.model.data;

@data
public enum ConnectionState {
   connected,
   notResponding,
   disconnected;

   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$HostSystem$ConnectionState;

   public static ConnectionState fromHostState(com.vmware.vim.binding.vim.HostSystem.ConnectionState state) {
      switch($SWITCH_TABLE$com$vmware$vim$binding$vim$HostSystem$ConnectionState()[state.ordinal()]) {
      case 1:
         return connected;
      case 2:
         return notResponding;
      default:
         return disconnected;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$HostSystem$ConnectionState() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vim$binding$vim$HostSystem$ConnectionState;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[com.vmware.vim.binding.vim.HostSystem.ConnectionState.values().length];

         try {
            var0[com.vmware.vim.binding.vim.HostSystem.ConnectionState.connected.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[com.vmware.vim.binding.vim.HostSystem.ConnectionState.disconnected.ordinal()] = 3;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[com.vmware.vim.binding.vim.HostSystem.ConnectionState.notResponding.ordinal()] = 2;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vim$binding$vim$HostSystem$ConnectionState = var0;
         return var0;
      }
   }
}
