package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vim.vsandp.binding.vim.vsandp.ProtectionState;
import com.vmware.vise.core.model.data;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public enum VsanObjectDataProtectionHealthState {
   UNKNOWN("unknown"),
   NOT_CONFIGURED("notconfigured"),
   PROTECTION_OK("protectionok"),
   FULL_SYNC_IN_PROGRESS("fullsyncinprogress"),
   PROTECTION_NOT_OWNER("protectionnotowner"),
   VM_QUIESCING_FAILED("vmquiescingfailed"),
   INVALID_PROTECTION_CONFIGURATION("invalidprotectionconfiguration"),
   ARCHIVE_INACCESSIBLE("archivestoreinaccessible"),
   ARCHIVE_NO_SPACE("archivestorenospace"),
   ARCHIVE_NOT_CONFIGURED("archivetargetnotconfigured"),
   CG_OBJECT_NOT_AVAILABLE("cgobjectnotavailable"),
   RETENTION_FAILURES("retentionfailures"),
   LOCAL_STORAGE_USAGE_EXCEEDED_THRESHOLD("localstorageusageexceededthreshold"),
   CG_CONTAINS_UNPROMOTED_OBJECTS("cgcontainsunpromotedobjects"),
   MULTIPLE_CGS_FOR_PE("multiplecgsforpe");

   private static final Log _logger = LogFactory.getLog(VsanObjectDataProtectionHealthState.class);
   private static Map<String, VsanObjectDataProtectionHealthState> protectionStateNameToValues;
   private String text;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState;

   private VsanObjectDataProtectionHealthState(String text) {
      this.text = text;
   }

   public static VsanObjectDataProtectionHealthState fromProtectionState(ProtectionState state) {
      switch($SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState()[state.ordinal()]) {
      case 1:
         return PROTECTION_OK;
      case 2:
         return NOT_CONFIGURED;
      case 3:
         return FULL_SYNC_IN_PROGRESS;
      case 4:
         return PROTECTION_NOT_OWNER;
      case 5:
         return INVALID_PROTECTION_CONFIGURATION;
      case 6:
         return VM_QUIESCING_FAILED;
      case 7:
         return UNKNOWN;
      case 8:
         return CG_OBJECT_NOT_AVAILABLE;
      case 9:
         return RETENTION_FAILURES;
      case 10:
         return ARCHIVE_INACCESSIBLE;
      case 11:
         return ARCHIVE_NO_SPACE;
      case 12:
         return ARCHIVE_NOT_CONFIGURED;
      case 13:
         return LOCAL_STORAGE_USAGE_EXCEEDED_THRESHOLD;
      case 14:
         return CG_CONTAINS_UNPROMOTED_OBJECTS;
      default:
         _logger.warn("Unknown VsanObjectDataProtectionHealthState detected: " + state);
         return UNKNOWN;
      }
   }

   public static VsanObjectDataProtectionHealthState fromProtectionStateName(String protectionStateName) {
      VsanObjectDataProtectionHealthState val = (VsanObjectDataProtectionHealthState)getProtectionStateNamesToValues().get(protectionStateName);
      if (val == null) {
         _logger.warn("Unknown VsanObjectDataProtectionHealthState detected for protection state: " + protectionStateName);
         return UNKNOWN;
      } else {
         return val;
      }
   }

   private static Map<String, VsanObjectDataProtectionHealthState> getProtectionStateNamesToValues() {
      if (protectionStateNameToValues == null) {
         protectionStateNameToValues = new HashMap();
         ProtectionState[] var3;
         int var2 = (var3 = ProtectionState.values()).length;

         for(int var1 = 0; var1 < var2; ++var1) {
            ProtectionState pState = var3[var1];
            protectionStateNameToValues.put(pState.name(), fromProtectionState(pState));
         }
      }

      return protectionStateNameToValues;
   }

   public static VsanObjectDataProtectionHealthState fromString(String text) {
      if (text != null) {
         VsanObjectDataProtectionHealthState[] var4;
         int var3 = (var4 = values()).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            VsanObjectDataProtectionHealthState val = var4[var2];
            if (text.equalsIgnoreCase(val.text)) {
               return val;
            }
         }
      }

      _logger.warn("Unknown VsanObjectDataProtectionHealthState detected: " + text);
      return UNKNOWN;
   }

   public static VsanObjectDataProtectionHealthState fromServerLocalizedString(String text) {
      if (text != null) {
         text = text.replaceAll("-", "");
         return fromString(text);
      } else {
         _logger.warn("Empty VsanObjectDataProtectionHealthState text detected!");
         return UNKNOWN;
      }
   }

   public String valueOf() {
      return this.text;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[ProtectionState.values().length];

         try {
            var0[ProtectionState.archiveStorageNoSpace.ordinal()] = 11;
         } catch (NoSuchFieldError var15) {
         }

         try {
            var0[ProtectionState.archiveStorageNotAccessible.ordinal()] = 10;
         } catch (NoSuchFieldError var14) {
         }

         try {
            var0[ProtectionState.archiveTargetNotConfigured.ordinal()] = 12;
         } catch (NoSuchFieldError var13) {
         }

         try {
            var0[ProtectionState.cgObjectUnavailable.ordinal()] = 8;
         } catch (NoSuchFieldError var12) {
         }

         try {
            var0[ProtectionState.containsUnpromotedObjects.ordinal()] = 14;
         } catch (NoSuchFieldError var11) {
         }

         try {
            var0[ProtectionState.fullSyncInProgress.ordinal()] = 3;
         } catch (NoSuchFieldError var10) {
         }

         try {
            var0[ProtectionState.healthOk.ordinal()] = 1;
         } catch (NoSuchFieldError var9) {
         }

         try {
            var0[ProtectionState.invalidConfiguration.ordinal()] = 5;
         } catch (NoSuchFieldError var8) {
         }

         try {
            var0[ProtectionState.localRetentionFailure.ordinal()] = 9;
         } catch (NoSuchFieldError var7) {
         }

         try {
            var0[ProtectionState.localStorageUsageExceededThreshold.ordinal()] = 13;
         } catch (NoSuchFieldError var6) {
         }

         try {
            var0[ProtectionState.multipleCgsForPE.ordinal()] = 15;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[ProtectionState.protectionNotConfigured.ordinal()] = 2;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[ProtectionState.protectionNotOwner.ordinal()] = 4;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[ProtectionState.unknown.ordinal()] = 7;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[ProtectionState.vmQuiescingFailure.ordinal()] = 6;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vim$vsandp$binding$vim$vsandp$ProtectionState = var0;
         return var0;
      }
   }
}
