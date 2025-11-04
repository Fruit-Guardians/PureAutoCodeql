package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.Map;
import org.codehaus.jackson.JsonNode;

@data
public class VsanComponent {
   private static final long serialVersionUID = 1L;
   public static final String VSAN_OBJECT_NOT_FOUND_STRING = Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.component.objectNotFound");
   private static final String COMPONENT_UUID_KEY = "componentUuid";
   private static final String COMPONENT_BYTES_TO_SYNC_KEY = "bytesToSync";
   private static final String COMPONENT_RECOVERY_ETA_KEY = "recoveryETA";
   public long byteToSync;
   public String componentUuid;
   public long recoveryEta;
   public VsanComponentState state;
   public String hostUuid;
   public String hostName;
   public String type;
   public String cacheDiskUuid;
   public String capacityDiskUuid;
   public ComponentIntent intent;

   public VsanComponent() {
   }

   public VsanComponent(boolean setDefaultValues) {
      if (setDefaultValues) {
         this.setDefaultValues();
      }

   }

   public VsanComponent(JsonNode node, JsonNode attributeNode, Map<String, String> componentUuidsToHostNames) {
      this.componentUuid = node.path("componentUuid").getTextValue();
      this.hostName = (String)componentUuidsToHostNames.get(this.componentUuid);
      this.byteToSync = this.getLongValue(attributeNode, "bytesToSync", 0);
      this.recoveryEta = (long)this.getIntValue(attributeNode, "recoveryETA", -1);
      this.intent = this.getIntent(attributeNode);
   }

   public void setDefaultValues() {
      this.cacheDiskUuid = VSAN_OBJECT_NOT_FOUND_STRING;
      this.capacityDiskUuid = VSAN_OBJECT_NOT_FOUND_STRING;
      this.hostUuid = VSAN_OBJECT_NOT_FOUND_STRING;
   }

   private ComponentIntent getIntent(JsonNode attributeNode) {
      int flags = this.getIntValue(attributeNode, "flags", 0);
      ComponentIntent result = null;
      if ((flags & ComponentIntent.DECOM.getValue()) == ComponentIntent.DECOM.getValue()) {
         result = ComponentIntent.DECOM;
      } else if ((flags & ComponentIntent.MOVE.getValue()) == ComponentIntent.MOVE.getValue()) {
         result = ComponentIntent.MOVE;
      } else if ((flags & ComponentIntent.REBALANCE.getValue()) == ComponentIntent.REBALANCE.getValue()) {
         result = ComponentIntent.REBALANCE;
      } else if ((flags & ComponentIntent.REPAIR.getValue()) == ComponentIntent.REPAIR.getValue()) {
         result = ComponentIntent.REPAIR;
      } else if ((flags & ComponentIntent.FIXCOMPLIANCE.getValue()) == ComponentIntent.FIXCOMPLIANCE.getValue()) {
         result = ComponentIntent.FIXCOMPLIANCE;
      } else if ((flags & ComponentIntent.POLICYCHANGE.getValue()) == ComponentIntent.POLICYCHANGE.getValue()) {
         result = ComponentIntent.POLICYCHANGE;
      } else if ((flags & ComponentIntent.STALE.getValue()) == ComponentIntent.STALE.getValue()) {
         result = ComponentIntent.STALE;
      } else if ((flags & ComponentIntent.MERGE_CONTACT.getValue()) == ComponentIntent.MERGE_CONTACT.getValue()) {
         result = ComponentIntent.MERGE_CONTACT;
      } else {
         result = ComponentIntent.FIXCOMPLIANCE;
      }

      return result;
   }

   private int getIntValue(JsonNode node, String field, int defaultValue) {
      return node != null && !node.isMissingNode() && node.has(field) ? node.get(field).getIntValue() : defaultValue;
   }

   private long getLongValue(JsonNode node, String field, int defaultValue) {
      return node != null && !node.isMissingNode() && node.has(field) ? node.get(field).getLongValue() : (long)defaultValue;
   }
}
