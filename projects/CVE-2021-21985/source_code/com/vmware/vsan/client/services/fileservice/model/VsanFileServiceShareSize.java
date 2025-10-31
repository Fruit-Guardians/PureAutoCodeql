package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.Arrays;
import java.util.List;

@data
public enum VsanFileServiceShareSize {
   MB("MEM_MB", new String[]{"MB", "M"}, 2),
   GB("MEM_GB", new String[]{"GB", "G"}, 3),
   TB("MEM_TB", new String[]{"TB", "T"}, 4);

   public final String labelKey;
   public final List<String> values;
   public final double multiplier;

   private VsanFileServiceShareSize(String labelKey, String[] values, int power) {
      this.labelKey = labelKey;
      this.values = Arrays.asList(values);
      this.multiplier = Math.pow(1024.0D, (double)power);
   }

   public static VsanFileServiceShareSize parse(String value) {
      if (value == null) {
         throw new NullPointerException("VsanFileServiceShareSize cannot be null");
      } else {
         VsanFileServiceShareSize[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            VsanFileServiceShareSize type = var4[var2];
            if (type.values.contains(value.toUpperCase())) {
               return type;
            }
         }

         throw new IllegalArgumentException("Unknonw VsanFileServiceShareSize: " + value);
      }
   }

   public String toString() {
      return Utils.getLocalizedString(this.labelKey);
   }
}
