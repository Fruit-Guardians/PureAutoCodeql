package com.vmware.vsan.client.util;

import com.vmware.vim.binding.vim.NumericRange;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class StringUtil {
   private static final String RANGE_DELIMITER = ", ";
   private static final String RANGE_DIVIDER = "-";
   private static final String TIMESTAMP_FORMAT = "%1$tY-%5$tm-%1$td %1$tH:%1$tM:%1$tS";

   public static String getIndexedString(List<String> existingStrings, String baseString, String indexSeparator) {
      if (baseString != null && baseString.length() != 0) {
         if (existingStrings != null && existingStrings.size() != 0) {
            if (indexSeparator == null) {
               indexSeparator = "";
            }

            String newName = baseString;
            Boolean isUnique = false;
            int index = 1;

            while(true) {
               while(!isUnique) {
                  isUnique = true;
                  Iterator var7 = existingStrings.iterator();

                  while(var7.hasNext()) {
                     String str = (String)var7.next();
                     if (str.equalsIgnoreCase(newName)) {
                        newName = baseString + indexSeparator + index;
                        ++index;
                        isUnique = false;
                        break;
                     }
                  }
               }

               return newName;
            }
         } else {
            return baseString;
         }
      } else {
         throw new IllegalArgumentException("Default name cannot be null or empty.");
      }
   }

   public static String parseNumericRange(NumericRange[] ranges) {
      if (ranges == null) {
         return null;
      } else {
         StringBuilder result = new StringBuilder();
         NumericRange[] var5 = ranges;
         int var4 = ranges.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            NumericRange range = var5[var3];
            if (range.start == range.end) {
               result.append(Integer.toString(range.start));
            } else {
               result.append(range.start + "-" + range.end);
            }

            result.append(", ");
         }

         if (result.length() >= ", ".length()) {
            result.setLength(result.length() - ", ".length());
         }

         return result.toString();
      }
   }

   public static String parseTimestamp(Date timestamp) {
      return timestamp == null ? "null" : String.format("%1$tY-%5$tm-%1$td %1$tH:%1$tM:%1$tS", timestamp);
   }
}
