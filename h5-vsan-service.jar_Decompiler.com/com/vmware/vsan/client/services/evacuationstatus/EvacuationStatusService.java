package com.vmware.vsan.client.services.evacuationstatus;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.host.MaintenanceSpec;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo;
import com.vmware.vim.binding.vim.vsan.host.DecommissionMode;
import com.vmware.vim.binding.vmodl.LocalizableMessage;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthGroup;
import com.vmware.vim.vsan.binding.vim.vsan.FaultDomainResourceCheckResult;
import com.vmware.vim.vsan.binding.vim.vsan.HostResourceCheckResult;
import com.vmware.vim.vsan.binding.vim.vsan.ResourceCheckResult;
import com.vmware.vim.vsan.binding.vim.vsan.ResourceCheckSpec;
import com.vmware.vim.vsan.binding.vim.vsan.ResourceCheckStatus;
import com.vmware.vim.vsan.binding.vim.vsan.ResourceCheckStatusType;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.evacuationstatus.model.ClusterEvacuationCapacityData;
import com.vmware.vsan.client.services.evacuationstatus.model.EvacuationEntity;
import com.vmware.vsan.client.services.evacuationstatus.model.EvacuationReport;
import com.vmware.vsan.client.services.evacuationstatus.model.EvacuationStatusData;
import com.vmware.vsan.client.services.evacuationstatus.model.EvacuationTaskData;
import com.vmware.vsan.client.services.evacuationstatus.model.FaultDomainEvacuationCapacityData;
import com.vmware.vsan.client.services.evacuationstatus.model.HostEvacuationCapacityData;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsService;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.health.VsanHealthData;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.whatif.VsanWhatIfComplianceStatus;
import com.vmware.vsphere.client.vsan.whatif.WhatIfPropertyProvider;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class EvacuationStatusService {
   private static final Log logger = LogFactory.getLog(EvacuationStatusService.class);
   private static final VsanProfiler profiler = new VsanProfiler(EvacuationStatusService.class);
   private static final String ENTER_MM_OPERATION_TYPE = "EnterMaintenanceMode";
   private static final String HOST_DEFAULT_ICON_ID = "vsphere-icon-host";
   private static final ArrayList<String> WARNING_MASSAGES_FILTER_KEYS = new ArrayList() {
      {
         this.add("com.vmware.vsan.whatif.compliance.hostinmaintenancemode");
      }
   };
   @Autowired
   private VsanClient vsanClient;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VirtualObjectsService virtualObjectsService;

   @TsService
   public EvacuationStatusData getEvacuationStatus(ManagedObjectReference clusterRef) throws VsanUiLocalizableException {
      EvacuationStatusData evacuationStatusData = new EvacuationStatusData();
      if (VsanCapabilityUtils.isEvacuationStatusSupportedOnCluster(clusterRef)) {
         evacuationStatusData.isEvacuationStatusSupported = true;
         ArrayList evacuationEntities = new ArrayList();

         try {
            Map<Object, Map<String, Object>> hostResponse = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"name", "config.vsanHostConfig", "primaryIconId", "config.vsanHostConfig.clusterInfo.nodeUuid", "runtime.connectionState", "runtime.inMaintenanceMode"}).getMap();
            if (CollectionUtils.isEmpty(hostResponse)) {
               logger.warn("No evacuation entities found for cluster: " + clusterRef);
               return evacuationStatusData;
            }

            Iterator var6 = hostResponse.entrySet().iterator();

            label48:
            while(true) {
               Entry mapEntry;
               Map hostProperties;
               ConfigInfo vsanConfigInfo;
               do {
                  do {
                     if (!var6.hasNext()) {
                        break label48;
                     }

                     mapEntry = (Entry)var6.next();
                     hostProperties = (Map)mapEntry.getValue();
                     vsanConfigInfo = (ConfigInfo)hostProperties.get("config.vsanHostConfig");
                  } while(vsanConfigInfo == null);
               } while(!Boolean.TRUE.equals(vsanConfigInfo.enabled));

               EvacuationEntity evacuationEntity = new EvacuationEntity();
               evacuationEntity.moRef = (ManagedObjectReference)mapEntry.getKey();
               evacuationEntity.name = (String)hostProperties.get("name");
               evacuationEntity.iconId = (String)hostProperties.get("primaryIconId");
               evacuationEntity.uuid = (String)hostProperties.get("config.vsanHostConfig.clusterInfo.nodeUuid");
               ConnectionState connectionState = (ConnectionState)hostProperties.get("runtime.connectionState");
               evacuationEntity.isHostConnected = ConnectionState.connected.equals(connectionState);
               evacuationEntity.isInMaintenanceMode = evacuationEntity.isHostConnected && (Boolean)hostProperties.get("runtime.inMaintenanceMode");
               evacuationEntities.add(evacuationEntity);
            }
         } catch (IllegalStateException var11) {
            logger.warn("Cannot retrieve evacuation entities for cluster: " + clusterRef, var11);
         } catch (Exception var12) {
            logger.error("Error encountered while retrieving evacuation entities for cluster: " + clusterRef, var12);
            throw new VsanUiLocalizableException("vsan.evacuationStatus.getEvacuationStatusFailed");
         }

         evacuationStatusData.evacuationEntities = (EvacuationEntity[])evacuationEntities.toArray(new EvacuationEntity[0]);
         return evacuationStatusData;
      } else {
         return evacuationStatusData;
      }
   }

   @TsService
   public EvacuationReport getEvacuationReport(ManagedObjectReference param1, String param2, String param3) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference runEvacuationStatus(ManagedObjectReference param1, String param2, String param3) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public List<VirtualObjectModel> getVirtualObjectsList(ManagedObjectReference clusterRef, String[] inaccessibleObjects, String[] nonCompliantObjects) throws Exception {
      List<VirtualObjectModel> virtualObjects = new ArrayList();
      List<VirtualObjectModel> vsanObjects = this.virtualObjectsService.listVirtualObjects(clusterRef);
      WhatIfPropertyProvider whatIfPropertyProvider = new WhatIfPropertyProvider();
      if (ArrayUtils.isNotEmpty(inaccessibleObjects)) {
         virtualObjects.addAll(whatIfPropertyProvider.getVsanObjects(inaccessibleObjects, vsanObjects, VsanWhatIfComplianceStatus.INACCESSIBLE));
      }

      if (ArrayUtils.isNotEmpty(nonCompliantObjects)) {
         virtualObjects.addAll(whatIfPropertyProvider.getVsanObjects(nonCompliantObjects, vsanObjects, VsanWhatIfComplianceStatus.NOT_COMPLIANT));
      }

      return virtualObjects;
   }

   @TsService
   public ManagedObjectReference runEnterMaintenanceMode(ManagedObjectReference param1, String param2, boolean param3) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   private ResourceCheckSpec getResourceCheckSpec(String decommissionModeValue, String uuid) {
      ResourceCheckSpec resourceCheckSpec = new ResourceCheckSpec();
      resourceCheckSpec.entities = new String[]{uuid};
      resourceCheckSpec.operation = "EnterMaintenanceMode";
      DecommissionMode decommissionMode = new DecommissionMode(decommissionModeValue);
      resourceCheckSpec.maintenanceSpec = new MaintenanceSpec();
      resourceCheckSpec.maintenanceSpec.setVsanMode(decommissionMode);
      return resourceCheckSpec;
   }

   private EvacuationReport parseResourceCheckStatusToEvacuationReport(ResourceCheckStatus resourceCheckStatus, String uuid, ManagedObjectReference moRef) throws Exception {
      EvacuationReport evacuationReport = new EvacuationReport();
      if (resourceCheckStatus != null && (resourceCheckStatus.result != null || resourceCheckStatus.task != null)) {
         if (resourceCheckStatus.task != null && resourceCheckStatus.result == null) {
            evacuationReport.runningTask = this.getRunningTaskData(resourceCheckStatus, moRef);
            return evacuationReport;
         } else {
            boolean isResourceCheckCompleted = ResourceCheckStatusType.resourceCheckCompleted.name().equalsIgnoreCase(resourceCheckStatus.status);
            if (!isResourceCheckCompleted) {
               return evacuationReport;
            } else {
               evacuationReport.hasEvacuationReport = true;
               ResourceCheckResult result = resourceCheckStatus.result;
               evacuationReport.status = result.status;
               evacuationReport.dataToMove = result.dataToMove;
               evacuationReport.reportDate = result.timestamp.getTime();
               if (ArrayUtils.isNotEmpty(result.messages)) {
                  List<String> messages = new ArrayList();
                  LocalizableMessage[] var11;
                  int var10 = (var11 = result.messages).length;

                  for(int var9 = 0; var9 < var10; ++var9) {
                     LocalizableMessage localizableMessage = var11[var9];
                     boolean addMessage = localizableMessage != null && localizableMessage.getMessage() != null && !WARNING_MASSAGES_FILTER_KEYS.contains(localizableMessage.getKey());
                     if (addMessage) {
                        messages.add(localizableMessage.getMessage());
                     }
                  }

                  evacuationReport.messages = (String[])messages.toArray(new String[0]);
               }

               evacuationReport.inaccessibleObjects = result.inaccessibleObjects;
               evacuationReport.nonCompliantObjects = result.nonCompliantObjects;
               evacuationReport.clusterCapacity = this.parseCapacityReport(result, uuid, moRef.getServerGuid());
               evacuationReport.healthSummary = this.parseHealthData(result, moRef.getServerGuid());
               return evacuationReport;
            }
         }
      } else {
         return evacuationReport;
      }
   }

   private EvacuationTaskData getRunningTaskData(ResourceCheckStatus resourceCheckStatus, ManagedObjectReference moRef) throws Exception {
      EvacuationTaskData runningTask = new EvacuationTaskData();
      VmodlHelper.assignServerGuid(resourceCheckStatus.task.host, moRef.getServerGuid());
      runningTask.hostName = (String)QueryUtil.getProperty(resourceCheckStatus.task.host, "name");
      if (resourceCheckStatus.parentTask != null) {
         runningTask.isMaintenanceModeTask = true;
         runningTask.taskMoRef = resourceCheckStatus.parentTask.task;
      } else {
         runningTask.taskMoRef = resourceCheckStatus.task.task;
      }

      VmodlHelper.assignServerGuid(runningTask.taskMoRef, moRef.getServerGuid());
      if (resourceCheckStatus.task.maintenanceSpec != null && resourceCheckStatus.task.maintenanceSpec.vsanMode != null) {
         String decommissionMode = resourceCheckStatus.task.maintenanceSpec.vsanMode.objectAction;

         try {
            runningTask.decommissionMode = com.vmware.vsan.client.services.diskGroups.data.DecommissionMode.valueOf(decommissionMode);
         } catch (IllegalArgumentException var6) {
            logger.error("Unexpected vSAN objectAction retrieved for host " + runningTask.hostName, var6);
         }
      }

      return runningTask;
   }

   private ClusterEvacuationCapacityData parseCapacityReport(ResourceCheckResult result, String uuid, String serverGuid) {
      ClusterEvacuationCapacityData clusterCapacityData = new ClusterEvacuationCapacityData();
      if (result.capacityThreshold != null) {
         clusterCapacityData.warningThreshold = (int)result.capacityThreshold.yellowValue;
         clusterCapacityData.errorThreshold = (int)result.capacityThreshold.redValue;
      }

      clusterCapacityData.preOperationCapacity.totalCapacity = result.capacity;
      clusterCapacityData.preOperationCapacity.usedCapacity = result.usedCapacity;
      clusterCapacityData.postOperationCapacity.totalCapacity = result.postOperationCapacity;
      clusterCapacityData.postOperationCapacity.usedCapacity = result.postOperationUsedCapacity;
      if (ArrayUtils.isEmpty(result.faultDomains)) {
         return clusterCapacityData;
      } else {
         Map<String, String> hostIdToIconIdMap = this.getHostIdToIconIdMap(result.faultDomains, serverGuid);
         FaultDomainResourceCheckResult[] var9;
         int var8 = (var9 = result.faultDomains).length;

         for(int var7 = 0; var7 < var8; ++var7) {
            FaultDomainResourceCheckResult resourceCheckResult = var9[var7];
            this.assignCapacityDataFromFaultDomainsResource(clusterCapacityData, resourceCheckResult, uuid, hostIdToIconIdMap);
         }

         return clusterCapacityData;
      }
   }

   private VsanHealthData parseHealthData(ResourceCheckResult result, String serverGuid) {
      Set<ManagedObjectReference> allMoRefs = new HashSet();
      if (result.health != null && !ArrayUtils.isEmpty(result.health.groups)) {
         VsanClusterHealthGroup[] var7;
         int var6 = (var7 = result.health.groups).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VsanClusterHealthGroup healthGroup = var7[var5];
            VsanHealthUtil.addToTestMoRefs(healthGroup, allMoRefs, serverGuid);
         }

         return VsanHealthUtil.getVsanHealthData(result.health, VsanHealthUtil.getNamesForMoRefs(allMoRefs), true);
      } else {
         return null;
      }
   }

   private Map<String, String> getHostIdToIconIdMap(FaultDomainResourceCheckResult[] faultDomains, String serverGuid) {
      List<ManagedObjectReference> hostMoRefs = new ArrayList();
      FaultDomainResourceCheckResult[] var7 = faultDomains;
      int var6 = faultDomains.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         FaultDomainResourceCheckResult resourceCheckResult = var7[var5];
         if (resourceCheckResult != null && !ArrayUtils.isEmpty(resourceCheckResult.hosts)) {
            HostResourceCheckResult[] var11;
            int var10 = (var11 = resourceCheckResult.hosts).length;

            for(int var9 = 0; var9 < var10; ++var9) {
               HostResourceCheckResult hostResource = var11[var9];
               if (hostResource != null && hostResource.host != null && !hostResource.isNew) {
                  ManagedObjectReference hostMoRef = VmodlHelper.assignServerGuid(hostResource.host, serverGuid);
                  hostMoRefs.add(hostMoRef);
               }
            }
         }
      }

      Map<String, String> hostIdToIconIdMap = new HashMap();
      if (CollectionUtils.isEmpty(hostMoRefs)) {
         return hostIdToIconIdMap;
      } else {
         try {
            DataServiceResponse hostIconIdsResponse = QueryUtil.getProperties((ManagedObjectReference[])hostMoRefs.toArray(new ManagedObjectReference[0]), new String[]{"primaryIconId"});
            Iterator var17 = hostIconIdsResponse.getResourceObjects().iterator();

            while(var17.hasNext()) {
               Object hostRef = var17.next();
               String hostIconId = (String)hostIconIdsResponse.getProperty(hostRef, "primaryIconId");
               String hostRefValue = ((ManagedObjectReference)hostRef).getValue();
               hostIdToIconIdMap.put(hostRefValue, hostIconId);
            }
         } catch (Exception var13) {
            logger.error("Cannot retrieve Primary icon ID property for the returned list of hosts.", var13);
         }

         return hostIdToIconIdMap;
      }
   }

   private void assignCapacityDataFromFaultDomainsResource(ClusterEvacuationCapacityData clusterCapacityData, FaultDomainResourceCheckResult resourceCheckResult, String uuid, Map<String, String> hostIdToIconIdMap) {
      if (resourceCheckResult != null && !ArrayUtils.isEmpty(resourceCheckResult.hosts)) {
         FaultDomainEvacuationCapacityData faultDomainCapacityData = new FaultDomainEvacuationCapacityData(resourceCheckResult.name);
         if (resourceCheckResult.isNew) {
            ++clusterCapacityData.faultDomainsNeeded;
         } else {
            HostResourceCheckResult[] var9;
            int var8 = (var9 = resourceCheckResult.hosts).length;

            for(int var7 = 0; var7 < var8; ++var7) {
               HostResourceCheckResult hostResource = var9[var7];
               this.assignCapacityDataFromHostResource(faultDomainCapacityData, hostResource, uuid, hostIdToIconIdMap);
            }

            if (StringUtils.isEmpty(faultDomainCapacityData.faultDomainName)) {
               clusterCapacityData.standaloneHosts.addAll(faultDomainCapacityData.hostsCapacityData);
            } else if (!resourceCheckResult.isNew) {
               faultDomainCapacityData.preOperationCapacity.totalCapacity = resourceCheckResult.capacity;
               faultDomainCapacityData.preOperationCapacity.usedCapacity = resourceCheckResult.usedCapacity;
               faultDomainCapacityData.postOperationCapacity.totalCapacity = resourceCheckResult.postOperationCapacity;
               faultDomainCapacityData.postOperationCapacity.usedCapacity = resourceCheckResult.postOperationUsedCapacity;
               faultDomainCapacityData.hasInsufficientSpace = !faultDomainCapacityData.isAdditionalHostNeeded && resourceCheckResult.additionalRequiredCapacity > 0L;
               clusterCapacityData.faultDomains.add(faultDomainCapacityData);
            }

         }
      }
   }

   private void assignCapacityDataFromHostResource(FaultDomainEvacuationCapacityData faultDomainCapacityData, HostResourceCheckResult hostResource, String uuid, Map<String, String> hostIdToIconIdMap) {
      if (hostResource != null) {
         if (StringUtils.isNotEmpty(faultDomainCapacityData.faultDomainName) && hostResource.isNew) {
            faultDomainCapacityData.isAdditionalHostNeeded = true;
         } else {
            HostEvacuationCapacityData hostCapacityData = new HostEvacuationCapacityData(hostResource.name);
            String hostIconId = null;
            if (!CollectionUtils.isEmpty(hostIdToIconIdMap) && hostResource.host != null) {
               hostIconId = (String)hostIdToIconIdMap.get(hostResource.host.getValue());
            }

            hostCapacityData.iconId = StringUtils.isNotEmpty(hostIconId) ? hostIconId : "vsphere-icon-host";
            hostCapacityData.isHostSelected = uuid.equals(hostResource.uuid);
            hostCapacityData.capacityNeeded = hostResource.additionalRequiredCapacity;
            hostCapacityData.preOperationCapacity.totalCapacity = hostResource.capacity;
            hostCapacityData.preOperationCapacity.usedCapacity = hostResource.usedCapacity;
            hostCapacityData.postOperationCapacity.totalCapacity = hostResource.postOperationCapacity;
            hostCapacityData.postOperationCapacity.usedCapacity = hostResource.postOperationUsedCapacity;
            if (hostResource.components != null && hostResource.maxComponents != null && hostResource.components > 0L && hostResource.maxComponents > 0L) {
               boolean isComponentLimitReached = hostResource.components.equals(hostResource.maxComponents);
               hostCapacityData.isComponentLimitReached = isComponentLimitReached;
               faultDomainCapacityData.isComponentLimitReached = isComponentLimitReached;
            }

            faultDomainCapacityData.hostsCapacityData.add(hostCapacityData);
         }
      }
   }
}
