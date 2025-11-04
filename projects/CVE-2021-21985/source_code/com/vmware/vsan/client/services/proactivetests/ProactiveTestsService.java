package com.vmware.vsan.client.services.proactivetests;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterCreateVmHealthTestResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultBase;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultRow;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthResultTable;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterNetworkLoadTestResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.health.ProactiveTestData;
import com.vmware.vsphere.client.vsan.health.VsanTestData;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

@Component
public class ProactiveTestsService {
   private static final Log _logger = LogFactory.getLog(ProactiveTestsService.class);
   private static final VsanProfiler _profiler = new VsanProfiler(ProactiveTestsService.class);
   private static final String CREATE_VM_TEST_HELP_ID = "com.vmware.vsan.health.test.createvmtest";
   private static final String NETWORKPERFTEST_HELPID = "com.vmware.vsan.health.test.networkperftest";
   private static final String UNICASTPERFTEST_HELPID = "com.vmware.vsan.health.test.unicastperftest";
   private static final String UNSUPPORT_UNICAST_HOST_VERSION = "6.7.0";
   private static final String MOR_PATTERN = "^(mor:).*$";

   @TsService
   public List<ProactiveTestData> getProactiveTestResults(ManagedObjectReference clusterRef) throws Exception {
      List<ProactiveTestData> results = new ArrayList();
      ProactiveTestData vmCreationTestResult = this.getLastVmCreationTestResult(clusterRef);
      if (vmCreationTestResult != null) {
         results.add(vmCreationTestResult);
      }

      ProactiveTestData networkTestResult = null;
      if (VsanCapabilityUtils.isNetworkPerfTestSupportedOnCluster(clusterRef)) {
         networkTestResult = this.getLastNetworkTestResult(clusterRef);
      }

      if (networkTestResult != null) {
         results.add(networkTestResult);
      }

      return results;
   }

   private ProactiveTestData getLastVmCreationTestResult(ManagedObjectReference param1) {
      // $FF: Couldn't be decompiled
   }

   private ProactiveTestData getLastNetworkTestResult(ManagedObjectReference clusterRef) throws Exception {
      ProactiveTestData result = null;

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterNetworkPerfHistoryTest");

            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               VsanClusterNetworkLoadTestResult[] networkLoadResults = healthSystem.queryClusterNetworkPerfHistoryTest(clusterRef, 1);
               if (!ArrayUtils.isEmpty(networkLoadResults) && "com.vmware.vsan.health.test.unicastperftest".equals(networkLoadResults[0].clusterResult.healthTest.testId)) {
                  result = this.createNetworkLoadTestResult(clusterRef, networkLoadResults[0]);
               } else {
                  result = this.createEmptyNetworkLoadTestResult();
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }

            return result;
         } catch (Throwable var15) {
            if (var3 == null) {
               var3 = var15;
            } else if (var3 != var15) {
               var3.addSuppressed(var15);
            }

            throw var3;
         }
      } catch (Exception var16) {
         _logger.error("Unable to get network test history results.", var16);
         throw new VsanUiLocalizableException("vsan.proactive.tests.network.history.error");
      }
   }

   @TsService
   public ProactiveTestData getVMCreationTestResult(ManagedObjectReference clusterRef, int timeout) throws Exception {
      ProactiveTestData data = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterCreateVmHealthTest");

            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               VsanClusterCreateVmHealthTestResult vmHealthTestResult = healthSystem.queryClusterCreateVmHealthTest(clusterRef, timeout);
               if (vmHealthTestResult != null && vmHealthTestResult.clusterResult != null) {
                  data = this.createVMCreationTestResult(clusterRef, vmHealthTestResult);
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }

            return data;
         } catch (Throwable var16) {
            if (var4 == null) {
               var4 = var16;
            } else if (var4 != var16) {
               var4.addSuppressed(var16);
            }

            throw var4;
         }
      } catch (Exception var17) {
         _logger.error("Unable to get the VM creation test result.", var17);
         throw new VsanUiLocalizableException("vsan.proactive.tests.vmcreation.test.result.error");
      }
   }

   @TsService
   public ProactiveTestData getNetworkPerfTestResult(ManagedObjectReference clusterRef, boolean isMulticast) throws Exception {
      ProactiveTestData data = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterNetworkPerfTest");

            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               VsanClusterNetworkLoadTestResult networkLoadResults = healthSystem.queryClusterNetworkPerfTest(clusterRef, isMulticast, (Integer)null);
               if (networkLoadResults != null && networkLoadResults.clusterResult != null) {
                  data = this.createNetworkLoadTestResult(clusterRef, networkLoadResults);
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }

            return data;
         } catch (Throwable var16) {
            if (var4 == null) {
               var4 = var16;
            } else if (var4 != var16) {
               var4.addSuppressed(var16);
            }

            throw var4;
         }
      } catch (Exception var17) {
         _logger.error("Unable to get the network load test result.", var17);
         throw new VsanUiLocalizableException("vsan.proactive.tests.network.test.result.error");
      }
   }

   private ProactiveTestData createVMCreationTestResult(ManagedObjectReference clusterRef, VsanClusterCreateVmHealthTestResult vmHealthTestResult) {
      Set<ManagedObjectReference> moRefs = new HashSet();
      VsanHealthUtil.addToTestMoRefsFromBaseResults(vmHealthTestResult.clusterResult.healthTest.testDetails, moRefs, clusterRef.getServerGuid());
      ProactiveTestData data = new ProactiveTestData();
      data.generalData = new VsanTestData(vmHealthTestResult.clusterResult.healthTest, VsanHealthUtil.getNamesForMoRefs(moRefs));
      data.timestamp = vmHealthTestResult.clusterResult.timestamp.getTimeInMillis();
      data.perfTestType = ProactiveTestData.PerfTestType.vmCreation;
      data.helpId = "com.vmware.vsan.health.test.createvmtest";
      return data;
   }

   private ProactiveTestData createEmptyVMCreationTestResult() {
      ProactiveTestData result = new ProactiveTestData();
      result.generalData = new VsanTestData();
      result.perfTestType = ProactiveTestData.PerfTestType.vmCreation;
      result.helpId = "com.vmware.vsan.health.test.createvmtest";
      return result;
   }

   private ProactiveTestData createEmptyNetworkLoadTestResult() {
      ProactiveTestData result = new ProactiveTestData();
      result.generalData = new VsanTestData();
      result.helpId = "com.vmware.vsan.health.test.unicastperftest";
      result.perfTestType = ProactiveTestData.PerfTestType.unicast;
      return result;
   }

   private ProactiveTestData createNetworkLoadTestResult(ManagedObjectReference clusterRef, VsanClusterNetworkLoadTestResult networkLoadTestResult) {
      Set<ManagedObjectReference> moRefs = new HashSet();
      VsanHealthUtil.addToTestMoRefsFromBaseResults(networkLoadTestResult.clusterResult.healthTest.testDetails, moRefs, clusterRef.getServerGuid());
      ProactiveTestData data = new ProactiveTestData();
      data.generalData = new VsanTestData(networkLoadTestResult.clusterResult.healthTest, VsanHealthUtil.getNamesForMoRefs(moRefs));
      data.timestamp = networkLoadTestResult.clusterResult.timestamp.getTimeInMillis();
      data.perfTestType = ProactiveTestData.PerfTestType.unicast;
      data.helpId = "com.vmware.vsan.health.test.unicastperftest";
      return data;
   }

   private void filterMissingHostHealthTests(VsanClusterCreateVmHealthTestResult testResult) {
      VsanClusterHealthResultBase[] testDetails = testResult.clusterResult.healthTest.testDetails;
      if (!ArrayUtils.isEmpty(testDetails)) {
         VsanClusterHealthResultBase[] var6 = testDetails;
         int var5 = testDetails.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanClusterHealthResultBase testDetail = var6[var4];
            if (testDetail instanceof VsanClusterHealthResultTable) {
               VsanClusterHealthResultTable table = (VsanClusterHealthResultTable)testDetail;
               if (!ArrayUtils.isEmpty(table.rows)) {
                  List<VsanClusterHealthResultRow> availableRows = new ArrayList();
                  VsanClusterHealthResultRow[] var12;
                  int var11 = (var12 = table.rows).length;

                  for(int var10 = 0; var10 < var11; ++var10) {
                     VsanClusterHealthResultRow row = var12[var10];
                     if (ArrayUtils.isNotEmpty(row.values) && StringUtils.isNotBlank(row.values[0])) {
                        boolean isHostInCluster = row.values[0].matches("^(mor:).*$");
                        if (isHostInCluster) {
                           availableRows.add(row);
                        }
                     }
                  }

                  table.rows = (VsanClusterHealthResultRow[])availableRows.toArray(new VsanClusterHealthResultRow[0]);
               }
            }
         }

      }
   }
}
