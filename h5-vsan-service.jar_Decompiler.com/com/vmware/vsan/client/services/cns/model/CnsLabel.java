package com.vmware.vsan.client.services.cns.model;

import com.vmware.vim.binding.vim.KeyValue;
import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;

@data
public class CnsLabel {
   public String key;
   public String value;

   public static List<CnsLabel> fromKeyValue(KeyValue[] keyValues) {
      List<CnsLabel> result = new ArrayList();
      if (ArrayUtils.isEmpty(keyValues)) {
         return result;
      } else {
         KeyValue[] var5 = keyValues;
         int var4 = keyValues.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            KeyValue keyValue = var5[var3];
            CnsLabel label = new CnsLabel();
            label.key = keyValue.key;
            label.value = keyValue.value;
            result.add(label);
         }

         return result;
      }
   }

   public static KeyValue toKeyValue(CnsLabel label) {
      return new KeyValue(label.key, label.value);
   }

   public static KeyValue[] toKeyValue(CnsLabel[] labels) {
      List<KeyValue> result = new ArrayList();
      CnsLabel[] var5 = labels;
      int var4 = labels.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         CnsLabel label = var5[var3];
         result.add(toKeyValue(label));
      }

      return (KeyValue[])result.toArray(new KeyValue[0]);
   }
}
