package com.vmware.vsphere.client.vsan.guardrail;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vim.vsan.binding.vim.vsan.host.RuntimeStats;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanSyncingObjectQueryResult;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.diskmanagement.DiskManagementService;
import com.vmware.vsan.client.services.resyncing.VsanResyncingComponentsProvider;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.FormatUtil;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class GuardRailPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final String VSAN_GUARD_RAIL_RESULT = "clusterGuardRailResult";
   private static final String VSAN_GUARD_RAIL_MESSAGES = "clusterGuardRailMessages";
   public static final String HOST_NAME_SEPARATOR = ", ";
   private static final String[] HOST_PROPERTIES = new String[]{"name", "runtime.connectionState", "runtime.inMaintenanceMode"};
   private static final Log _logger = LogFactory.getLog(GuardRailPropertyProviderAdapter.class);
   @Autowired
   public VsanResyncingComponentsProvider resyncingComponentsProvider;
   @Autowired
   private DiskManagementService diskMgmtService;
   @Autowired
   private VsanClient vsanClient;

   public GuardRailPropertyProviderAdapter(DataServiceExtensionRegistry registry) {
      Validate.notNull(registry);
      TypeInfo clusterInfo = new TypeInfo();
      clusterInfo.type = ClusterComputeResource.class.getSimpleName();
      clusterInfo.properties = new String[]{"clusterGuardRailResult", "clusterGuardRailMessages"};
      TypeInfo[] providedProperties = new TypeInfo[]{clusterInfo};
      registry.registerDataAdapter(this, providedProperties);
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      if (ArrayUtils.isEmpty(propertyRequest.objects)) {
         _logger.warn("Empty propertyRequest.objects is passed");
         return null;
      } else {
         ArrayList resultItems = new ArrayList();

         try {
            String[] propertyNames = QueryUtil.getPropertyNames(propertyRequest.properties);
            Object[] var7;
            int var6 = (var7 = propertyRequest.objects).length;

            for(int var5 = 0; var5 < var6; ++var5) {
               Object obj = var7[var5];
               ManagedObjectReference clusterRef = (ManagedObjectReference)obj;
               List<PropertyValue> propertyValues = new ArrayList();
               GuardRailResult guardRailResult = this.getGuardRailResult(clusterRef);
               String[] var14 = propertyNames;
               int var13 = propertyNames.length;

               for(int var12 = 0; var12 < var13; ++var12) {
                  String property = var14[var12];
                  PropertyValue propertyValue = null;
                  if ("clusterGuardRailMessages".equals(property)) {
                     Map<GuardRailMessageStatus, List<String>> guardRailMessages = this.convertToGuardRailMessages(guardRailResult);
                     propertyValue = QueryUtil.newProperty("clusterGuardRailMessages", guardRailMessages);
                  } else if ("clusterGuardRailResult".equals(property)) {
                     propertyValue = QueryUtil.newProperty("clusterGuardRailResult", guardRailResult);
                  } else {
                     _logger.warn("Unknown property: " + property);
                  }

                  if (propertyValue != null) {
                     propertyValues.add(propertyValue);
                  }
               }

               ResultItem resultItem = new ResultItem();
               resultItem.properties = (PropertyValue[])propertyValues.toArray(new PropertyValue[propertyValues.size()]);
               resultItem.resourceObject = clusterRef;
               resultItems.add(resultItem);
            }
         } catch (Exception var17) {
            _logger.error("Failed to retrieve ClusterGuardRailResult property. ", var17);
            ResultSet resultSet = new ResultSet();
            resultSet.error = new Exception(Utils.getLocalizedString("vsan.guardRail.providerGeneralError"));
            return resultSet;
         }

         ResultSet result = new ResultSet();
         result.items = (ResultItem[])resultItems.toArray(new ResultItem[resultItems.size()]);
         result.totalMatchedObjectCount = resultItems.size();
         return result;
      }
   }

   private Map<GuardRailMessageStatus, List<String>> convertToGuardRailMessages(GuardRailResult guardRailResult) {
      Map<GuardRailMessageStatus, List<String>> messages = new HashMap();
      List<String> warningMessages = new ArrayList();
      List<String> infoMessages = new ArrayList();
      String hostNames;
      if (ArrayUtils.isNotEmpty(guardRailResult.hostsInMaintenanceMode)) {
         hostNames = StringUtils.join(guardRailResult.hostsInMaintenanceMode, ", ");
         warningMessages.add(Utils.getLocalizedString("vsan.guardRail.hostInMaintenanceMode", hostNames));
      }

      if (ArrayUtils.isNotEmpty(guardRailResult.hostsNotConnected)) {
         hostNames = StringUtils.join(guardRailResult.hostsNotConnected, ", ");
         warningMessages.add(Utils.getLocalizedString("vsan.guardRail.hostsNotConnected", hostNames));
      }

      boolean areObjectsResyncing = guardRailResult.isClusterInResync && guardRailResult.objectsToSyncCount != null && guardRailResult.objectsToSyncCount > 0L;
      if (areObjectsResyncing) {
         String message = Utils.getLocalizedString("vsan.guardRail.clusterInResync", String.valueOf(guardRailResult.objectsToSyncCount));
         warningMessages.add(message);
      } else if (guardRailResult.repairTimerData.objectsCount > 0L) {
         long minutesToRepair = FormatUtil.getMinutesFromNow(guardRailResult.repairTimerData.minTimer);
         String message = minutesToRepair <= 1L ? Utils.getLocalizedString("vsan.guardRail.scheduledResync.oneMinute") : Utils.getLocalizedString("vsan.guardRail.scheduledResync", String.valueOf(minutesToRepair));
         warningMessages.add(message);
      }

      if (guardRailResult.hasNetworkPartitioning) {
         warningMessages.add(Utils.getLocalizedString("vsan.guardRail.networkPartitioning"));
      }

      if (!guardRailResult.isAutomaticRebalanceSupported && !guardRailResult.resyncCollected) {
         infoMessages.add(Utils.getLocalizedString("vsan.guardRail.legacyClusterInResync"));
      }

      messages.put(GuardRailMessageStatus.WARNING, warningMessages);
      messages.put(GuardRailMessageStatus.INFO, infoMessages);
      return messages;
   }

   private GuardRailResult getGuardRailResult(ManagedObjectReference clusterRef) throws Exception {
      Measure measure = new Measure("Retrieving guard rail data");
      Future<VSANWitnessHostInfo[]> witnessHostsFuture = this.queryWitnessHost(measure, clusterRef);
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), HOST_PROPERTIES);
      Set<String> hostsInMM = new TreeSet(String.CASE_INSENSITIVE_ORDER);
      Set<String> hostsNotConnected = new TreeSet(String.CASE_INSENSITIVE_ORDER);
      Set<ManagedObjectReference> allHostsRefs = new HashSet();
      boolean isAutomaticRebalanceSupported = VsanCapabilityUtils.isAutomaticRebalanceSupported(clusterRef);
      Future<VsanSyncingObjectQueryResult> vsanSyncingObjectQueryResultFuture = null;
      Iterator var11 = response.getResourceObjects().iterator();

      while(var11.hasNext()) {
         Object obj = var11.next();
         ManagedObjectReference hostRef = (ManagedObjectReference)obj;
         allHostsRefs.add(hostRef);
         boolean isInMaintenanceMode = (Boolean)response.getProperty(hostRef, "runtime.inMaintenanceMode");
         ConnectionState connectionState = (ConnectionState)response.getProperty(hostRef, "runtime.connectionState");
         boolean isConnected = ConnectionState.connected == connectionState;
         if (!isConnected) {
            hostsNotConnected.add((String)response.getProperty(hostRef, "name"));
         }

         if (isInMaintenanceMode) {
            hostsInMM.add((String)response.getProperty(hostRef, "name"));
         } else if (!isAutomaticRebalanceSupported && vsanSyncingObjectQueryResultFuture == null && VsanCapabilityUtils.isResyncEnhancedApiSupported(hostRef) && isConnected) {
            vsanSyncingObjectQueryResultFuture = this.querySyncingVsanObjects(measure, hostRef);
         }
      }

      Future[] repairTimerDataFutures = null;
      if (!isAutomaticRebalanceSupported) {
         repairTimerDataFutures = this.resyncingComponentsProvider.getRepairTimerDataFutures(clusterRef, measure);
      }

      return this.getGuardRailResultProperties(clusterRef, allHostsRefs, hostsInMM, hostsNotConnected, vsanSyncingObjectQueryResultFuture, repairTimerDataFutures, witnessHostsFuture, isAutomaticRebalanceSupported);
   }

   private Future<VSANWitnessHostInfo[]> queryWitnessHost(Measure measure, ManagedObjectReference clusterRef) {
      Future witnessHostsFuture = measure.newFuture("VsanVcStretchedClusterSystem.getWitnessHosts");

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanConnection connection = this.vsanClient.getConnection(clusterRef.getServerGuid());

            try {
               VsanVcStretchedClusterSystem stretchedClusterSystem = connection.getVcStretchedClusterSystem();
               stretchedClusterSystem.getWitnessHosts(clusterRef, witnessHostsFuture);
            } finally {
               if (connection != null) {
                  connection.close();
               }

            }

            return witnessHostsFuture;
         } catch (Throwable var15) {
            if (var4 == null) {
               var4 = var15;
            } else if (var4 != var15) {
               var4.addSuppressed(var15);
            }

            throw var4;
         }
      } catch (Exception var16) {
         throw new VsanUiLocalizableException("vsan.guardRail.witnessHost.error");
      }
   }

   private Future<VsanSyncingObjectQueryResult> querySyncingVsanObjects(Measure measure, ManagedObjectReference hostRef) {
      Future<VsanSyncingObjectQueryResult> future = null;
      VsanSystemEx vsanSystemEx = VsanProviderUtils.getVsanSystemEx(hostRef);
      if (vsanSystemEx != null) {
         future = measure.newFuture("vsanSystemEx.querySyncingVsanObjects");
         vsanSystemEx.querySyncingVsanObjects((String[])null, 0, 1, true, future);
      }

      return future;
   }

   private GuardRailResult getGuardRailResultProperties(ManagedObjectReference clusterRef, Set<ManagedObjectReference> allHostsRefs, Set<String> hostsInMM, Set<String> hostsNotConnected, Future<VsanSyncingObjectQueryResult> vsanSyncingObjectFuture, Future<RuntimeStats>[] repairTimerDataFutures, Future<VSANWitnessHostInfo[]> witnessHostsFuture, boolean isAutomaticRebalanceSupported) throws ExecutionException, InterruptedException {
      GuardRailResult guardRailResult = new GuardRailResult();
      if (CollectionUtils.isNotEmpty(hostsInMM)) {
         guardRailResult.hostsInMaintenanceMode = (String[])hostsInMM.toArray(new String[0]);
      }

      if (CollectionUtils.isNotEmpty(hostsNotConnected)) {
         guardRailResult.hostsNotConnected = (String[])hostsNotConnected.toArray(new String[0]);
      }

      guardRailResult.hasNetworkPartitioning = this.diskMgmtService.hasNetworkPartition(clusterRef, witnessHostsFuture, allHostsRefs);
      guardRailResult.repairTimerData = this.resyncingComponentsProvider.getRepairTimerData(repairTimerDataFutures);
      if (isAutomaticRebalanceSupported) {
         guardRailResult.isAutomaticRebalanceSupported = true;
         return guardRailResult;
      } else {
         guardRailResult.resyncCollected = vsanSyncingObjectFuture != null;
         if (guardRailResult.resyncCollected) {
            VsanSyncingObjectQueryResult vsanSyncingObject = (VsanSyncingObjectQueryResult)vsanSyncingObjectFuture.get();
            if (ArrayUtils.isNotEmpty(vsanSyncingObject.objects)) {
               guardRailResult.isClusterInResync = true;
               guardRailResult.recoveryETA = vsanSyncingObject.totalRecoveryETA;
               guardRailResult.objectsToSyncCount = vsanSyncingObject.totalObjectsToSync;
            }
         }

         return guardRailResult;
      }
   }
}
