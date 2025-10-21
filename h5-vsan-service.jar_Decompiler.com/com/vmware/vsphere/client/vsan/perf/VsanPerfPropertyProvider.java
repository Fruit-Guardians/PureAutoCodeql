package com.vmware.vsphere.client.vsan.perf;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.KeyValue;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.dvs.HostMember.PnicBacking;
import com.vmware.vim.binding.vim.dvs.HostMember.PnicSpec;
import com.vmware.vim.binding.vim.fault.Timedout;
import com.vmware.vim.binding.vim.fault.VimFault;
import com.vmware.vim.binding.vim.host.HostProxySwitch;
import com.vmware.vim.binding.vim.host.NetworkInfo;
import com.vmware.vim.binding.vim.host.PortGroup;
import com.vmware.vim.binding.vim.host.VirtualNic;
import com.vmware.vim.binding.vim.host.HostProxySwitch.HostLagConfig;
import com.vmware.vim.binding.vim.host.NetworkPolicy.NicOrderPolicy;
import com.vmware.vim.binding.vim.host.PortGroup.Port;
import com.vmware.vim.binding.vim.vm.DefinedProfileSpec;
import com.vmware.vim.binding.vim.vm.device.VirtualController;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice.FileBackingInfo;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo.NetworkInfo.PortConfig;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.RuntimeFault;
import com.vmware.vim.binding.vmodl.fault.InvalidArgument;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityMetricCSV;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityType;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfNodeInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfQuerySpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfTimeRange;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfTimeRangeQuerySpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerformanceManager;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfsvcConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsan.client.util.retriever.VsanAsyncDataRetriever;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.providers.VsanIscsiPropertyProvider;
import com.vmware.vsphere.client.vsan.perf.model.ActiveVmnicDataSpec;
import com.vmware.vsphere.client.vsan.perf.model.CapacityHistoryBasicInfo;
import com.vmware.vsphere.client.vsan.perf.model.DiskGroup;
import com.vmware.vsphere.client.vsan.perf.model.EntityPerfStateObject;
import com.vmware.vsphere.client.vsan.perf.model.HostDiskGroupsData;
import com.vmware.vsphere.client.vsan.perf.model.HostPnicsData;
import com.vmware.vsphere.client.vsan.perf.model.HostVnicsData;
import com.vmware.vsphere.client.vsan.perf.model.PerfEntityStateData;
import com.vmware.vsphere.client.vsan.perf.model.PerfGraphMetricsData;
import com.vmware.vsphere.client.vsan.perf.model.PerfMetricsInfo;
import com.vmware.vsphere.client.vsan.perf.model.PerfMonitorCommonPropsData;
import com.vmware.vsphere.client.vsan.perf.model.PerfPhysicalAdapterEntity;
import com.vmware.vsphere.client.vsan.perf.model.PerfQuerySpec;
import com.vmware.vsphere.client.vsan.perf.model.PerfStatsObjectInfo;
import com.vmware.vsphere.client.vsan.perf.model.PerfTimeRangeData;
import com.vmware.vsphere.client.vsan.perf.model.PerfVirtualDiskEntity;
import com.vmware.vsphere.client.vsan.perf.model.PerfVirtualMachineDiskData;
import com.vmware.vsphere.client.vsan.perf.model.PerfVnicEntity;
import com.vmware.vsphere.client.vsan.perf.model.PerfVscsiEntity;
import com.vmware.vsphere.client.vsan.perf.model.ServerObjectInfo;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class VsanPerfPropertyProvider {
   private static final Log _logger = LogFactory.getLog(VsanPerfMutationProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanPerfPropertyProvider.class);
   private static final String PBM_RELATION = "pbmProfiles";
   private static final String PBM_TARGET_TYPE = "PbmRequirementStorageProfile";
   private static final String PBM_PROFILE_PROP = "profileContent";
   private static final String DATASTORE_URL_PREFIX = "ds://";
   private static final String DATASTORE_URL_PROP = "summary.url";
   private static final String NAME_PROP = "name";
   private static final String HOST_CONFIG_NETWORK_PROPERTY = "config.network";
   private static final String NETWORK_PROPERTY = "network";
   private static final String ACTIVE_UPLINK_PORT_PROPERTY = "config.defaultPortConfig.uplinkTeamingPolicy.uplinkPortOrder.activeUplinkPort";
   private static final String DISTRIBUTED_VIRTUAL_SWITCH_PROPERTY = "config.distributedVirtualSwitch";
   private static final String HOST_VSANCONFIG_DISK_MAPPING_PROPERTY = "config.vsanHostConfig.storageInfo.diskMapping";
   private static final String CLUSTER_PROPERTY = "cluster";
   private static final String VSAN_HOST_CONFIG_NETWORKINFO_PORT_PROPERTY = "config.vsanHostConfig.networkInfo.port";
   private static final String HOST_CONFIG_NETWORK_VNIC_PROPERTY = "config.network.vnic";
   private static final String VM_CONFIG_UUID_PROPERTY = "config.instanceUuid";
   private static final long MILISECONDS_IN_HOUR = 3600000L;
   private static final String CAPACITY_HISTORY_DEDUPLICATION_KEY = "savedByDedup";
   private static final String CAPACITY_HISTORY_DEDUPLICATION_RATIO_KEY = "dedupRatio";
   private static final String CAPACITY_ENTITY_TYPE = "vsan-cluster-capacity";
   private static final String DATA_PROTECTION_ENTITY_TYPE = "cluster-dataprotection";
   private static final String TOTAL_DP_OVERHEAD_KEY = "totalDpOverhead";
   private static final String WILDCARDS_ENTITY_ID = ":*";
   private static final String ENTITY_REF_ID_KEY = "entityRefId";
   @Autowired
   private PermissionService permissionService;
   @Autowired
   private VsanIscsiPropertyProvider iscsiPropertyProvider;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;

   @TsService
   public List<ServerObjectInfo> getEntitiesInfo(ManagedObjectReference clusterRef) throws Exception {
      List<ServerObjectInfo> entities = new ArrayList();
      Map<Object, Map<String, Object>> result = QueryUtil.getProperties(clusterRef, new String[]{"name", "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.uuid"}).getMap();
      Map<String, Object> properties = (Map)result.get(clusterRef);
      ServerObjectInfo clusterInfo = new ServerObjectInfo();
      clusterInfo.isCluster = true;
      clusterInfo.name = (String)properties.get("name");
      clusterInfo.vsanUuid = (String)properties.get("configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.uuid");
      entities.add(clusterInfo);
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"name", "config.vsanHostConfig.clusterInfo.nodeUuid"});
      if (response == null) {
         return entities;
      } else {
         Iterator var8 = response.getResourceObjects().iterator();

         while(var8.hasNext()) {
            Object resourceObject = var8.next();
            ManagedObjectReference hostRef = (ManagedObjectReference)resourceObject;
            ServerObjectInfo hostInfo = new ServerObjectInfo();
            hostInfo.isCluster = false;
            hostInfo.name = (String)response.getProperty(hostRef, "name");
            hostInfo.vsanUuid = (String)response.getProperty(hostRef, "config.vsanHostConfig.clusterInfo.nodeUuid");
            entities.add(hostInfo);
         }

         return entities;
      }
   }

   @TsService
   public List<PerfEntityStateData> getEntityPerfStateForWildcards(ManagedObjectReference clusterRef, PerfQuerySpec[] specs) throws Exception {
      Map<String, PerfEntityStateData> entitiesDataMap = new HashMap();
      PerfQuerySpec[] var7 = specs;
      int var6 = specs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PerfQuerySpec spec = var7[var5];
         List<PerfEntityStateData> stateDataList = new ArrayList();
         long startTime = spec.startTime;

         for(long endTime = startTime + 3600000L; endTime <= spec.endTime; endTime += 3600000L) {
            PerfQuerySpec tempSpec = new PerfQuerySpec();
            tempSpec.startTime = startTime;
            tempSpec.endTime = endTime;
            tempSpec.entityType = spec.entityType;
            tempSpec.entityUuid = spec.entityUuid;
            stateDataList.addAll(this.getEntityPerfState(clusterRef, new PerfQuerySpec[]{tempSpec}));
            startTime = endTime;
         }

         if (!CollectionUtils.isEmpty(stateDataList)) {
            entitiesDataMap = this.aggregateStateDataAndUpdateMap(stateDataList, (Map)entitiesDataMap);
         }
      }

      return new ArrayList(((Map)entitiesDataMap).values());
   }

   private Map<String, PerfEntityStateData> aggregateStateDataAndUpdateMap(List<PerfEntityStateData> stateDataList, Map<String, PerfEntityStateData> entitiesDataMap) {
      Iterator var4 = stateDataList.iterator();

      while(var4.hasNext()) {
         PerfEntityStateData stateData = (PerfEntityStateData)var4.next();
         PerfEntityStateData aggregatedStateData = (PerfEntityStateData)entitiesDataMap.get(stateData.entityRefId);
         if (aggregatedStateData == null) {
            aggregatedStateData = new PerfEntityStateData();
            aggregatedStateData.metricsSeries = new ArrayList();
            aggregatedStateData.timeStamps = new ArrayList();
         }

         if (aggregatedStateData.timeStamps.size() > 0 && stateData.timeStamps.size() > 0 && ((String)aggregatedStateData.timeStamps.get(aggregatedStateData.timeStamps.size() - 1)).equalsIgnoreCase((String)stateData.timeStamps.get(0))) {
            stateData.timeStamps.remove(0);
            stateData.metricsSeries = this.removeFirstPoint(stateData.metricsSeries);
         }

         aggregatedStateData.entityRefId = stateData.entityRefId;
         aggregatedStateData.metricsSeries = this.aggregateMetricsData(aggregatedStateData.metricsSeries, stateData.metricsSeries);
         aggregatedStateData.timeStamps.addAll(stateData.timeStamps);
         aggregatedStateData.metricsCollectInterval = stateData.metricsCollectInterval;
         entitiesDataMap.put(stateData.entityRefId, aggregatedStateData);
      }

      return entitiesDataMap;
   }

   private List<PerfGraphMetricsData> removeFirstPoint(List<PerfGraphMetricsData> metricsSeries) {
      if (metricsSeries.isEmpty()) {
         return metricsSeries;
      } else {
         Iterator var3 = metricsSeries.iterator();

         while(var3.hasNext()) {
            PerfGraphMetricsData data = (PerfGraphMetricsData)var3.next();
            if (data != null && !data.values.isEmpty()) {
               data.values.remove(0);
            }
         }

         return metricsSeries;
      }
   }

   private List<PerfGraphMetricsData> aggregateMetricsData(List<PerfGraphMetricsData> base, List<PerfGraphMetricsData> newMetrics) {
      if (CollectionUtils.isEmpty(base)) {
         return newMetrics;
      } else {
         Iterator var4 = base.iterator();

         while(true) {
            while(var4.hasNext()) {
               PerfGraphMetricsData data = (PerfGraphMetricsData)var4.next();
               Iterator var6 = newMetrics.iterator();

               while(var6.hasNext()) {
                  PerfGraphMetricsData newData = (PerfGraphMetricsData)var6.next();
                  if (newData.key.equals(data.key)) {
                     data.values.addAll(newData.values);
                     break;
                  }
               }
            }

            return base;
         }
      }
   }

   @TsService
   public List<HostDiskGroupsData> getClusterDiskMappings(ManagedObjectReference clusterRef) throws Exception {
      List<HostDiskGroupsData> hostDiskgroups = new ArrayList();
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"name", "config.vsanHostConfig.storageInfo.diskMapping"});
      if (response == null) {
         return hostDiskgroups;
      } else {
         Iterator var5 = response.getResourceObjects().iterator();

         while(true) {
            ManagedObjectReference hostRef;
            DiskMapping[] diskMappings;
            do {
               if (!var5.hasNext()) {
                  return hostDiskgroups;
               }

               Object resourceObject = var5.next();
               hostRef = (ManagedObjectReference)resourceObject;
               diskMappings = (DiskMapping[])response.getProperty(hostRef, "config.vsanHostConfig.storageInfo.diskMapping");
            } while(ArrayUtils.isEmpty(diskMappings));

            List<DiskGroup> groups = new ArrayList();
            DiskMapping[] var12 = diskMappings;
            int var11 = diskMappings.length;

            for(int var10 = 0; var10 < var11; ++var10) {
               DiskMapping diskMapping = var12[var10];
               groups.add(DiskGroup.fromDiskMapping(diskMapping));
            }

            HostDiskGroupsData diskgroupData = new HostDiskGroupsData();
            diskgroupData.hostName = (String)response.getProperty(hostRef, "name");
            diskgroupData.diskgroups = groups;
            hostDiskgroups.add(diskgroupData);
         }
      }
   }

   @TsService
   public CapacityHistoryBasicInfo getCapacityHistoryBasicInfo(ManagedObjectReference objectRef) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      CapacityHistoryBasicInfo info = new CapacityHistoryBasicInfo();
      info.clusterRef = clusterRef;
      info.isEmptyCluster = this.isEmptyCluster(clusterRef);
      info.entityTypes = this.getPerfEntityTypes(clusterRef);
      info.isPerformanceServiceEnabled = this.getPerfServiceEnabled(clusterRef);
      info.isLocalDataProtectionSupported = VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef);
      if (!info.isPerformanceServiceEnabled) {
         ManagedObjectReference vcRoot = VmodlHelper.getRootFolder(clusterRef.getServerGuid());
         ManagedObjectReference[] refs = new ManagedObjectReference[]{clusterRef, vcRoot};
         String[] privileges = new String[]{"Host.Inventory.EditCluster", "StorageProfile.View"};
         info.hasEditPolicyPermission = this.permissionService.havePermissions(refs, privileges);
      }

      return info;
   }

   private boolean isEmptyCluster(ManagedObjectReference clusterRef) {
      Integer clusterHosts = null;

      try {
         clusterHosts = (Integer)QueryUtil.getProperty(clusterRef, "host._length", (Object)null);
      } catch (Exception var4) {
         _logger.warn("Failed to get host count for cluster: " + clusterRef, var4);
      }

      if (clusterHosts != null) {
         return clusterHosts == 0;
      } else {
         return true;
      }
   }

   @TsService
   public PerfEntityStateData getHistoricalSpaceReport(ManagedObjectReference objectRef, PerfQuerySpec[] specs, boolean sparseMetrics) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      Validate.notNull(clusterRef);
      boolean dedupEnabled = false;
      String uuid = "";
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);

      PerfEntityStateData capacityChart;
      try {
         Throwable var8 = null;
         capacityChart = null;

         try {
            VsanProfiler.Point point = _profiler.point("VsanVcClusterConfigSystem.getConfigInfoEx");

            try {
               ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
               if (configInfoEx.getDataEfficiencyConfig() != null) {
                  dedupEnabled = configInfoEx.getDataEfficiencyConfig().dedupEnabled;
               }

               if (configInfoEx.getDefaultConfig() == null || configInfoEx.getDefaultConfig().getUuid() == null) {
                  _logger.error("Failed to retrieve uuid for cluster: " + clusterRef);
                  throw new VsanUiLocalizableException("vsan.perf.query.uuid.error");
               }

               uuid = configInfoEx.getDefaultConfig().getUuid();
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var20) {
            if (var8 == null) {
               var8 = var20;
            } else if (var8 != var20) {
               var8.addSuppressed(var20);
            }

            throw var8;
         }
      } catch (Exception var21) {
         _logger.error("Unable to query cluster configuration for cluster: " + clusterRef, var21);
         throw new VsanUiLocalizableException("vsan.common.cluster.configuration.error");
      }

      List<PerfEntityStateData> allCharts = this.getAllChartsForCapacityHistory(clusterRef, specs, uuid);
      if (allCharts == null) {
         return null;
      } else {
         capacityChart = null;
         PerfEntityStateData dpChart = null;
         Iterator var12 = allCharts.iterator();

         while(var12.hasNext()) {
            PerfEntityStateData chart = (PerfEntityStateData)var12.next();
            if (chart.entityRefId.indexOf("vsan-cluster-capacity") > -1) {
               if (!this.isValuableMetric(chart)) {
                  return null;
               }

               capacityChart = chart;
            } else if (chart.entityRefId.indexOf("cluster-dataprotection") > -1) {
               dpChart = chart;
            }
         }

         if (!dedupEnabled) {
            capacityChart = this.removeDedupData(capacityChart);
         }

         boolean isLocalDataProtectionSupported = VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef);
         capacityChart = this.recombineCapacityHistoryCharts(capacityChart, dpChart, isLocalDataProtectionSupported);
         return sparseMetrics ? this.sparseChartPoints(capacityChart) : capacityChart;
      }
   }

   private List<PerfEntityStateData> getAllChartsForCapacityHistory(ManagedObjectReference clusterRef, PerfQuerySpec[] specs, String uuid) throws Exception {
      PerfQuerySpec[] var7 = specs;
      int var6 = specs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PerfQuerySpec spec = var7[var5];
         spec.entityUuid = uuid;
      }

      List<PerfEntityStateData> perfState = this.getEntityPerfState(clusterRef, specs);
      if (perfState.isEmpty()) {
         return null;
      } else {
         Map<String, PerfEntityStateData> entitiesDataMap = new HashMap();
         Map<String, PerfEntityStateData> entitiesDataMap = this.aggregateStateDataAndUpdateMap(perfState, entitiesDataMap);
         return new ArrayList(entitiesDataMap.values());
      }
   }

   private PerfEntityStateData recombineCapacityHistoryCharts(PerfEntityStateData capacityChart, PerfEntityStateData dpChart, boolean isLocalDataProtectionSupported) {
      if (isLocalDataProtectionSupported) {
         if (this.isValuableMetric(dpChart)) {
            PerfGraphMetricsData totalDpOverheadMetric = null;
            Iterator var6 = dpChart.metricsSeries.iterator();

            while(var6.hasNext()) {
               PerfGraphMetricsData metric = (PerfGraphMetricsData)var6.next();
               if ("totalDpOverhead".equalsIgnoreCase(metric.key)) {
                  totalDpOverheadMetric = metric;
                  break;
               }
            }

            for(int index = 0; index < capacityChart.metricsSeries.size(); ++index) {
               if ("totalDpOverhead".equalsIgnoreCase(((PerfGraphMetricsData)capacityChart.metricsSeries.get(index)).key)) {
                  capacityChart.metricsSeries.set(index, totalDpOverheadMetric);
                  break;
               }
            }
         }
      } else {
         for(int index = 0; index < capacityChart.metricsSeries.size(); ++index) {
            if ("totalDpOverhead".equalsIgnoreCase(((PerfGraphMetricsData)capacityChart.metricsSeries.get(index)).key)) {
               capacityChart.metricsSeries.remove(index);
               break;
            }
         }
      }

      return capacityChart;
   }

   @TsService
   public List<PerfEntityStateData> getEntityPerfState(ManagedObjectReference clusterRef, PerfQuerySpec[] specs) throws Exception {
      if (ArrayUtils.isEmpty(specs)) {
         _logger.error("Invalid perf query specs are passed.");
      }

      List<VsanPerfQuerySpec> querySpecs = new ArrayList(specs.length);
      PerfQuerySpec[] var7 = specs;
      int var6 = specs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PerfQuerySpec spec = var7[var5];
         querySpecs.add(PerfQuerySpec.toVmodl(spec));
      }

      EntityPerfStateObject perfState = new EntityPerfStateObject();

      RuntimeFault runtimeFault;
      try {
         Throwable var28 = null;
         runtimeFault = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.queryVsanPerf");

            try {
               VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
               perfState.metrics = perfMgr.queryVsanPerf((VsanPerfQuerySpec[])querySpecs.toArray(new VsanPerfQuerySpec[0]), clusterRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var22) {
            if (var28 == null) {
               var28 = var22;
            } else if (var28 != var22) {
               var28.addSuppressed(var22);
            }

            throw var28;
         }
      } catch (Timedout var23) {
         perfState.errorMessage = var23.getLocalizedMessage();
      } catch (VimFault var24) {
         throw var24;
      } catch (InvalidArgument var25) {
         if (var25.getInvalidProperty().equals("entityRefId")) {
            runtimeFault = new RuntimeFault();
            runtimeFault.setMessage("InvalidEntityRefID");
            throw runtimeFault;
         }

         throw Utils.getMethodFault(var25);
      } catch (Exception var26) {
         throw Utils.getMethodFault(var26);
      }

      return this.parseStateObjectChartData(perfState, querySpecs);
   }

   private List<PerfEntityStateData> parseStateObjectChartData(EntityPerfStateObject perfState, List<VsanPerfQuerySpec> querySpecs) {
      List<PerfEntityStateData> stateDataList = new ArrayList();
      PerfMetricsInfo metricsInfo = PerfMetricsInfo.extractMetricsInfo(perfState.metrics);

      for(int metricIndex = 0; metricIndex < perfState.metrics.length; ++metricIndex) {
         long startTime = 0L;
         long endTime = 0L;
         if (querySpecs.size() == 1) {
            startTime = ((VsanPerfQuerySpec)querySpecs.get(0)).startTime.getTimeInMillis();
            endTime = ((VsanPerfQuerySpec)querySpecs.get(0)).endTime.getTimeInMillis();
         } else {
            VsanPerfQuerySpec spec = (VsanPerfQuerySpec)querySpecs.get(metricIndex);
            startTime = spec.startTime.getTimeInMillis();
            endTime = spec.endTime.getTimeInMillis();
         }

         VsanPerfEntityMetricCSV metric = perfState.metrics[metricIndex];
         if (!StringUtils.isEmpty(metric.sampleInfo) && !ArrayUtils.isEmpty(metric.value)) {
            stateDataList.add(PerfEntityStateData.parsePerfEntityMetricCSV(metric, startTime, endTime));
         } else if (!MapUtils.isEmpty(metricsInfo.entityRefIdToIntervalMap) && !MapUtils.isEmpty(metricsInfo.entityRefIdToMetricIdMap) && metricsInfo.entityRefIdToIntervalMap.get(metric.entityRefId) != null) {
            int interval = (Integer)metricsInfo.entityRefIdToIntervalMap.get(metric.entityRefId);
            List<String> metricIds = (List)metricsInfo.entityRefIdToMetricIdMap.get(metric.entityRefId);
            stateDataList.add(PerfEntityStateData.parsePerfEntityMetricCSV(metric, startTime, endTime, interval, metricIds));
         }
      }

      return stateDataList;
   }

   private PerfEntityStateData removeDedupData(PerfEntityStateData chart) {
      Iterator iterator = chart.metricsSeries.iterator();

      while(true) {
         PerfGraphMetricsData metric;
         do {
            if (!iterator.hasNext()) {
               return chart;
            }

            metric = (PerfGraphMetricsData)iterator.next();
         } while(!metric.key.equalsIgnoreCase("savedByDedup") && !metric.key.equalsIgnoreCase("dedupRatio"));

         iterator.remove();
      }
   }

   private PerfEntityStateData sparseChartPoints(PerfEntityStateData metric) {
      if (!this.isValuableMetric(metric)) {
         return null;
      } else {
         int metricsInterval = metric.metricsCollectInterval == 0 ? 60 : metric.metricsCollectInterval;
         int interval = (int)Math.floor((double)(3600 / metricsInterval));
         List<String> newSampleInfos = new ArrayList();

         for(int infosIndex = 0; infosIndex < metric.timeStamps.size(); ++infosIndex) {
            if (infosIndex % interval == 0) {
               newSampleInfos.add((String)metric.timeStamps.get(infosIndex));
            }
         }

         if (newSampleInfos.size() == 0) {
            return null;
         } else {
            metric.timeStamps = newSampleInfos;

            ArrayList newValues;
            PerfGraphMetricsData value;
            for(Iterator var6 = metric.metricsSeries.iterator(); var6.hasNext(); value.values = newValues) {
               value = (PerfGraphMetricsData)var6.next();
               newValues = new ArrayList();

               for(int valuesIndex = 0; valuesIndex < value.values.size(); ++valuesIndex) {
                  if (valuesIndex % interval == 0) {
                     newValues.add((Double)value.values.get(valuesIndex));
                  }
               }
            }

            boolean isEmptyChart = true;
            Iterator var13 = metric.metricsSeries.iterator();

            while(true) {
               while(var13.hasNext()) {
                  PerfGraphMetricsData value = (PerfGraphMetricsData)var13.next();
                  Iterator var9 = value.values.iterator();

                  while(var9.hasNext()) {
                     Double pointValue = (Double)var9.next();
                     if (pointValue != null) {
                        isEmptyChart = false;
                        break;
                     }
                  }
               }

               return isEmptyChart ? null : metric;
            }
         }
      }
   }

   private boolean isValuableMetric(PerfEntityStateData metric) {
      return metric != null && !metric.metricsSeries.isEmpty() && !metric.timeStamps.isEmpty();
   }

   @TsService
   public Boolean getPerfServiceEnabled(ManagedObjectReference param1) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public PerfTimeRangeData[] getSavedTimeRanges(ManagedObjectReference clusterRef) throws Exception {
      ArrayList list = new ArrayList();

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.queryTimeRanges");

            try {
               VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
               VsanPerfTimeRange[] ranges = perfMgr.queryTimeRanges(clusterRef, new VsanPerfTimeRangeQuerySpec());
               if (!ArrayUtils.isEmpty(ranges)) {
                  VsanPerfTimeRange[] var11 = ranges;
                  int var10 = ranges.length;

                  for(int var9 = 0; var9 < var10; ++var9) {
                     VsanPerfTimeRange range = var11[var9];
                     PerfTimeRangeData t = new PerfTimeRangeData();
                     t.name = range.name;
                     t.from = range.startTime.getTime();
                     t.to = range.endTime.getTime();
                     list.add(t);
                  }
               }
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var20) {
            if (var3 == null) {
               var3 = var20;
            } else if (var3 != var20) {
               var3.addSuppressed(var20);
            }

            throw var3;
         }
      } catch (Exception var21) {
      }

      PerfTimeRangeData[] t = new PerfTimeRangeData[list.size()];
      return (PerfTimeRangeData[])list.toArray(t);
   }

   @TsService
   public String getConfiguredPolicy(ManagedObjectReference clusterRef) throws Exception {
      boolean isPerfSvcAutoConfigSupported = VsanCapabilityUtils.isPerfSvcAutoConfigSupportedOnVc(clusterRef);
      if (isPerfSvcAutoConfigSupported) {
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanConfigSystem.getConfigInfoEx");

            Throwable var10000;
            label268: {
               label263: {
                  boolean var10001;
                  String var21;
                  try {
                     VsanPerfsvcConfig perfsvcConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).perfsvcConfig;
                     if (perfsvcConfig == null || !(perfsvcConfig.profile instanceof DefinedProfileSpec)) {
                        break label263;
                     }

                     DefinedProfileSpec profileSpec = (DefinedProfileSpec)perfsvcConfig.profile;
                     if (profileSpec == null) {
                        break label263;
                     }

                     var21 = profileSpec.profileId;
                  } catch (Throwable var19) {
                     var10000 = var19;
                     var10001 = false;
                     break label268;
                  }

                  if (p != null) {
                     p.close();
                  }

                  try {
                     return var21;
                  } catch (Throwable var18) {
                     var10000 = var18;
                     var10001 = false;
                     break label268;
                  }
               }

               if (p != null) {
                  p.close();
               }

               return this.getStatesObjectInformation(clusterRef).spbmProfileUuid;
            }

            var4 = var10000;
            if (p != null) {
               p.close();
            }

            throw var4;
         } catch (Throwable var20) {
            if (var4 == null) {
               var4 = var20;
            } else if (var4 != var20) {
               var4.addSuppressed(var20);
            }

            throw var4;
         }
      } else {
         return this.getStatesObjectInformation(clusterRef).spbmProfileUuid;
      }
   }

   @TsService
   public PerfStatsObjectInfo getStatesObjectInformation(ManagedObjectReference clusterRef) throws Exception {
      PerfStatsObjectInfo info = null;

      PropertyValue[] resultset;
      try {
         Throwable var3 = null;
         resultset = null;

         try {
            VsanProfiler.Point p = _profiler.point("perfMgr.queryStatsObjectInformation");

            try {
               VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
               VsanObjectInformation information = perfMgr.queryStatsObjectInformation(clusterRef);
               info = PerfStatsObjectInfo.fromVmodl(information);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var20) {
            if (var3 == null) {
               var3 = var20;
            } else if (var3 != var20) {
               var3.addSuppressed(var20);
            }

            throw var3;
         }
      } catch (Exception var21) {
         info = new PerfStatsObjectInfo();
      }

      if (info.vsanObjectUuid != null && info.spbmProfileUuid != null) {
         ManagedObjectReference vcRootRef = VmodlHelper.getRootFolder(clusterRef.getServerGuid());

         try {
            resultset = QueryUtil.getPropertiesForRelatedObjects(vcRootRef, "pbmProfiles", "PbmRequirementStorageProfile", new String[]{"profileContent"}).getPropertyValues();
            PropertyValue[] var8 = resultset;
            int var25 = resultset.length;

            for(int var24 = 0; var24 < var25; ++var24) {
               PropertyValue profileContent = var8[var24];
               Profile profile = (Profile)profileContent.value;
               if (profile.profileId.uniqueId.equals(info.spbmProfileUuid)) {
                  info.spbmProfile = profile;
                  break;
               }
            }
         } catch (Exception var18) {
            throw Utils.getMethodFault(var18);
         }
      }

      info.serviceEnabled = this.getPerfServiceEnabled(clusterRef);
      info.verboseModeEnabled = this.isPerfVerboseModeEnabled(clusterRef);
      info.networkDiagnosticModeEnabled = this.isPerfNetworkDiagnosticModeEnabled(clusterRef);
      return info;
   }

   private boolean isPerfVerboseModeEnabled(ManagedObjectReference clusterRef) throws Exception {
      VsanCapabilityData vcCapabilities = VsanCapabilityUtils.getVcCapabilities(clusterRef);
      if (vcCapabilities.isVerboseModeInClusterConfigurationSupported) {
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         VsanPerfsvcConfig perfConfig = null;
         Throwable var5 = null;
         Object var6 = null;

         try {
            VsanProfiler.Point p = _profiler.point("vsanConfigSystem.getConfigInfoEx");

            try {
               perfConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).perfsvcConfig;
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var16) {
            if (var5 == null) {
               var5 = var16;
            } else if (var5 != var16) {
               var5.addSuppressed(var16);
            }

            throw var5;
         }

         if (perfConfig != null && perfConfig.verboseMode != null) {
            return BooleanUtils.isTrue(perfConfig.verboseMode);
         }
      }

      try {
         VsanPerfNodeInformation node = this.getPerfStatsMasterNode(clusterRef);
         return node != null && node.masterInfo != null && node.masterInfo.verboseMode != null ? BooleanUtils.isTrue(node.masterInfo.verboseMode) : false;
      } catch (Exception var14) {
         throw Utils.getMethodFault(var14);
      }
   }

   private boolean isPerfNetworkDiagnosticModeEnabled(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      VsanPerfsvcConfig perfConfig = null;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("vsanConfigSystem.getConfigInfoEx");

         try {
            perfConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).perfsvcConfig;
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var12) {
         if (var4 == null) {
            var4 = var12;
         } else if (var4 != var12) {
            var4.addSuppressed(var12);
         }

         throw var4;
      }

      return perfConfig == null ? false : BooleanUtils.isTrue(perfConfig.diagnosticMode);
   }

   private Map<String, VsanPerfEntityType> getPerfEntityTypes(ManagedObjectReference param1) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private Map<String, VsanPerfEntityType> handlePerfEntityTypes(VsanPerfEntityType[] types) throws Exception {
      if (ArrayUtils.isEmpty(types)) {
         return null;
      } else {
         Map<String, VsanPerfEntityType> entitySpecMap = new HashMap();
         VsanPerfEntityType[] var6 = types;
         int var5 = types.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanPerfEntityType entitySpec = var6[var4];
            entitySpecMap.put(entitySpec.id, entitySpec);
         }

         return entitySpecMap;
      }
   }

   @TsService
   public List<DiskGroup> getDiskMappings(ManagedObjectReference host) throws Exception {
      List<DiskGroup> groups = new ArrayList();
      DiskMapping[] diskMappings = (DiskMapping[])QueryUtil.getProperty(host, "config.vsanHostConfig.storageInfo.diskMapping", (Object)null);
      if (ArrayUtils.isEmpty(diskMappings)) {
         return groups;
      } else {
         DiskMapping[] var7 = diskMappings;
         int var6 = diskMappings.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            DiskMapping diskMapping = var7[var5];
            groups.add(DiskGroup.fromDiskMapping(diskMapping));
         }

         return groups;
      }
   }

   private VsanPerfNodeInformation getPerfStatsMasterNode(ManagedObjectReference clusterRef) throws Exception {
      VsanPerformanceManager perfMgr = VsanProviderUtils.getVsanPerformanceManager(clusterRef);
      VsanPerfNodeInformation[] nodes = null;
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point p = _profiler.point("perfMgr.queryNodeInformation");

         try {
            nodes = perfMgr.queryNodeInformation(clusterRef);
         } finally {
            if (p != null) {
               p.close();
            }

         }
      } catch (Throwable var12) {
         if (var4 == null) {
            var4 = var12;
         } else if (var4 != var12) {
            var4.addSuppressed(var12);
         }

         throw var4;
      }

      return this.getPerfMasterNode(nodes);
   }

   private VsanPerfNodeInformation getPerfMasterNode(VsanPerfNodeInformation[] nodes) {
      if (ArrayUtils.isNotEmpty(nodes)) {
         VsanPerfNodeInformation[] var5 = nodes;
         int var4 = nodes.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanPerfNodeInformation n = var5[var3];
            if (n.isStatsMaster) {
               return n;
            }
         }
      }

      return null;
   }

   @TsService
   public PerfMonitorCommonPropsData getPerfMonitorCommonPropsData(ManagedObjectReference objectRef) throws Exception {
      PerfMonitorCommonPropsData data = new PerfMonitorCommonPropsData();
      data.clusterRef = ClusterComputeResource.class.getSimpleName().equals(objectRef.getType()) ? objectRef : (ManagedObjectReference)QueryUtil.getProperty(objectRef, "cluster");
      Throwable var3 = null;
      Object var4 = null;

      try {
         Measure measure = new Measure("Collect performance service related data");

         try {
            VsanAsyncDataRetriever dataRetriever = this.dataRetrieverFactory.createVsanAsyncDataRetriever(measure, data.clusterRef).loadConfigInfoEx().loadNodeInformation().loadSupportedEntityTypes().loadStatsObjectInformation();
            data.entityTypes = this.handlePerfEntityTypes(dataRetriever.getSupportedEntityTypes());
            VsanPerfNodeInformation masterNode = this.getPerfMasterNode(dataRetriever.getNodeInformation());
            data.currentTimeOnMasterNode = this.getCurrentTimeOnMasterNode(masterNode);
            if (masterNode != null && masterNode.masterInfo != null && masterNode.masterInfo.verboseMode != null) {
               data.isVerboseModeEnabled = BooleanUtils.isTrue(masterNode.masterInfo.verboseMode);
            }

            ConfigInfoEx configInfo = dataRetriever.getConfigInfoEx();
            data.isIscsiServiceEnabled = this.isIscsiServiceEnabled(configInfo);
            data.isFileServiceEnabled = this.isFileServiceEnabled(configInfo);
            data.isEmptyClusterForIscsi = this.iscsiPropertyProvider.isEmptyClusterForIscsi(data.clusterRef);
            boolean isPerfSvcAutoConfigSupported = VsanCapabilityUtils.isPerfSvcAutoConfigSupportedOnVc(data.clusterRef);
            if (isPerfSvcAutoConfigSupported && configInfo != null && configInfo.perfsvcConfig != null) {
               data.isPerformanceServiceEnabled = configInfo.perfsvcConfig.enabled;
            } else {
               VsanObjectInformation objectInfo = dataRetriever.getStatsObjectInformation();
               data.isPerformanceServiceEnabled = objectInfo != null ? objectInfo.vsanObjectUuid != null : false;
            }

            data.hasEditPrivilege = this.permissionService.hasPermissions(data.clusterRef, new String[]{"Host.Inventory.EditCluster"}) && this.permissionService.hasVcPermissions(data.clusterRef, new String[]{"StorageProfile.View"});
            if (VirtualMachine.class.getSimpleName().equals(objectRef.getType())) {
               data.isDataProtectionSupported = (Boolean)QueryUtil.getProperty(objectRef, "isVmDataProtected");
            } else {
               data.isDataProtectionSupported = VsanCapabilityUtils.isLocalDataProtectionSupported(data.clusterRef);
            }
         } finally {
            if (measure != null) {
               measure.close();
            }

         }

         return data;
      } catch (Throwable var16) {
         if (var3 == null) {
            var3 = var16;
         } else if (var3 != var16) {
            var3.addSuppressed(var16);
         }

         throw var3;
      }
   }

   private boolean isIscsiServiceEnabled(ConfigInfoEx configInfo) {
      return configInfo != null && configInfo.getIscsiConfig() != null && configInfo.getIscsiConfig().getEnabled() != null ? configInfo.getIscsiConfig().getEnabled() : false;
   }

   private boolean isFileServiceEnabled(ConfigInfoEx configInfo) {
      return configInfo != null && configInfo.getFileServiceConfig() != null && configInfo.getFileServiceConfig().enabled;
   }

   private Long getCurrentTimeOnMasterNode(VsanPerfNodeInformation perfMasterNode) throws Exception {
      if (perfMasterNode != null) {
         PropertyConstraint masterNode = QueryUtil.createPropertyConstraint(HostSystem.class.getSimpleName(), "name", Comparator.EQUALS, perfMasterNode.hostname);
         String[] properties = new String[]{"currentTimeOnHost"};
         ResultSet resultSet = QueryUtil.getData(QueryUtil.buildQuerySpec((Constraint)masterNode, properties));
         DataServiceResponse response = QueryUtil.getDataServiceResponse(resultSet, properties);
         PropertyValue[] var7;
         if ((var7 = response.getPropertyValues()).length != 0) {
            PropertyValue propertyValue = var7[0];
            Calendar time = (Calendar)propertyValue.value;
            return time.getTimeInMillis();
         }
      }

      return null;
   }

   private Set<String> getActiveVmnicsForStandardNetworkConfiguration(NetworkInfo networkInfo, VirtualNic vn) {
      Set<String> activePnics = new HashSet();
      if (ArrayUtils.isEmpty(networkInfo.portgroup)) {
         return activePnics;
      } else {
         PortGroup[] var7;
         int var6 = (var7 = networkInfo.portgroup).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PortGroup pgroup = var7[var5];
            if (!ArrayUtils.isEmpty(pgroup.port) && pgroup.computedPolicy != null && pgroup.computedPolicy.nicTeaming != null) {
               Port[] var11;
               int var10 = (var11 = pgroup.port).length;

               for(int var9 = 0; var9 < var10; ++var9) {
                  Port p = var11[var9];
                  if (p.key != null && p.key.equals(vn.port)) {
                     NicOrderPolicy nicOrder = pgroup.computedPolicy.nicTeaming.nicOrder;
                     if (nicOrder != null) {
                        activePnics.addAll(Arrays.asList(nicOrder.activeNic));
                        break;
                     }
                  }
               }
            }
         }

         return activePnics;
      }
   }

   private Set<String> getActiveVmnicsFromDistributedSwitch(String switchUuid, NetworkInfo networkInfo, String[] uplinks) {
      Set<String> activePnics = new HashSet();
      if (!ArrayUtils.isEmpty(networkInfo.proxySwitch) && !ArrayUtils.isEmpty(uplinks) && !StringUtils.isBlank(switchUuid)) {
         HostProxySwitch[] var8;
         int var7 = (var8 = networkInfo.proxySwitch).length;

         for(int var6 = 0; var6 < var7; ++var6) {
            HostProxySwitch proxySwitch = var8[var6];
            if (switchUuid.equals(proxySwitch.dvsUuid) && !ArrayUtils.isEmpty(proxySwitch.uplinkPort)) {
               List<String> activeUplinkKeys = new ArrayList();
               String[] var13 = uplinks;
               int var12 = uplinks.length;

               for(int var11 = 0; var11 < var12; ++var11) {
                  String uplink = var13[var11];
                  KeyValue[] var17;
                  int var16 = (var17 = proxySwitch.uplinkPort).length;

                  int var15;
                  for(var15 = 0; var15 < var16; ++var15) {
                     KeyValue kv = var17[var15];
                     if (kv.value.equals(uplink)) {
                        activeUplinkKeys.add(kv.key);
                     }
                  }

                  if (!ArrayUtils.isEmpty(proxySwitch.hostLag)) {
                     HostLagConfig[] var27;
                     var16 = (var27 = proxySwitch.hostLag).length;

                     for(var15 = 0; var15 < var16; ++var15) {
                        HostLagConfig lagConfig = var27[var15];
                        if (lagConfig.lagName.equals(uplink) && !ArrayUtils.isEmpty(lagConfig.uplinkPort)) {
                           KeyValue[] var21;
                           int var20 = (var21 = lagConfig.uplinkPort).length;

                           for(int var19 = 0; var19 < var20; ++var19) {
                              KeyValue k = var21[var19];
                              activeUplinkKeys.add(k.key);
                           }
                        }
                     }
                  }
               }

               if (!CollectionUtils.isEmpty(activeUplinkKeys) && proxySwitch.spec != null && proxySwitch.spec.backing != null) {
                  PnicBacking backing = (PnicBacking)proxySwitch.spec.backing;
                  if (!ArrayUtils.isEmpty(backing.pnicSpec)) {
                     PnicSpec[] var26;
                     int var24 = (var26 = backing.pnicSpec).length;

                     for(var12 = 0; var12 < var24; ++var12) {
                        PnicSpec spec = var26[var12];
                        if (activeUplinkKeys.contains(spec.uplinkPortKey)) {
                           activePnics.add(spec.pnicDevice);
                        }
                     }
                  }
               }
            }
         }

         return activePnics;
      } else {
         return activePnics;
      }
   }

   private ActiveVmnicDataSpec getDvsConfigurationsFromHostNetworks(ManagedObjectReference[] networks) throws Exception {
      ActiveVmnicDataSpec vmnicSpec = new ActiveVmnicDataSpec();
      List<ManagedObjectReference> switches = new ArrayList();
      Map<String, ManagedObjectReference> uuidSwitchMap = new HashMap();
      Multimap<ManagedObjectReference, ManagedObjectReference> switchNetworkMap = ArrayListMultimap.create();
      Map<ManagedObjectReference, String[]> networkUplinksMap = new HashMap();
      PropertyValue[] pv = QueryUtil.getProperties(networks, new String[]{"config.distributedVirtualSwitch", "config.defaultPortConfig.uplinkTeamingPolicy.uplinkPortOrder.activeUplinkPort"}).getPropertyValues();
      int var10;
      if (!ArrayUtils.isEmpty(pv)) {
         PropertyValue[] var11 = pv;
         var10 = pv.length;

         for(int var9 = 0; var9 < var10; ++var9) {
            PropertyValue property = var11[var9];
            ManagedObjectReference _network = (ManagedObjectReference)property.resourceObject;
            String var13;
            switch((var13 = property.propertyName).hashCode()) {
            case -418780824:
               if (var13.equals("config.distributedVirtualSwitch")) {
                  ManagedObjectReference _switch = (ManagedObjectReference)property.value;
                  if (_switch != null) {
                     switches.add(_switch);
                     switchNetworkMap.put(_switch, _network);
                  }
               }
               break;
            case 406754676:
               if (var13.equals("config.defaultPortConfig.uplinkTeamingPolicy.uplinkPortOrder.activeUplinkPort")) {
                  String[] activeUplinks = (String[])property.value;
                  if (activeUplinks != null) {
                     networkUplinksMap.put(_network, activeUplinks);
                  }
               }
            }
         }
      }

      if (!CollectionUtils.isEmpty(switches)) {
         PropertyValue[] props = QueryUtil.getProperties((ManagedObjectReference[])switches.toArray(new ManagedObjectReference[0]), new String[]{"uuid"}).getPropertyValues();
         if (!ArrayUtils.isEmpty(props)) {
            PropertyValue[] var19 = props;
            int var18 = props.length;

            for(var10 = 0; var10 < var18; ++var10) {
               PropertyValue prop = var19[var10];
               uuidSwitchMap.put((String)prop.value, (ManagedObjectReference)prop.resourceObject);
            }
         }
      }

      vmnicSpec.switches = switches;
      vmnicSpec.uuidSwitchMap = uuidSwitchMap;
      vmnicSpec.switchNetworkMap = switchNetworkMap;
      vmnicSpec.networkUplinksMap = networkUplinksMap;
      return vmnicSpec;
   }

   @TsService
   public List<HostPnicsData> getHostPhysicalAdapterMapping(ManagedObjectReference serverObjRef) throws Exception {
      List<HostPnicsData> hostPnics = new ArrayList();
      DataServiceResponse response = this.getPnicQueryData(serverObjRef);
      if (response == null) {
         return hostPnics;
      } else {
         Iterator var5 = response.getResourceObjects().iterator();

         while(true) {
            HashSet activePnics;
            ManagedObjectReference hostRef;
            NetworkInfo networkInfo;
            ManagedObjectReference[] networks;
            String hostUuid;
            PortConfig[] portConfigs;
            VirtualNic[] configuredVnics;
            do {
               do {
                  do {
                     do {
                        if (!var5.hasNext()) {
                           return hostPnics;
                        }

                        Object resourceObject = var5.next();
                        activePnics = new HashSet();
                        hostRef = (ManagedObjectReference)resourceObject;
                        networkInfo = (NetworkInfo)response.getProperty(hostRef, "config.network");
                        networks = (ManagedObjectReference[])response.getProperty(hostRef, "network");
                        hostUuid = (String)response.getProperty(hostRef, "config.vsanHostConfig.clusterInfo.nodeUuid");
                        portConfigs = (PortConfig[])response.getProperty(hostRef, "config.vsanHostConfig.networkInfo.port");
                        configuredVnics = (VirtualNic[])response.getProperty(hostRef, "config.network.vnic");
                     } while(ArrayUtils.isEmpty(portConfigs));
                  } while(ArrayUtils.isEmpty(configuredVnics));
               } while(networkInfo == null);
            } while(ArrayUtils.isEmpty(networkInfo.vnic));

            ActiveVmnicDataSpec vmnicSpec = null;
            if (networks != null) {
               vmnicSpec = this.getDvsConfigurationsFromHostNetworks(networks);
            }

            List<PerfVnicEntity> activeVnics = this.getVsanUsedVnicEntities(portConfigs, configuredVnics, hostUuid);
            Iterator var16 = activeVnics.iterator();

            int var18;
            int var19;
            while(var16.hasNext()) {
               PerfVnicEntity activeVnic = (PerfVnicEntity)var16.next();
               VirtualNic[] var20;
               var19 = (var20 = networkInfo.vnic).length;

               for(var18 = 0; var18 < var19; ++var18) {
                  VirtualNic vnic = var20[var18];
                  if (vnic.device.equals(activeVnic.deviceName)) {
                     if (!StringUtils.isEmpty(vnic.port)) {
                        activePnics.addAll(this.getActiveVmnicsForStandardNetworkConfiguration(networkInfo, vnic));
                     } else if (vmnicSpec != null) {
                        String switchUuid = vnic.spec.distributedVirtualPort == null ? null : vnic.spec.distributedVirtualPort.switchUuid;
                        String[] uplinkArr = this.getActiveUplinkNamesOnHost(vmnicSpec, vnic);
                        activePnics.addAll(this.getActiveVmnicsFromDistributedSwitch(switchUuid, networkInfo, uplinkArr));
                     }
                  }
               }
            }

            String[] activePnicArr = new String[activePnics.size()];
            activePnicArr = (String[])activePnics.toArray(activePnicArr);
            Arrays.sort(activePnicArr);
            List<PerfPhysicalAdapterEntity> pnics = new ArrayList();
            String[] var28 = activePnicArr;
            var19 = activePnicArr.length;

            for(var18 = 0; var18 < var19; ++var18) {
               String pnic = var28[var18];
               PerfPhysicalAdapterEntity entity = new PerfPhysicalAdapterEntity();
               entity.hostUuid = hostUuid;
               entity.deviceName = pnic;
               pnics.add(entity);
            }

            HostPnicsData pnicData = new HostPnicsData();
            pnicData.hostName = (String)response.getProperty(hostRef, "name");
            pnicData.pnics = pnics;
            hostPnics.add(pnicData);
         }
      }
   }

   private DataServiceResponse getPnicQueryData(ManagedObjectReference serverObjRef) throws Exception {
      DataServiceResponse response = null;
      if (ClusterComputeResource.class.getSimpleName().equals(serverObjRef.getType())) {
         response = QueryUtil.getPropertiesForRelatedObjects(serverObjRef, "host", HostSystem.class.getSimpleName(), new String[]{"name", "config.vsanHostConfig.clusterInfo.nodeUuid", "config.network", "network", "config.vsanHostConfig.networkInfo.port", "config.network.vnic"});
      } else if (HostSystem.class.getSimpleName().equals(serverObjRef.getType())) {
         response = QueryUtil.getProperties(serverObjRef, new String[]{"name", "config.vsanHostConfig.clusterInfo.nodeUuid", "config.network", "network", "config.vsanHostConfig.networkInfo.port", "config.network.vnic"});
      }

      return response;
   }

   private List<PerfVnicEntity> getVsanUsedVnicEntities(PortConfig[] portConfigs, VirtualNic[] configuredVnics, String hostUuid) {
      List<String> allActiveVnics = new ArrayList();
      PortConfig[] var8 = portConfigs;
      int var7 = portConfigs.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         PortConfig c = var8[var6];
         allActiveVnics.add(c.getDevice());
      }

      List<PerfVnicEntity> vnicEntities = new ArrayList();
      VirtualNic[] var9 = configuredVnics;
      int var13 = configuredVnics.length;

      for(var7 = 0; var7 < var13; ++var7) {
         VirtualNic vnic = var9[var7];
         if (vnic.device != null && allActiveVnics.contains(vnic.device)) {
            PerfVnicEntity vnicEntity = new PerfVnicEntity();
            vnicEntity.deviceName = vnic.device;
            vnicEntity.netStackInstanceKey = vnic.getSpec().netStackInstanceKey;
            vnicEntity.hostUuid = hostUuid;
            vnicEntities.add(vnicEntity);
         }
      }

      return vnicEntities;
   }

   @TsService
   public List<PerfPhysicalAdapterEntity> getHostPhysicalAdapters(ManagedObjectReference hostRef) throws Exception {
      List<HostPnicsData> result = this.getHostPhysicalAdapterMapping(hostRef);
      return CollectionUtils.isEmpty(result) ? null : ((HostPnicsData)result.get(0)).pnics;
   }

   private String[] getActiveUplinkNamesOnHost(ActiveVmnicDataSpec vmnicSpec, VirtualNic vn) throws Exception {
      String switchUuid = vn.spec.distributedVirtualPort == null ? null : vn.spec.distributedVirtualPort.switchUuid;
      if (vmnicSpec != null && !StringUtils.isEmpty(switchUuid) && !CollectionUtils.isEmpty(vmnicSpec.switches)) {
         String portgroupKey = vn.spec.distributedVirtualPort.portgroupKey;
         Set<String> uplinks = vmnicSpec.getUplinksBySwitchUuid(switchUuid, portgroupKey);
         String[] uplinkArr = new String[uplinks.size()];
         uplinkArr = (String[])uplinks.toArray(uplinkArr);
         return uplinkArr;
      } else {
         return new String[0];
      }
   }

   @TsService
   public List<HostVnicsData> getHostVnicsMapping(ManagedObjectReference serverObj) throws Exception {
      List<HostVnicsData> hostVnics = new ArrayList();
      DataServiceResponse response = this.getVnicQueryData(serverObj);
      if (response == null) {
         return hostVnics;
      } else {
         Iterator var5 = response.getResourceObjects().iterator();

         while(var5.hasNext()) {
            Object resourceObject = var5.next();
            ManagedObjectReference hostRef = (ManagedObjectReference)resourceObject;
            PortConfig[] portConfigs = (PortConfig[])response.getProperty(hostRef, "config.vsanHostConfig.networkInfo.port");
            VirtualNic[] vnics = (VirtualNic[])response.getProperty(hostRef, "config.network.vnic");
            if (!ArrayUtils.isEmpty(portConfigs) && !ArrayUtils.isEmpty(vnics)) {
               String hostUuid = (String)response.getProperty(hostRef, "config.vsanHostConfig.clusterInfo.nodeUuid");
               List<PerfVnicEntity> vnicEntities = this.getVsanUsedVnicEntities(portConfigs, vnics, hostUuid);
               HostVnicsData vnicsData = new HostVnicsData();
               vnicsData.hostName = (String)response.getProperty(hostRef, "name");
               vnicsData.vnics = vnicEntities;
               hostVnics.add(vnicsData);
            }
         }

         return hostVnics;
      }
   }

   private DataServiceResponse getVnicQueryData(ManagedObjectReference serverObj) throws Exception {
      DataServiceResponse response = null;
      if (ClusterComputeResource.class.getSimpleName().equals(serverObj.getType())) {
         response = QueryUtil.getPropertiesForRelatedObjects(serverObj, "host", HostSystem.class.getSimpleName(), new String[]{"name", "config.vsanHostConfig.clusterInfo.nodeUuid", "config.vsanHostConfig.networkInfo.port", "config.network.vnic"});
      } else if (HostSystem.class.getSimpleName().equals(serverObj.getType())) {
         response = QueryUtil.getProperties(serverObj, new String[]{"name", "config.vsanHostConfig.clusterInfo.nodeUuid", "config.vsanHostConfig.networkInfo.port", "config.network.vnic"});
      }

      return response;
   }

   @TsService
   public List<PerfVnicEntity> getHostVirtualAdapters(ManagedObjectReference hostRef) throws Exception {
      List<HostVnicsData> result = this.getHostVnicsMapping(hostRef);
      return CollectionUtils.isEmpty(result) ? null : ((HostVnicsData)result.get(0)).vnics;
   }

   @TsService
   public PerfVirtualMachineDiskData getVirtualMachineDiskData(ManagedObjectReference vmRef) throws Exception {
      PerfVirtualMachineDiskData data = new PerfVirtualMachineDiskData();
      DataServiceResponse response = QueryUtil.getProperties(vmRef, new String[]{"config.instanceUuid", "config.hardware.device"});
      if (response == null) {
         return data;
      } else {
         VirtualDevice[] vDevs;
         for(Iterator var5 = response.getResourceObjects().iterator(); var5.hasNext(); data.virtualDisks = this.getVirtualDiskEntities(vDevs)) {
            Object resourceObject = var5.next();
            data.vmUuid = (String)response.getProperty(resourceObject, "config.instanceUuid");
            vDevs = (VirtualDevice[])response.getProperty(resourceObject, "config.hardware.device");
            data.vscsiEntities = this.getVscsiEntities(vDevs);
         }

         return data;
      }
   }

   private List<PerfVscsiEntity> getVscsiEntities(VirtualDevice[] vDevs) throws Exception {
      List<PerfVscsiEntity> vscsiEntities = new ArrayList();
      if (vDevs.length > 0) {
         List<VirtualDisk> vDisks = new ArrayList();
         Map<Integer, VirtualController> vConts = new HashMap();
         VirtualDevice[] var8 = vDevs;
         int var7 = vDevs.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            VirtualDevice vDev = var8[var6];
            if (vDev instanceof VirtualDisk) {
               vDisks.add((VirtualDisk)vDev);
            } else {
               try {
                  if (vDev.getClass().getField("scsiCtlrUnitNumber") != null) {
                     vConts.put(vDev.key, (VirtualController)vDev);
                  }
               } catch (Exception var17) {
               }
            }
         }

         Map<ManagedObjectReference, List<VirtualDisk>> datastoreMap = new HashMap();

         Object disksInSameStore;
         VirtualDisk vdisk;
         for(Iterator var21 = vDisks.iterator(); var21.hasNext(); ((List)disksInSameStore).add(vdisk)) {
            vdisk = (VirtualDisk)var21.next();
            FileBackingInfo backing = (FileBackingInfo)vdisk.getBacking();
            disksInSameStore = (List)datastoreMap.get(backing.datastore);
            if (disksInSameStore == null) {
               disksInSameStore = new ArrayList();
               datastoreMap.put(backing.datastore, disksInSameStore);
            }
         }

         ManagedObjectReference[] keyArr = (ManagedObjectReference[])datastoreMap.keySet().toArray(new ManagedObjectReference[0]);
         if (keyArr != null && keyArr.length > 0) {
            PropertyValue[] dsTypeValues = QueryUtil.getProperties(keyArr, new String[]{"summary.type"}).getPropertyValues();
            List<ManagedObjectReference> vsanDsRefs = new ArrayList();
            PropertyValue[] var12 = dsTypeValues;
            int var11 = dsTypeValues.length;

            for(int var10 = 0; var10 < var11; ++var10) {
               PropertyValue dsType = var12[var10];
               if (dsType.value.equals("vsan")) {
                  vsanDsRefs.add((ManagedObjectReference)dsType.resourceObject);
               }
            }

            List<VirtualDisk> disksOnVsan = new ArrayList();
            Iterator var29 = vsanDsRefs.iterator();

            while(var29.hasNext()) {
               ManagedObjectReference dsRef = (ManagedObjectReference)var29.next();
               List<VirtualDisk> disks = (List)datastoreMap.get(dsRef);
               if (disks != null) {
                  disksOnVsan.addAll(disks);
               }
            }

            Map<Integer, List<VirtualDisk>> controllerMap = new HashMap();

            Object disks;
            VirtualDisk disk;
            Iterator var33;
            for(var33 = disksOnVsan.iterator(); var33.hasNext(); ((List)disks).add(disk)) {
               disk = (VirtualDisk)var33.next();
               disks = (List)controllerMap.get(disk.controllerKey);
               if (disks == null) {
                  disks = new ArrayList();
                  controllerMap.put(disk.controllerKey, disks);
               }
            }

            var33 = controllerMap.entrySet().iterator();

            while(true) {
               Entry entry;
               VirtualController controller;
               do {
                  do {
                     if (!var33.hasNext()) {
                        return vscsiEntities;
                     }

                     entry = (Entry)var33.next();
                     controller = (VirtualController)vConts.get(entry.getKey());
                  } while(controller == null);
               } while(((List)entry.getValue()).isEmpty());

               Iterator var15 = ((List)entry.getValue()).iterator();

               while(var15.hasNext()) {
                  VirtualDisk disk = (VirtualDisk)var15.next();
                  PerfVscsiEntity entity = new PerfVscsiEntity();
                  entity.busId = controller.busNumber;
                  entity.controllerKey = controller.key;
                  entity.deviceName = disk.deviceInfo.label;
                  entity.vmdkName = ((FileBackingInfo)disk.backing).fileName;
                  entity.position = disk.unitNumber;
                  vscsiEntities.add(entity);
               }
            }
         }
      }

      return vscsiEntities;
   }

   private List<PerfVirtualDiskEntity> getVirtualDiskEntities(VirtualDevice[] vDevs) throws Exception {
      List<PerfVirtualDiskEntity> virtualDiskEntities = new ArrayList();
      Map<ManagedObjectReference, List<PerfVirtualDiskEntity>> dsMap = new HashMap();
      VirtualDevice[] var7 = vDevs;
      int var6 = vDevs.length;

      ManagedObjectReference dsRef;
      for(int var5 = 0; var5 < var6; ++var5) {
         VirtualDevice dev = var7[var5];
         if (dev instanceof VirtualDisk) {
            PerfVirtualDiskEntity entity = new PerfVirtualDiskEntity();
            entity.diskName = dev.deviceInfo.label;
            entity.controllerKey = dev.controllerKey;
            FileBackingInfo backing = (FileBackingInfo)dev.getBacking();
            dsRef = backing.datastore;
            Object type = QueryUtil.getProperty(dsRef, "summary.type", (Object)null);
            if (type != null && "vsan".equalsIgnoreCase(type.toString())) {
               virtualDiskEntities.add(entity);
               entity.vmdkPath = backing.getFileName();
               List<PerfVirtualDiskEntity> disds = (List)dsMap.get(dsRef);
               if (disds == null) {
                  disds = new ArrayList();
                  dsMap.put(dsRef, disds);
               }

               ((List)disds).add(entity);
            }
         }
      }

      ManagedObjectReference[] keyArr = (ManagedObjectReference[])dsMap.keySet().toArray(new ManagedObjectReference[0]);
      int mark;
      if (keyArr != null && keyArr.length > 0) {
         PropertyValue[] dsPropsVals = QueryUtil.getProperties(keyArr, new String[]{"name", "summary.url"}).getPropertyValues();
         PropertyValue[] var23 = dsPropsVals;
         mark = dsPropsVals.length;

         label69:
         for(int var20 = 0; var20 < mark; ++var20) {
            PropertyValue propVal = var23[var20];
            dsRef = (ManagedObjectReference)propVal.resourceObject;
            List<PerfVirtualDiskEntity> pvdes = (List)dsMap.get(dsRef);
            PerfVirtualDiskEntity pvde;
            Iterator var14;
            String var25;
            switch((var25 = propVal.propertyName).hashCode()) {
            case -1193995481:
               if (!var25.equals("summary.url")) {
                  break;
               }

               var14 = pvdes.iterator();

               while(true) {
                  if (!var14.hasNext()) {
                     continue label69;
                  }

                  pvde = (PerfVirtualDiskEntity)var14.next();
                  pvde.datastorePath = propVal.value.toString();
               }
            case 3373707:
               if (var25.equals("name")) {
                  for(var14 = pvdes.iterator(); var14.hasNext(); pvde.datastoreName = propVal.value.toString()) {
                     pvde = (PerfVirtualDiskEntity)var14.next();
                  }
               }
            }
         }
      }

      PerfVirtualDiskEntity pvde;
      for(Iterator var19 = virtualDiskEntities.iterator(); var19.hasNext(); pvde.datastorePath = pvde.datastorePath.substring(mark)) {
         pvde = (PerfVirtualDiskEntity)var19.next();
         String dsNamePair = "[" + pvde.datastoreName + "] ";
         mark = pvde.vmdkPath.indexOf(dsNamePair);
         pvde.vmdkPath = mark != -1 ? pvde.vmdkPath.substring(mark + dsNamePair.length()) : pvde.vmdkPath;
         mark = "ds://".length();
      }

      return virtualDiskEntities;
   }
}
