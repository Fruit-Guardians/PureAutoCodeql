package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class ConfigCardData {
   public String title;
   public String actionId;
   public ConfigCardData.Status status;
   public boolean enabled;
   public boolean nextStep;
   public String launchButtonText;
   public String contentHeader;
   public String contentText;
   public List<String> listItems;
   public boolean validatePresent;
   public boolean validateEnabled;
   public boolean operationInProgress;
   public int progress;
   public ValidationData validationData;

   public ConfigCardData(String title, String actionId, boolean validatePresent, boolean operationInProgress, String launchButtonText) {
      this.title = title;
      this.actionId = actionId;
      this.validatePresent = validatePresent;
      this.operationInProgress = operationInProgress;
      this.status = ConfigCardData.Status.NOT_AVAILABLE;
      this.enabled = false;
      this.launchButtonText = launchButtonText;
      this.nextStep = false;
      this.validateEnabled = false;
   }

   @data
   public static enum Status {
      NOT_AVAILABLE,
      PASSED,
      ERROR;
   }
}
