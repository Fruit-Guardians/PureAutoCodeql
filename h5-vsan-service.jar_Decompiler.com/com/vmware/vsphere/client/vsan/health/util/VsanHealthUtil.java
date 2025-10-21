package com.vmware.vsphere.client.vsan.health.util;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultBase;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultRow;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultTable;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthSummary;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthTest;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.Conjoiner;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.health.ColumnType;
import com.vmware.vsphere.client.vsan.health.VsanHealthData;
import com.vmware.vsphere.client.vsan.health.VsanHealthServiceGoalState;
import com.vmware.vsphere.client.vsan.health.VsanHealthServiceStatus;
import com.vmware.vsphere.client.vsan.health.VsanHealthServiceSubStatus;
import com.vmware.vsphere.client.vsan.health.VsanHealthStatus;
import com.vmware.vsphere.client.vsan.health.VsanTestData;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class VsanHealthUtil {
   private static final String HEALTH_STATUS = "status";
   private static final String HEALTH_GOAL_STATE = "goalState";
   private static final String STATUS_ISSUE = "statusIssue";
   public static final String TASK_TYPE = "Task";
   public static final String VSAN_INTERNET_ACCESS_ENABLED = "enableInternetAccess";
   private static final String NAME_PROPERTY = "name";
   private static final String HOST_CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   private static final String HOST_VERSION_PROPERTY = "config.product.version";
   private static final Log _logger = LogFactory.getLog(VsanHealthUtil.class);
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType;

   public static VsanHealthServiceStatus getVsanHealthServiceStatus(String status) throws Exception {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode root = objectMapper.readTree(status);
      if (root != null) {
         VsanHealthServiceStatus vhss = new VsanHealthServiceStatus();
         JsonNode statusNode = root.get("status");
         JsonNode goalStateNode = root.get("goalState");
         JsonNode statusIssueNode = root.get("statusIssue");
         if (statusNode != null) {
            vhss.status = VsanHealthServiceSubStatus.valueOf(statusNode.getTextValue());
         }

         if (goalStateNode != null) {
            vhss.goalState = VsanHealthServiceGoalState.valueOf(goalStateNode.getTextValue());
         }

         if (statusIssueNode != null) {
            vhss.statusIssue = statusIssueNode.getTextValue();
         }

         return vhss;
      } else {
         return null;
      }
   }

   public static ManagedObjectReference buildTaskMor(String taskId, String vcGuid) {
      ManagedObjectReference task = new ManagedObjectReference("Task", taskId, vcGuid);
      return task;
   }

   public static void addToTestMoRefs(VsanClusterHealthGroup healthGroup, Set<ManagedObjectReference> allMoRefs, String serverGuid) {
      addToTestMoRefsFromBaseResults(healthGroup.groupDetails, allMoRefs, serverGuid);
      if (healthGroup.groupTests != null) {
         VsanClusterHealthTest[] var6;
         int var5 = (var6 = healthGroup.groupTests).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthTest test = var6[var4];
            addToTestMoRefsFromBaseResults(test.testDetails, allMoRefs, serverGuid);
         }
      }

   }

   public static void addToTestMoRefsFromBaseResults(VsanClusterHealthResultBase[] baseResults, Set<ManagedObjectReference> allMoRefs, String serverGuid) {
      if (baseResults != null) {
         VsanClusterHealthResultBase[] var6 = baseResults;
         int var5 = baseResults.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthResultBase baseResult = var6[var4];
            if (baseResult instanceof VsanClusterHealthResultTable) {
               VsanClusterHealthResultTable table = (VsanClusterHealthResultTable)baseResult;
               addToTestMoRefsFromTable(table, allMoRefs, serverGuid);
            }
         }

      }
   }

   private static void addToTestMoRefsFromTable(VsanClusterHealthResultTable table, Set<ManagedObjectReference> allMoRefs, String serverGuid) {
      if (table.columns != null && table.rows != null) {
         for(int i = 0; i < table.columns.length; ++i) {
            ColumnType columnType = ColumnType.valueOf(table.columns[i].type);
            if (columnType.equals(ColumnType.mor) || columnType.equals(ColumnType.listMor) || columnType.equals(ColumnType.dynamic)) {
               VsanClusterHealthResultRow[] var8;
               int var7 = (var8 = table.rows).length;

               for(int var6 = 0; var6 < var7; ++var6) {
                  VsanClusterHealthResultRow row = var8[var6];
                  if (row.values[i] != null && !"".equals(row.values[i])) {
                     ColumnType cellType = columnType;
                     String cellValue = row.values[i];
                     if (columnType.equals(ColumnType.dynamic)) {
                        cellType = ColumnType.valueOf(row.values[i].split(":")[0]);
                        cellValue = cellValue.substring(cellType.toString().length() + 1);
                     }

                     ManagedObjectReference moRef;
                     switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType()[cellType.ordinal()]) {
                     case 1:
                        moRef = BaseUtils.generateMor(cellValue, serverGuid);
                        if (moRef != null) {
                           allMoRefs.add(moRef);
                        }
                        break;
                     case 2:
                        String[] var15;
                        int var14 = (var15 = cellValue.split(",")).length;

                        for(int var13 = 0; var13 < var14; ++var13) {
                           String mofStr = var15[var13];
                           moRef = BaseUtils.generateMor(mofStr, serverGuid);
                           if (moRef != null) {
                              allMoRefs.add(moRef);
                           }
                        }
                     }
                  }
               }
            }
         }

      }
   }

   public static Map<ManagedObjectReference, String> getNamesForMoRefs(Set<ManagedObjectReference> objects) {
      if (objects.size() == 0) {
         return new HashMap();
      } else {
         PropertyValue[] propValues = null;

         try {
            propValues = QueryUtil.getProperties((ManagedObjectReference[])objects.toArray(new ManagedObjectReference[objects.size()]), new String[]{"name"}).getPropertyValues();
         } catch (Exception var7) {
            _logger.error("Invalid query parameters are passed." + var7);
         }

         Map<ManagedObjectReference, String> moRefToNameMap = new HashMap();
         if (propValues == null) {
            return moRefToNameMap;
         } else {
            PropertyValue[] var6 = propValues;
            int var5 = propValues.length;

            for(int var4 = 0; var4 < var5; ++var4) {
               PropertyValue propValue = var6[var4];
               moRefToNameMap.put((ManagedObjectReference)propValue.resourceObject, (String)propValue.value);
            }

            return moRefToNameMap;
         }
      }
   }

   public static List<ManagedObjectReference> getClusterConnectedHosts(ManagedObjectReference clusterRef) throws Exception {
      List<ManagedObjectReference> hosts = new ArrayList();
      Constraint dsHostsConstraint = QueryUtil.createConstraintForRelationship(clusterRef, "host", HostSystem.class.getSimpleName());
      Constraint connectedHostsConstraint = QueryUtil.createPropertyConstraint(HostSystem.class.getSimpleName(), "runtime.connectionState", Comparator.EQUALS, ConnectionState.connected.name());
      Constraint dsConnectedHosts = QueryUtil.combineIntoSingleConstraint(new Constraint[]{dsHostsConstraint, connectedHostsConstraint}, Conjoiner.AND);
      QuerySpec qSpec = QueryUtil.buildQuerySpec(dsConnectedHosts, new String[]{"config.product.version"});
      qSpec.name = clusterRef.getValue();
      ResultItem[] resultItems = QueryUtil.getData(qSpec).items;
      if (resultItems == null) {
         return hosts;
      } else {
         ResultItem[] var10 = resultItems;
         int var9 = resultItems.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            ResultItem resultItem = var10[var8];
            hosts.add((ManagedObjectReference)resultItem.resourceObject);
         }

         return hosts;
      }
   }

   public static VsanHealthData getVsanHealthData(VsanClusterHealthSummary healthSummary, Map<ManagedObjectReference, String> moRefToNameMap, boolean isFlat) {
      VsanHealthData healthData = new VsanHealthData();
      healthData.description = healthSummary.overallHealthDescription;
      healthData.status = VsanHealthStatus.valueOf(healthSummary.overallHealth);
      healthData.testsData = new ArrayList();
      int var6;
      if (isFlat) {
         VsanTestData singleGroup = new VsanTestData();
         singleGroup.subtests = new ArrayList();
         VsanClusterHealthGroup[] var8;
         int var7 = (var8 = healthSummary.groups).length;

         for(var6 = 0; var6 < var7; ++var6) {
            VsanClusterHealthGroup healthGroup = var8[var6];
            VsanClusterHealthTest[] var12;
            int var11 = (var12 = healthGroup.groupTests).length;

            for(int var10 = 0; var10 < var11; ++var10) {
               VsanClusterHealthTest healthTest = var12[var10];
               singleGroup.subtests.add(new VsanTestData(healthTest, moRefToNameMap));
            }
         }

         healthData.testsData.add(singleGroup);
      } else {
         VsanClusterHealthGroup[] var15;
         var6 = (var15 = healthSummary.groups).length;

         for(int var14 = 0; var14 < var6; ++var14) {
            VsanClusterHealthGroup healthGroup = var15[var14];
            healthData.testsData.add(new VsanTestData(healthGroup, moRefToNameMap));
         }
      }

      return healthData;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[ColumnType.values().length];

         try {
            var0[ColumnType.Float.ordinal()] = 7;
         } catch (NoSuchFieldError var12) {
         }

         try {
            var0[ColumnType.HostReference.ordinal()] = 9;
         } catch (NoSuchFieldError var11) {
         }

         try {
            var0[ColumnType.Long.ordinal()] = 6;
         } catch (NoSuchFieldError var10) {
         }

         try {
            var0[ColumnType.date.ordinal()] = 12;
         } catch (NoSuchFieldError var9) {
         }

         try {
            var0[ColumnType.dynamic.ordinal()] = 8;
         } catch (NoSuchFieldError var8) {
         }

         try {
            var0[ColumnType.health.ordinal()] = 4;
         } catch (NoSuchFieldError var7) {
         }

         try {
            var0[ColumnType.listMor.ordinal()] = 2;
         } catch (NoSuchFieldError var6) {
         }

         try {
            var0[ColumnType.mor.ordinal()] = 1;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[ColumnType.string.ordinal()] = 5;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[ColumnType.vsanDataProtectionObjectHealth.ordinal()] = 11;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[ColumnType.vsanObjectHealth.ordinal()] = 10;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[ColumnType.vsanObjectUuid.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType = var0;
         return var0;
      }
   }
}
