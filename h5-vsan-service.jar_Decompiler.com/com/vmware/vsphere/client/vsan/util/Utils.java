package com.vmware.vsphere.client.vsan.util;

import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vim.vsan.host.DiskResult.State;
import com.vmware.vim.binding.vmodl.MethodFault;
import com.vmware.vim.binding.vmodl.RuntimeFault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class Utils {
   private static MessageBundle MESSAGE_BUNDLE;

   public static void setMessageBundle(MessageBundle messageBundle) {
      MESSAGE_BUNDLE = messageBundle;
   }

   public static boolean isDiskEligible(DiskResult result) {
      State diskState = (State)Enum.valueOf(State.class, result.state);
      return diskState == State.eligible;
   }

   public static String getLocalizedString(String key) {
      return MESSAGE_BUNDLE.string(key);
   }

   public static String getLocalizedString(String key, String... params) {
      return MESSAGE_BUNDLE.string(key, params);
   }

   public static MethodFault getMethodFault(Throwable e) {
      if (e == null) {
         return null;
      } else if (e instanceof MethodFault) {
         return (MethodFault)e;
      } else {
         MethodFault methodFault = new MethodFault();
         methodFault.setMessage(e.getMessage());
         methodFault.initCause(e);
         if (e instanceof RuntimeFault) {
            methodFault.setFaultCause((RuntimeFault)e);
         }

         return methodFault;
      }
   }

   public static <T> List<T> arrayToList(T... array) {
      return array != null ? Arrays.asList(array) : Collections.EMPTY_LIST;
   }

   public static JsonNode getJsonRootNode(String jsonStr) {
      if (StringUtils.isEmpty(jsonStr)) {
         return null;
      } else {
         ObjectMapper mapper = new ObjectMapper();
         JsonNode rootNode = null;

         try {
            rootNode = mapper.readTree(jsonStr);
         } catch (Exception var3) {
         }

         return rootNode;
      }
   }
}
