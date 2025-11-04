package com.vmware.vsan.client.services.virtualobjects;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class VsanVirtualObjectsProvider {
   private static final Log _logger = LogFactory.getLog(VsanVirtualObjectsProvider.class);
   private static final String COMPOSITE_UUID = "compositeUuid";
   private final Map<String, Long> times = new HashMap();

   public Set<String> getVirtualObjectsUuids(ManagedObjectReference clusterRef) throws Exception {
      this.startTimer("getVirtualObjectsUuids");
      String[] jsonProperties = new String[]{"vsanPhysicalDiskVirtualMapping"};
      QuerySpec querySpec = this.getClusterHostsQuerySpec(clusterRef, "host", jsonProperties);
      ResultItem[] resultItems = QueryUtil.getData(querySpec).items;
      this.stopTimer("getVirtualObjectsUuids");
      if (resultItems == null) {
         return Collections.emptySet();
      } else {
         Set<String> vsanUuids = new HashSet();
         ResultItem[] var9 = resultItems;
         int var8 = resultItems.length;

         for(int var7 = 0; var7 < var8; ++var7) {
            ResultItem resultItem = var9[var7];
            JsonNode hostJsonData = this.getHostJsonData(resultItem);
            if (hostJsonData != null) {
               List<String> hostVsanUuids = hostJsonData.findValuesAsText("compositeUuid");
               vsanUuids.addAll(hostVsanUuids);
            }
         }

         return vsanUuids;
      }
   }

   private QuerySpec getClusterHostsQuerySpec(ManagedObjectReference clusterRef, String relation, String[] properties) {
      ObjectIdentityConstraint clusterConstraint = QueryUtil.createObjectIdentityConstraint(clusterRef);
      RelationalConstraint clusterHostsConstraint = QueryUtil.createRelationalConstraint(relation, clusterConstraint, true, HostSystem.class.getSimpleName());
      QuerySpec querySpecHosts = QueryUtil.buildQuerySpec((Constraint)clusterHostsConstraint, properties);
      return querySpecHosts;
   }

   private JsonNode getHostJsonData(ResultItem resultItem) {
      PropertyValue[] var5;
      int var4 = (var5 = resultItem.properties).length;

      for(int var3 = 0; var3 < var4; ++var3) {
         PropertyValue propValue = var5[var3];
         if ("vsanPhysicalDiskVirtualMapping".equals(propValue.propertyName)) {
            return Utils.getJsonRootNode((String)propValue.value);
         }
      }

      return null;
   }

   private void startTimer(String timerName) {
      long startTime = System.currentTimeMillis();
      if (!this.times.containsKey(timerName)) {
         this.times.remove(timerName);
      }

      this.times.put(timerName, startTime);
   }

   private void stopTimer(String timerName) {
      if (!this.times.containsKey(timerName)) {
         _logger.info("No start time for " + timerName);
      } else {
         _logger.info(timerName + " total time: " + (System.currentTimeMillis() - (Long)this.times.get(timerName)));
      }
   }
}
