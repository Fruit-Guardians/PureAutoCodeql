package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthAction;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultBase;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultColumnInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultRow;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultTable;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultValues;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthTest;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectHealthState;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

@data
public class VsanTestData {
   public String testId;
   public String testName;
   public String testDescription;
   public String testShortDescription;
   public Integer numberOfHealthyEntities;
   public Integer numberOfAllEntities;
   public VsanHealthStatus status;
   public List<VsanTestTable> details;
   public List<VsanTestData> subtests;
   public String helpId;
   public List<VsanClusterHealthAction> actions;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType;

   public VsanTestData() {
   }

   public VsanTestData(VsanClusterHealthGroup healthGroup, Map<ManagedObjectReference, String> moRefToNameMap) {
      this.testName = healthGroup.groupName;
      this.status = VsanHealthStatus.valueOf(healthGroup.groupHealth);
      this.details = this.getTestDetails(healthGroup.groupDetails, moRefToNameMap);
      this.subtests = new ArrayList();
      if (healthGroup.groupTests != null) {
         VsanClusterHealthTest[] var6;
         int var5 = (var6 = healthGroup.groupTests).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthTest test = var6[var4];
            this.subtests.add(new VsanTestData(test, moRefToNameMap));
         }
      }

   }

   public VsanTestData(VsanClusterHealthTest test, Map<ManagedObjectReference, String> moRefToNameMap) {
      this.testId = this.wrapTestId(test.testId);
      this.testName = test.testName;
      this.testDescription = test.testDescription;
      this.testShortDescription = test.testShortDescription;
      this.numberOfHealthyEntities = test.testHealthyEntities;
      this.numberOfAllEntities = test.testAllEntities;
      this.status = VsanHealthStatus.valueOf(test.testHealth);
      this.details = this.getTestDetails(test.testDetails, moRefToNameMap);
      this.helpId = test.testId;
      if (test.testActions != null) {
         this.actions = new ArrayList(test.testActions.length);
         VsanClusterHealthAction[] var6;
         int var5 = (var6 = test.testActions).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthAction vlha = var6[var4];
            this.actions.add(vlha);
         }
      }

   }

   private String wrapTestId(String testId) {
      if (StringUtils.isEmpty(testId)) {
         return "";
      } else {
         String[] substrs = StringUtils.split(testId, ".");
         return substrs[substrs.length - 1];
      }
   }

   private List<VsanTestTable> getTestDetails(VsanClusterHealthResultBase[] testDetails, Map<ManagedObjectReference, String> moRefToNameMap) {
      if (testDetails == null) {
         return new ArrayList();
      } else {
         List<VsanTestTable> testTables = new ArrayList();
         VsanClusterHealthResultBase[] var7 = testDetails;
         int var6 = testDetails.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VsanClusterHealthResultBase baseResult = var7[var5];
            VsanTestTable testTable = null;
            if (baseResult instanceof VsanClusterHealthResultTable) {
               testTable = this.createTestTable((VsanClusterHealthResultTable)baseResult, moRefToNameMap);
            } else if (baseResult instanceof VsanClusterHealthResultValues) {
               testTable = this.createTestTable((VsanClusterHealthResultValues)baseResult);
            }

            testTables.add(testTable);
         }

         return testTables;
      }
   }

   private VsanTestTable createTestTable(VsanClusterHealthResultValues parameters) {
      VsanTestTable testTable = new VsanTestTable();
      testTable.showHeader = false;
      testTable.title = parameters.label;
      VsanTestColumn column = new VsanTestColumn("", ColumnType.string);
      testTable.columns = new VsanTestColumn[]{column};
      List<VsanTestRow> rows = new ArrayList();
      if (parameters.values == null) {
         return testTable;
      } else {
         String[] var8;
         int var7 = (var8 = parameters.values).length;

         for(int var6 = 0; var6 < var7; ++var6) {
            String parameter = var8[var6];
            rows.add(this.createVsanTestRow(parameter));
         }

         testTable.rows = (VsanTestRow[])rows.toArray(new VsanTestRow[rows.size()]);
         return testTable;
      }
   }

   private VsanTestTable createTestTable(VsanClusterHealthResultTable tableResult, Map<ManagedObjectReference, String> moRefToNameMap) {
      VsanTestTable testTable = new VsanTestTable();
      testTable.showHeader = true;
      testTable.title = tableResult.label;
      testTable.columns = new VsanTestColumn[tableResult.columns.length];

      for(int i = 0; i < tableResult.columns.length; ++i) {
         VsanClusterHealthResultColumnInfo columnInfo = tableResult.columns[i];

         try {
            ColumnType columnType = ColumnType.valueOf(columnInfo.type);
            testTable.columns[i] = new VsanTestColumn(columnInfo.label, columnType);
         } catch (Exception var10) {
         }
      }

      if (tableResult.rows == null) {
         return testTable;
      } else {
         List<VsanTestRow> rows = new ArrayList();
         VsanClusterHealthResultRow[] var8;
         int var7 = (var8 = tableResult.rows).length;

         for(int var13 = 0; var13 < var7; ++var13) {
            VsanClusterHealthResultRow resultRow = var8[var13];
            VsanTestRow row = this.createVsanTestRow(resultRow, testTable.columns, moRefToNameMap);
            rows.add(row);
         }

         testTable.rows = (VsanTestRow[])rows.toArray(new VsanTestRow[rows.size()]);
         return testTable;
      }
   }

   private String getServerGuid(Map<ManagedObjectReference, String> moRefToNameMap) {
      return moRefToNameMap != null && !moRefToNameMap.isEmpty() ? ((ManagedObjectReference)moRefToNameMap.keySet().iterator().next()).getServerGuid() : null;
   }

   private ObjectWithName getObjectWithName(String morString, Map<ManagedObjectReference, String> moRefToNameMap, String serverGuid) {
      ManagedObjectReference moRef = BaseUtils.generateMor(morString, serverGuid);
      String name = (String)moRefToNameMap.get(moRef);
      return new ObjectWithName(name, moRef);
   }

   private VsanTestRow createVsanTestRow(VsanClusterHealthResultRow resultRow, VsanTestColumn[] columns, Map<ManagedObjectReference, String> moRefToNameMap) {
      String serverGuid = this.getServerGuid(moRefToNameMap);
      VsanTestCell[] values = new VsanTestCell[resultRow.values.length];

      for(int i = 0; i < resultRow.values.length; ++i) {
         String rowValue = resultRow.values[i];
         if (StringUtils.isEmpty(rowValue)) {
            values[i] = new VsanTestCell();
         } else {
            ColumnType cellType = columns[i].columnType;
            if (ColumnType.dynamic.equals(cellType)) {
               try {
                  cellType = ColumnType.valueOf(rowValue.split(":")[0]);
                  rowValue = rowValue.substring(cellType.toString().length() + 1);
               } catch (Exception var15) {
                  continue;
               }
            }

            Object cellValue = null;
            switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$health$ColumnType()[cellType.ordinal()]) {
            case 1:
               if (rowValue != null && rowValue.length() > 0 && serverGuid != null) {
                  cellValue = this.getObjectWithName(rowValue, moRefToNameMap, serverGuid);
               }
               break;
            case 2:
               if (rowValue != null && rowValue.length() > 0 && serverGuid != null) {
                  List<ObjectWithName> listMofs = new ArrayList();
                  String[] var14;
                  int var13 = (var14 = rowValue.split(",")).length;

                  for(int var12 = 0; var12 < var13; ++var12) {
                     String mofStr = var14[var12];
                     listMofs.add(this.getObjectWithName(mofStr, moRefToNameMap, serverGuid));
                  }

                  cellValue = listMofs;
               }
               break;
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            default:
               cellValue = rowValue;
               break;
            case 4:
               cellValue = VsanHealthStatus.valueOf(rowValue);
               break;
            case 10:
               cellValue = VsanObjectHealthState.fromServerLocalizedString(rowValue);
               break;
            case 11:
               cellValue = VsanObjectDataProtectionHealthState.fromServerLocalizedString(rowValue);
            }

            values[i] = new VsanTestCell(cellType, cellValue);
         }
      }

      VsanTestRow row = new VsanTestRow();
      row.rowValues = values;
      row.nestedRows = this.getNestedRows(resultRow, columns, moRefToNameMap);
      return row;
   }

   private VsanTestRow createVsanTestRow(String value) {
      VsanTestRow row = new VsanTestRow();
      VsanTestCell cell = new VsanTestCell(ColumnType.string, value);
      row.rowValues = new VsanTestCell[]{cell};
      return row;
   }

   private List<VsanTestRow> getNestedRows(VsanClusterHealthResultRow resultRow, VsanTestColumn[] columns, Map<ManagedObjectReference, String> moRefToNameMap) {
      List<VsanTestRow> nestedRows = new ArrayList();
      if (resultRow.nestedRows == null) {
         return nestedRows;
      } else {
         VsanClusterHealthResultRow[] var8;
         int var7 = (var8 = resultRow.nestedRows).length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VsanClusterHealthResultRow nestedResultRow = var8[var6];
            VsanTestRow nestedRow = this.createVsanTestRow(nestedResultRow, columns, moRefToNameMap);
            nestedRows.add(nestedRow);
         }

         return nestedRows;
      }
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
