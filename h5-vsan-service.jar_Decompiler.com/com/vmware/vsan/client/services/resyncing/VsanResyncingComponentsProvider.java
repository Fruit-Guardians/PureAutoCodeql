package com.vmware.vsan.client.services.resyncing;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.RepairTimerInfo;
import com.vmware.vim.vsan.binding.vim.vsan.RuntimeStatsHostMap;
import com.vmware.vim.vsan.binding.vim.vsan.host.RuntimeStats;
import com.vmware.vim.vsan.binding.vim.vsan.host.StatsType;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.common.data.VmData;
import com.vmware.vsan.client.services.fileservice.VsanFileServiceConfigService;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceShare;
import com.vmware.vsan.client.services.resyncing.data.DelayTimerData;
import com.vmware.vsan.client.services.resyncing.data.HostResyncTrafficData;
import com.vmware.vsan.client.services.resyncing.data.RepairTimerData;
import com.vmware.vsan.client.services.resyncing.data.ResyncMonitorData;
import com.vmware.vsan.client.services.resyncing.data.VsanSyncingObjectsQuerySpec;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectsFilter;
import com.vmware.vsan.client.services.virtualobjects.data.VsanObjectHealthData;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.impl.VsanPropertyProvider;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanResyncingComponentsProvider {
   private static final String RUNTIME_STAT_REPAIR_TIMER = "repairTimerInfo";
   private static final String[] RUNTIME_STATS = new String[]{"repairTimerInfo"};
   private static final int TIMER_DEFAULT_VALUE = 0;
   @Autowired
   private VsanResyncingIscsiTargetComponentsProvider iscsiTargetComponentsProvider;
   @Autowired
   private VsanResyncingComponentsRetriever vsanSyncComponentsRetriever;
   @Autowired
   private VsanFileServiceConfigService fileServiceConfigService;
   @Autowired
   private VsanPropertyProvider vsanPropertyProvider;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;
   private static final Log _logger = LogFactory.getLog(VsanResyncingComponentsProvider.class);
   private static final String RESYNC_THROTTLING_PROPERTY = "vsanResyncThrottling";
   private static final VsanProfiler _profiler = new VsanProfiler(VsanResyncingComponentsProvider.class);

   @TsService
   public HostResyncTrafficData[] getHostsResyncTraffic(ManagedObjectReference clusterRef) throws Exception {
      Map<ManagedObjectReference, HostResyncTrafficData> hostsToResyncTrafficMap = this.getHostsToResyncTrafficMap(clusterRef);
      if (hostsToResyncTrafficMap != null && hostsToResyncTrafficMap.size() != 0) {
         DataServiceResponse response = QueryUtil.getProperties((ManagedObjectReference[])hostsToResyncTrafficMap.keySet().toArray(new ManagedObjectReference[0]), new String[]{"name", "primaryIconId"});
         if (response == null) {
            return new HostResyncTrafficData[0];
         } else {
            Object resourceObject;
            HostResyncTrafficData data;
            for(Iterator var5 = response.getResourceObjects().iterator(); var5.hasNext(); data.primaryIconId = (String)response.getProperty(resourceObject, "primaryIconId")) {
               resourceObject = var5.next();
               data = (HostResyncTrafficData)hostsToResyncTrafficMap.get(resourceObject);
               data.name = (String)response.getProperty(resourceObject, "name");
            }

            return (HostResyncTrafficData[])hostsToResyncTrafficMap.values().toArray(new HostResyncTrafficData[hostsToResyncTrafficMap.size()]);
         }
      } else {
         return new HostResyncTrafficData[0];
      }
   }

   @TsService
   public boolean getIsResyncThrottlingSupported(ManagedObjectReference clusterRef) {
      boolean resyncThrottlingSupported = VsanCapabilityUtils.isResyncThrottlingSupported(clusterRef);
      return resyncThrottlingSupported;
   }

   private Map<ManagedObjectReference, HostResyncTrafficData> getHostsToResyncTrafficMap(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      RuntimeStatsHostMap[] runtimeStats = null;
      Throwable var4 = null;
      RuntimeStatsHostMap stats = null;

      try {
         VsanProfiler.Point point = _profiler.point("vsanConfigSystem.getRuntimeStats");

         try {
            runtimeStats = vsanConfigSystem.getRuntimeStats(clusterRef, new String[]{StatsType.resyncIopsInfo.toString()});
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var16) {
         if (var4 == null) {
            var4 = var16;
         } else if (var4 != var16) {
            var4.addSuppressed(var16);
         }

         throw var4;
      }

      Map<ManagedObjectReference, HostResyncTrafficData> hostToStatsMap = new HashMap();
      if (runtimeStats != null) {
         hostToStatsMap = new HashMap();
         RuntimeStatsHostMap[] var8 = runtimeStats;
         int var7 = runtimeStats.length;

         for(int var18 = 0; var18 < var7; ++var18) {
            stats = var8[var18];
            HostResyncTrafficData data = new HostResyncTrafficData();
            if (stats.stats != null && stats.stats.resyncIopsInfo != null) {
               data.resyncTraffic = stats.stats.resyncIopsInfo.resyncIops;
            } else {
               _logger.warn("Empty stats returned for host: " + stats);
            }

            ManagedObjectReference host = stats.host;
            VmodlHelper.assignServerGuid(host, clusterRef.getServerGuid());
            hostToStatsMap.put(stats.host, data);
         }
      }

      return hostToStatsMap;
   }

   @TsService
   public ResyncMonitorData getResyncingData(ManagedObjectReference clusterRef, int limit) throws Exception {
      return this.getVsanDatastoreResyncingData(clusterRef, limit, (String[])null, (String)null);
   }

   @TsService
   public ResyncMonitorData getResyncingDataForAutoRefresh(ManagedObjectReference clusterRef) throws Exception {
      return this.getVsanDatastoreResyncingData(clusterRef, 0, new String[1], (String)null);
   }

   @TsService
   public ResyncMonitorData getVsanDatastoreResyncingData(ManagedObjectReference clusterRef, int limit, String[] resyncTypes, String resyncStatus) throws Exception {
      if (clusterRef != null && this.isVsanEnabledOnCluster(clusterRef)) {
         _logger.debug("Getting resyncing components on the vsan datastore.");
         VsanSyncingObjectsQuerySpec spec = new VsanSyncingObjectsQuerySpec();
         spec.resyncTypes = resyncTypes;
         spec.status = resyncStatus;
         if (limit >= 0) {
            spec.limit = limit;
         }

         ResyncMonitorData resyncMonitorData = this.vsanSyncComponentsRetriever.getVsanResyncObjects(clusterRef, spec);
         resyncMonitorData.isVsanClusterPartitioned = this.vsanPropertyProvider.getIsVsanClusterPartitioned(clusterRef);
         resyncMonitorData.isResyncThrottlingSupported = this.getIsResyncThrottlingSupported(clusterRef);
         resyncMonitorData.resyncThrottlingValue = (Integer)QueryUtil.getProperty(clusterRef, "vsanResyncThrottling", (Object)null);
         Throwable var7 = null;
         Map vsanUuidToObjectHealthData = null;

         Future[] repairTimerDataFutures;
         try {
            Measure measure = new Measure("Retrieving delay/repair timer data");

            try {
               repairTimerDataFutures = this.getRepairTimerDataFutures(clusterRef, measure);
               Future<ConfigInfoEx> configInfoExFuture = this.getConfigInfoExFuture(clusterRef, measure);
               resyncMonitorData.repairTimerData = this.getRepairTimerData(repairTimerDataFutures);
               resyncMonitorData.delayTimerData = this.getDelayTimerData(configInfoExFuture);
            } finally {
               if (measure != null) {
                  measure.close();
               }

            }
         } catch (Throwable var37) {
            if (var7 == null) {
               var7 = var37;
            } else if (var7 != var37) {
               var7.addSuppressed(var37);
            }

            throw var7;
         }

         if (resyncMonitorData.components == null) {
            _logger.debug("No resyncing components found.");
            return resyncMonitorData;
         } else {
            Map virtualObjectsFilterToObjectIdentities;
            try {
               Throwable var42 = null;
               repairTimerDataFutures = null;

               try {
                  Measure measure = new Measure("Collect Resyncing objects information");

                  try {
                     _logger.info("ResyncMonitorData.getVsanObjectUuids: " + StringUtils.join(resyncMonitorData.getVsanObjectUuids(), ","));
                     VsanAsyncDataRetriever dataRetriever = this.initializeDataRetriever(clusterRef, measure, resyncMonitorData.getVsanObjectUuids());
                     VsanObjectIdentityAndHealth vsanObjectIdentityAndHealth = dataRetriever.getObjectIdentities();
                     virtualObjectsFilterToObjectIdentities = VsanResyncingComponentsUtil.getVirtualObjectsFilterToObjectIdentities(vsanObjectIdentityAndHealth);
                     VsanObjectInformation[] vsanObjectInformations = this.getVsanObjectInformationsFromDataRetriever(clusterRef, dataRetriever);
                     Map<String, String> storagePolicies = dataRetriever.getStoragePolicies();
                     vsanUuidToObjectHealthData = VsanResyncingComponentsUtil.getVsanUuidToObjectHealthData(vsanObjectIdentityAndHealth, vsanObjectInformations, storagePolicies);
                  } finally {
                     if (measure != null) {
                        measure.close();
                     }

                  }
               } catch (Throwable var39) {
                  if (var42 == null) {
                     var42 = var39;
                  } else if (var42 != var39) {
                     var42.addSuppressed(var39);
                  }

                  throw var42;
               }
            } catch (Exception var40) {
               _logger.error("Failed to Collect Resyncing objects information. Returning partial results. ", var40);
               return resyncMonitorData;
            }

            this.buildVms(clusterRef, resyncMonitorData, virtualObjectsFilterToObjectIdentities, vsanUuidToObjectHealthData);
            this.buildIscsiObjects(clusterRef, resyncMonitorData, virtualObjectsFilterToObjectIdentities, vsanUuidToObjectHealthData);
            this.buildFileShares(clusterRef, resyncMonitorData, virtualObjectsFilterToObjectIdentities, vsanUuidToObjectHealthData);
            List<String> orphanedSyncObjects = this.getOrphanedSyncObjects(resyncMonitorData, virtualObjectsFilterToObjectIdentities);
            resyncMonitorData.processOtherObjects((List)virtualObjectsFilterToObjectIdentities.get(VirtualObjectsFilter.OTHERS), orphanedSyncObjects, vsanUuidToObjectHealthData);
            return resyncMonitorData;
         }
      } else {
         return new ResyncMonitorData();
      }
   }

   private void buildVms(ManagedObjectReference clusterRef, ResyncMonitorData resyncMonitorData, Map<VirtualObjectsFilter, List<VsanObjectIdentity>> vsanObjectIdentitiesData, Map<String, VsanObjectHealthData> vsanHealthData) {
      if (!CollectionUtils.isEmpty((Collection)vsanObjectIdentitiesData.get(VirtualObjectsFilter.VMS))) {
         Map<ManagedObjectReference, VmData> vmDataMap = VsanResyncingComponentsUtil.getVmData(clusterRef, (List)vsanObjectIdentitiesData.get(VirtualObjectsFilter.VMS));
         resyncMonitorData.processVmObjects((List)vsanObjectIdentitiesData.get(VirtualObjectsFilter.VMS), vmDataMap, vsanHealthData);
      }

   }

   private void buildIscsiObjects(ManagedObjectReference clusterRef, ResyncMonitorData resyncMonitorData, Map<VirtualObjectsFilter, List<VsanObjectIdentity>> vsanObjectIdentitiesData, Map<String, VsanObjectHealthData> vsanHealthData) {
      List<VsanObjectIdentity> iscsiIdentityData = (List)vsanObjectIdentitiesData.get(VirtualObjectsFilter.ISCSI_TARGETS);
      if (!CollectionUtils.isEmpty(iscsiIdentityData)) {
         Map<String, VsanObject> iscsiObjects = this.iscsiTargetComponentsProvider.getIscsiResyncObjects(clusterRef, resyncMonitorData.getVsanObjectUuids());
         vsanHealthData.putAll(this.getIscsiExtraObjectsHealth(clusterRef, iscsiIdentityData, iscsiObjects));
         resyncMonitorData.processIscsiObjects(iscsiIdentityData, vsanHealthData, iscsiObjects);
      }

   }

   private void buildFileShares(ManagedObjectReference clusterRef, ResyncMonitorData resyncMonitorData, Map<VirtualObjectsFilter, List<VsanObjectIdentity>> vsanObjectIdentitiesData, Map<String, VsanObjectHealthData> vsanHealthData) throws Exception {
      List<VsanObjectIdentity> sharesIdentityData = (List)vsanObjectIdentitiesData.get(VirtualObjectsFilter.FILE_SHARES);
      if (!CollectionUtils.isEmpty(sharesIdentityData)) {
         List<VsanFileServiceShare> allShares = this.fileServiceConfigService.listAllShares(clusterRef);
         List<VsanFileServiceShare> resyncingShares = new ArrayList();
         Set<String> resyncingObjectsUuids = resyncMonitorData.getVsanObjectUuids();
         Iterator var10 = allShares.iterator();

         while(var10.hasNext()) {
            VsanFileServiceShare share = (VsanFileServiceShare)var10.next();
            this.filterOutObjectUuidsFromFileShare(share, resyncingObjectsUuids);
            if (CollectionUtils.isNotEmpty(share.objectUuids)) {
               resyncingShares.add(share);
            }
         }

         resyncMonitorData.processFileShares(sharesIdentityData, vsanHealthData, resyncingShares);
      }

   }

   private void filterOutObjectUuidsFromFileShare(VsanFileServiceShare share, Set<String> uuids) {
      List<String> filteredUuids = new ArrayList();
      Iterator var5 = share.objectUuids.iterator();

      while(var5.hasNext()) {
         String uuid = (String)var5.next();
         if (uuids.contains(uuid)) {
            filteredUuids.add(uuid);
         }
      }

      share.objectUuids = filteredUuids;
   }

   private DelayTimerData getDelayTimerData(Future<ConfigInfoEx> configInfoExFuture) {
      DelayTimerData delayTimerData = new DelayTimerData();
      if (configInfoExFuture == null) {
         delayTimerData.isSupported = false;
      } else {
         delayTimerData.isSupported = true;

         try {
            ConfigInfoEx configInfoEx = (ConfigInfoEx)configInfoExFuture.get();
            if (configInfoEx != null && configInfoEx.getExtendedConfig() != null) {
               delayTimerData.delayTimer = configInfoEx.getExtendedConfig().objectRepairTimer;
            } else {
               delayTimerData.errorMessage = Utils.getLocalizedString("vsan.resyncing.delayTimer.error");
               _logger.error("Cannot retrieve the Delay Timer value because the configuration is null!");
            }
         } catch (Exception var4) {
            delayTimerData.errorMessage = Utils.getLocalizedString("vsan.resyncing.delayTimer.error");
            _logger.error("Cannot retrieve Delay Timer information: ", var4);
         }
      }

      return delayTimerData;
   }

   public RepairTimerData getRepairTimerData(Future<RuntimeStats>[] repairTimerDataFutures) {
      RepairTimerData repairTimerData = new RepairTimerData();
      if (repairTimerDataFutures == null) {
         repairTimerData.isSupported = false;
      } else if (ArrayUtils.isEmpty(repairTimerDataFutures)) {
         repairTimerData.isSupported = true;
      } else {
         repairTimerData.isSupported = true;
         long maxTimer = Long.MIN_VALUE;
         long minTimer = Long.MAX_VALUE;
         long objectsCount = 0L;
         long objectsCountWithRepairTimer = 0L;
         long todayInMilliseconds = (new Date()).getTime();
         Future[] var16 = repairTimerDataFutures;
         int var15 = repairTimerDataFutures.length;

         for(int var14 = 0; var14 < var15; ++var14) {
            Future repairTimerDataFuture = var16[var14];

            try {
               RuntimeStats runtimeStats = (RuntimeStats)repairTimerDataFuture.get();
               RepairTimerInfo repairTimerInfo = runtimeStats.repairTimerInfo;
               if (repairTimerInfo == null) {
                  _logger.warn("No runtime stats received for host!");
               } else if (repairTimerInfo.objectCount <= 0) {
                  _logger.debug("No objects scheduled for resyncing on the host");
               } else {
                  if (repairTimerInfo.maxTimeToRepair >= 0) {
                     maxTimer = Math.max(maxTimer, todayInMilliseconds + (long)(repairTimerInfo.maxTimeToRepair * 1000));
                  }

                  if (repairTimerInfo.minTimeToRepair >= 0) {
                     minTimer = Math.min(minTimer, todayInMilliseconds + (long)(repairTimerInfo.minTimeToRepair * 1000));
                  }

                  objectsCountWithRepairTimer += (long)repairTimerInfo.objectCountWithRepairTimer;
                  objectsCount += (long)repairTimerInfo.objectCount;
               }
            } catch (Exception var19) {
               _logger.error("Cannot retrieve Repair Timer Data: ", var19);
            }
         }

         repairTimerData.maxTimer = maxTimer;
         repairTimerData.minTimer = minTimer;
         repairTimerData.objectsCount = objectsCount;
         repairTimerData.objectsCountWithRepairTimer = objectsCountWithRepairTimer;
         repairTimerData.objectsCountPending = objectsCount - objectsCountWithRepairTimer;
      }

      return repairTimerData;
   }

   public Future<RuntimeStats>[] getRepairTimerDataFutures(ManagedObjectReference clusterRef, Measure measure) throws Exception {
      ManagedObjectReference[] hosts = null;

      try {
         hosts = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host");
      } catch (Exception var11) {
         _logger.warn("Cannot retrieve hosts for cluster: " + clusterRef, var11);
      }

      if (ArrayUtils.isEmpty(hosts)) {
         return new Future[0];
      } else {
         List<Future> futures = new ArrayList(hosts.length);
         ManagedObjectReference[] var8 = hosts;
         int var7 = hosts.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            ManagedObjectReference hostRef = var8[var6];
            if (VsanCapabilityUtils.isRepairTimerInResyncStatsSupported(hostRef)) {
               VsanSystemEx vsanSystemEx = VsanProviderUtils.getVsanSystemEx(hostRef);
               Future<RuntimeStats> future = measure.newFuture("vsanSystemEx.getRuntimeStats");
               vsanSystemEx.getRuntimeStats(RUNTIME_STATS, future);
               futures.add(future);
            }
         }

         if (futures.isEmpty()) {
            return null;
         } else {
            return (Future[])futures.toArray(new Future[0]);
         }
      }
   }

   private Future<ConfigInfoEx> getConfigInfoExFuture(ManagedObjectReference clusterRef, Measure measure) throws Exception {
      if (VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         Future<ConfigInfoEx> result = measure.newFuture("VsanVcClusterConfigSystem.getConfigInfoEx");
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         vsanConfigSystem.getConfigInfoEx(clusterRef, result);
         return result;
      } else {
         return null;
      }
   }

   private Map<String, VsanObjectHealthData> getIscsiExtraObjectsHealth(ManagedObjectReference clusterRef, List<VsanObjectIdentity> iscsiIdentityData, Map<String, VsanObject> iscsiObjects) {
      Map<String, VsanObjectHealthData> extraHealthData = new HashMap();
      if (iscsiObjects != null) {
         Set<String> iscsiExtraIdentities = new HashSet();
         Iterator var7 = iscsiObjects.keySet().iterator();

         while(var7.hasNext()) {
            String iscsiObjectUuid = (String)var7.next();
            boolean matchFound = false;
            Iterator var10 = iscsiIdentityData.iterator();

            while(var10.hasNext()) {
               VsanObjectIdentity iscsiIdentity = (VsanObjectIdentity)var10.next();
               if (iscsiObjectUuid.equals(iscsiIdentity.uuid)) {
                  matchFound = true;
                  break;
               }
            }

            if (!matchFound) {
               iscsiExtraIdentities.add(iscsiObjectUuid);
            }
         }

         if (iscsiExtraIdentities.size() > 0) {
            try {
               Throwable var22 = null;
               var7 = null;

               try {
                  Measure measure = new Measure("Collect ISCSI Resyncing objects information");

                  try {
                     VsanAsyncDataRetriever dataRetriever = this.initializeDataRetriever(clusterRef, measure, iscsiExtraIdentities);
                     VsanObjectIdentityAndHealth vsanObjectIdentityAndHealth = dataRetriever.getObjectIdentities();
                     VsanObjectInformation[] vsanObjectInformations = this.getVsanObjectInformationsFromDataRetriever(clusterRef, dataRetriever);
                     Map<String, String> storagePolicies = dataRetriever.getStoragePolicies();
                     extraHealthData = VsanResyncingComponentsUtil.getVsanUuidToObjectHealthData(vsanObjectIdentityAndHealth, vsanObjectInformations, storagePolicies);
                  } finally {
                     if (measure != null) {
                        measure.close();
                     }

                  }
               } catch (Throwable var20) {
                  if (var22 == null) {
                     var22 = var20;
                  } else if (var22 != var20) {
                     var22.addSuppressed(var20);
                  }

                  throw var22;
               }
            } catch (Exception var21) {
               _logger.error("Failed to Collect ISCSI Resyncing objects information. Returning partial results. ", var21);
            }
         }
      }

      return (Map)extraHealthData;
   }

   private VsanAsyncDataRetriever initializeDataRetriever(ManagedObjectReference clusterRef, Measure measure, Set<String> uuIds) {
      VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, clusterRef).loadObjectIdentities(uuIds).loadStoragePolicies();
      if (!VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
         dataRetriever.loadObjectInformation(uuIds);
      }

      return dataRetriever;
   }

   private VsanObjectInformation[] getVsanObjectInformationsFromDataRetriever(ManagedObjectReference clusterRef, VsanAsyncDataRetriever dataRetriever) throws ExecutionException, InterruptedException {
      VsanObjectInformation[] vsanObjectInformations;
      if (!VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef)) {
         vsanObjectInformations = dataRetriever.getObjectInformation();
      } else {
         vsanObjectInformations = new VsanObjectInformation[0];
      }

      return vsanObjectInformations;
   }

   private List<String> getOrphanedSyncObjects(ResyncMonitorData resyncMonitorData, Map<VirtualObjectsFilter, List<VsanObjectIdentity>> vsanObjectIdentitiesData) {
      List<String> orphanedObjects = new ArrayList();
      Iterator var5 = resyncMonitorData.getVsanObjectUuids().iterator();

      while(var5.hasNext()) {
         String syncObjectUuid = (String)var5.next();
         boolean identityFound = false;
         VirtualObjectsFilter[] var10;
         int var9 = (var10 = VirtualObjectsFilter.values()).length;

         for(int var8 = 0; var8 < var9; ++var8) {
            VirtualObjectsFilter filter = var10[var8];
            if (filter != null && !CollectionUtils.isEmpty((Collection)vsanObjectIdentitiesData.get(filter))) {
               Iterator var12 = ((List)vsanObjectIdentitiesData.get(filter)).iterator();

               while(var12.hasNext()) {
                  VsanObjectIdentity identity = (VsanObjectIdentity)var12.next();
                  if (syncObjectUuid.equals(identity.uuid)) {
                     identityFound = true;
                     break;
                  }
               }

               if (identityFound) {
                  break;
               }
            }
         }

         if (!identityFound) {
            orphanedObjects.add(syncObjectUuid);
         }
      }

      return orphanedObjects;
   }

   private Boolean isVsanEnabledOnCluster(ManagedObjectReference clusterRef) throws Exception {
      return (Boolean)QueryUtil.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled", (Object)null);
   }
}
