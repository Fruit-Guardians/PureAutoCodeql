package com.vmware.vsphere.client.vsandp.data;

import com.vmware.vim.vsandp.binding.vim.vsandp.QuiescedType;

// $FF: synthetic class
class DataProtectionInstance$1 {
   // $FF: synthetic field
   static final int[] $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType = new int[QuiescedType.values().length];

   static {
      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType[QuiescedType.none.ordinal()] = 1;
      } catch (NoSuchFieldError var3) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType[QuiescedType.applicationQuiesced.ordinal()] = 2;
      } catch (NoSuchFieldError var2) {
      }

      try {
         $SwitchMap$com$vmware$vim$vsandp$binding$vim$vsandp$QuiescedType[QuiescedType.fileSystemQuiesced.ordinal()] = 3;
      } catch (NoSuchFieldError var1) {
      }

   }
}
