package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.vsan.binding.vim.vsan.FileShareConfig;
import com.vmware.vise.core.model.data;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

@data
public class VsanFileServiceShareConfig {
   private static final String SIZE_PATTERN = "^\\d+[\\.\\d]*";
   private static final String METRIC_PATTERN = "[mgtMGT]{1}[bB]{0,1}$";
   private static final String ALL_ACCESS_IP_PATTERN = "*";
   public String name;
   public String domainName;
   public Double quota;
   public VsanFileServiceShareSize quotaSize;
   public Double softQuota;
   public VsanFileServiceShareSize softQuotaSize;
   public Map<String, String> labels;
   public String policyId;
   public VsanFileServiceShareNetPermission[] netPermissions;
   public boolean isAllAccessAllowed;

   public static VsanFileServiceShareConfig fromVmodl(FileShareConfig vmodl) {
      return null;
   }

   private static String formatQuota(Double quota, VsanFileServiceShareSize size) {
      return quota != null && NumberUtils.compare(quota, NumberUtils.DOUBLE_ZERO) != 0 && size != null ? String.format("%.5f", quota) + size.toString() : "0";
   }

   private static Double parseQuotaValue(String value) {
      String quota = parse(value, "^\\d+[\\.\\d]*");
      return StringUtils.isEmpty(quota) ? null : Double.valueOf(quota);
   }

   private static VsanFileServiceShareSize parseQuotaMetric(String value) {
      String quota = parse(value, "[mgtMGT]{1}[bB]{0,1}$");
      return StringUtils.isEmpty(quota) ? null : VsanFileServiceShareSize.parse(quota);
   }

   private static String parse(String value, String patternStr) {
      if (StringUtils.isEmpty(value)) {
         return null;
      } else {
         Pattern pattern = Pattern.compile(patternStr);
         Matcher matcher = pattern.matcher(value);
         matcher.find();

         try {
            return matcher.group();
         } catch (Exception var4) {
            return null;
         }
      }
   }
}
