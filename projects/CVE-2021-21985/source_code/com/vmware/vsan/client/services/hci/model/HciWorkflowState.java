package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import org.apache.commons.lang.StringUtils;

@data
public enum HciWorkflowState {
   IN_PROGRESS("in_progress"),
   DONE("done"),
   INVALID("invalid"),
   NOT_IN_HCI_WORKFLOW("not_in_hci_workflow");

   private String text;

   private HciWorkflowState(String text) {
      this.text = text;
   }

   public String getText() {
      return this.text;
   }

   public static HciWorkflowState fromString(String text) throws Exception {
      if (!StringUtils.isBlank(text)) {
         HciWorkflowState[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            HciWorkflowState level = var4[var2];
            if (text.equals(level.getText())) {
               return level;
            }
         }
      }

      throw new IllegalArgumentException("Unsupported HCI workflow state " + text);
   }
}
