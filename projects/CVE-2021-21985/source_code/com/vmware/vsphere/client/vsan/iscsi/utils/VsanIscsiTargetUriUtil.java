package com.vmware.vsphere.client.vsan.iscsi.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class VsanIscsiTargetUriUtil {
   private static final char PLUS_CHAR = '+';
   private static final char PERCENTAGE_CHAR = '%';
   private static final String PLUS_FLAG = "\"plus\"";
   private static final String PERCENTAGE_FLAG = "\"percentage\"";

   public static String encode(String data) throws UnsupportedEncodingException {
      StringBuffer tempBuffer = new StringBuffer();
      int incrementor = 0;

      for(int dataLength = data.length(); incrementor < dataLength; ++incrementor) {
         char charecterAt = data.charAt(incrementor);
         if (charecterAt == '%') {
            tempBuffer.append("\"percentage\"");
         } else if (charecterAt == '+') {
            tempBuffer.append("\"plus\"");
         } else {
            tempBuffer.append(charecterAt);
         }
      }

      data = tempBuffer.toString();
      data = URLEncoder.encode(data, "UTF-8");
      return data;
   }

   public static String decode(String data) throws UnsupportedEncodingException {
      data = URLDecoder.decode(data, "UTF-8");
      data = data.replaceAll("\"percentage\"", Character.toString('%'));
      data = data.replaceAll("\"plus\"", Character.toString('+'));
      return data;
   }
}
