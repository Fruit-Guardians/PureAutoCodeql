package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public enum VsanObjectHealthState {
   HEALTHY("healthy"),
   DATA_MOVE("datamove"),
   NON_AVAILABILITY_RELATED_RECONFIG("nonavailabilityrelatedreconfig"),
   NON_AVAILABILITY_RELATED_INCOMPLIANCE("nonavailabilityrelatedincompliance"),
   REDUCED_AVAILABILITY_WITH_ACTIVE_REBUILD("reducedavailabilitywithactiverebuild"),
   REDUCED_AVAILABILITY_WITH_POLICY_PENDING("reducedavailabilitywithpolicypending"),
   NON_AVAILABILITY_RELATED_INCOMPLIANCE_WITH_POLICY_PENDING("nonavailabilityrelatedincompliancewithpolicypending"),
   NON_AVAILABILITY_RELATED_INCOMPLIANCE_WITH_POLICY_PENDING_FAILED("nonavailabilityrelatedincompliancewithpolicypendingfailed"),
   INACCESSIBLE("inaccessible"),
   REDUCED_AVAILABILITY_WITH_NO_REBUILD("reducedavailabilitywithnorebuild"),
   REDUCED_AVAILABILITY_WITH_NO_REBUILD_DELAY_TIMER("reducedavailabilitywithnorebuilddelaytimer"),
   REDUCED_AVAILABILITY_WITH_POLICY_PENDING_FAILED("reducedavailabilitywithpolicypendingfailed"),
   NON_AVAILABILITY_RELATED_INCOMPLIANCE_WITH_PAUSED_REBUILD("nonavailabilityrelatedincompliancewithpausedrebuild"),
   REDUCED_AVAILABILITY_WITH_PAUSED_REBUILD("reducedavailabilitywithpausedrebuild"),
   UNKNOWN("unknown");

   private static final Log _logger = LogFactory.getLog(VsanObjectHealthState.class);
   private String text;

   private VsanObjectHealthState(String text) {
      this.text = text;
   }

   public static VsanObjectHealthState fromString(String text) {
      if (text != null) {
         text = text.replace("-", "");
         VsanObjectHealthState[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            VsanObjectHealthState val = var4[var2];
            if (text.equalsIgnoreCase(val.text)) {
               return val;
            }
         }
      }

      _logger.warn("Unknown VsanObjectHealthState detected: " + text);
      return UNKNOWN;
   }

   public static VsanObjectHealthState fromServerLocalizedString(String text) {
      if (text != null) {
         text = text.replaceAll("-", "");
         return fromString(text);
      } else {
         _logger.warn("Empty VsanObjectHealthState text detected!");
         return UNKNOWN;
      }
   }

   public String valueOf() {
      return this.text;
   }
}
