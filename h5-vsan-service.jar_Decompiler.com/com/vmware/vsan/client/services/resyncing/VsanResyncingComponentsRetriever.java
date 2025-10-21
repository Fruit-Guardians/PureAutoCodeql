package com.vmware.vsan.client.services.resyncing;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.vsan.host.ConfigInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.fault.InvalidArgument;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSyncingObjectFilter;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanComponentSyncState;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanObjectSyncState;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanSyncingObjectQueryResult;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanSyncingObjectRecoveryDetails;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.resyncing.data.ResyncComponent;
import com.vmware.vsan.client.services.resyncing.data.ResyncMonitorData;
import com.vmware.vsan.client.services.resyncing.data.VsanSyncingObjectsQuerySpec;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.data.ComponentIntent;
import com.vmware.vsphere.client.vsan.base.data.VsanComponent;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.util.Version;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanResyncingComponentsRetriever {
   private static final Version HOST_VERSION_2015 = new Version("6.0.0");
   private static final String[] HOST_PROPERTIES = new String[]{"name", "runtime.connectionState", "config.vsanHostConfig", "config.product.version"};
   private static final Log _logger = LogFactory.getLog(VsanResyncingComponentsRetriever.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanResyncingComponentsRetriever.class);
   @Autowired
   VcClient vcClient;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$ComponentIntent;

   public ResyncMonitorData getVsanResyncObjects(ManagedObjectReference clusterRef, VsanSyncingObjectsQuerySpec spec) throws Exception {
      VsanResyncingComponentsRetriever.HostsData hostsData = this.getHostsData(clusterRef);
      if (VsanCapabilityUtils.getCapabilities(clusterRef).isResyncETAImprovementSupported) {
         ResyncMonitorData result = this.queryResyncingObjects(clusterRef, spec, hostsData.hostNodeUuidToHostNames);
         result.isResyncFilterApiSupported = true;
         return result;
      } else {
         return this.getResyncObjects(hostsData, spec);
      }
   }

   private ResyncMonitorData queryResyncingObjects(ManagedObjectReference param1, VsanSyncingObjectsQuerySpec param2, Map<String, String> param3) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private Future<VsanSyncingObjectQueryResult>[] getResyncingObjectsFutures(ManagedObjectReference clusterRef, Measure measure, List<VsanSyncingObjectFilter> filters) throws Exception {
      List<Future<VsanSyncingObjectQueryResult>> futures = new ArrayList(filters.size());
      VsanObjectSystem vsanObjectSystem = VsanProviderUtils.getVsanObjectSystem(clusterRef);
      Iterator var7 = filters.iterator();

      while(var7.hasNext()) {
         VsanSyncingObjectFilter filter = (VsanSyncingObjectFilter)var7.next();
         Future<VsanSyncingObjectQueryResult> future = measure.newFuture("VsanObjectSystem.querySyncingVsanObjectsSummary");
         vsanObjectSystem.querySyncingVsanObjectsSummary(clusterRef, filter, future);
         futures.add(future);
      }

      return (Future[])futures.toArray(new Future[0]);
   }

   private ResyncMonitorData combineResyncingObjects(List<ResyncMonitorData> resyncingObjects) {
      if (resyncingObjects.size() == 1) {
         return (ResyncMonitorData)resyncingObjects.get(0);
      } else if (resyncingObjects.size() > 1) {
         ResyncMonitorData result = null;
         Iterator var4 = resyncingObjects.iterator();

         while(var4.hasNext()) {
            ResyncMonitorData object = (ResyncMonitorData)var4.next();
            if (object != null) {
               if (result == null) {
                  result = object;
               } else {
                  result.uniteResyncingObjects(object);
               }
            }
         }

         return result;
      } else {
         return new ResyncMonitorData();
      }
   }

   private ResyncMonitorData getResyncObjects(VsanResyncingComponentsRetriever.HostsData hostsData, VsanSyncingObjectsQuerySpec spec) throws Exception {
      List<ManagedObjectReference> hostRefsEnhancedApi = new ArrayList();
      List<ManagedObjectReference> hostRefsLegacy = new ArrayList();
      Iterator var6 = hostsData.hostConnectionStates.keySet().iterator();

      while(var6.hasNext()) {
         ManagedObjectReference hostRef = (ManagedObjectReference)var6.next();
         if (ConnectionState.connected.equals(hostsData.hostConnectionStates.get(hostRef))) {
            if (VsanCapabilityUtils.isResyncEnhancedApiSupported(hostRef)) {
               hostRefsEnhancedApi.add(hostRef);
            } else {
               Version esxVersion = (Version)hostsData.hostVersions.get(hostRef);
               if (esxVersion != null && esxVersion.compareTo(HOST_VERSION_2015) >= 0) {
                  hostRefsLegacy.add(hostRef);
               }
            }
         }
      }

      if (!CollectionUtils.isEmpty(hostRefsEnhancedApi)) {
         ResyncMonitorData result = this.getResyncData(hostRefsEnhancedApi, spec, hostsData.hostNodeUuidToHostNames);
         result.isResyncFilterApiSupported = true;
         return result;
      } else if (!CollectionUtils.isEmpty(hostRefsLegacy)) {
         return this.getLegacyVsanResyncObjects(hostRefsLegacy, hostsData.hostNodeUuidToHostNames);
      } else {
         return new ResyncMonitorData();
      }
   }

   private ResyncMonitorData getResyncData(List<ManagedObjectReference> param1, VsanSyncingObjectsQuerySpec param2, Map<String, String> param3) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private ResyncMonitorData getLegacyVsanResyncObjects(List<ManagedObjectReference> param1, Map<String, String> param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private VsanSyncingObjectQueryResult convertLegacyDataToVsanSyncingObjects(Set<VsanObject> resyncObjects) {
      VsanSyncingObjectQueryResult syncingObjectQueryResult = new VsanSyncingObjectQueryResult(0L, 0L, -1L, new VsanObjectSyncState[0], (VsanSyncingObjectRecoveryDetails)null);
      if (CollectionUtils.isEmpty(resyncObjects)) {
         return syncingObjectQueryResult;
      } else {
         long componentsCount = 0L;
         long componentsBytesToSync = 0L;
         long recoveryEta = -1L;
         List<VsanObjectSyncState> syncObjects = new ArrayList();
         Iterator var11 = resyncObjects.iterator();

         while(var11.hasNext()) {
            VsanObject resyncObject = (VsanObject)var11.next();
            List<VsanComponentSyncState> components = new ArrayList();
            Iterator var14 = resyncObject.rootConfig.children.iterator();

            while(var14.hasNext()) {
               VsanComponent vsanComponent = (VsanComponent)var14.next();
               if (vsanComponent.byteToSync > 0L) {
                  VsanComponentSyncState vsanResyncComponent = new VsanComponentSyncState(vsanComponent.componentUuid, vsanComponent.capacityDiskUuid, vsanComponent.hostUuid, vsanComponent.byteToSync, vsanComponent.recoveryEta, new String[]{this.intentToResyncReason(vsanComponent.intent)});
                  components.add(vsanResyncComponent);
                  ++componentsCount;
                  componentsBytesToSync += vsanComponent.byteToSync;
                  recoveryEta = Math.max(vsanComponent.recoveryEta, recoveryEta);
               }
            }

            VsanObjectSyncState vsanSyncObject = new VsanObjectSyncState(resyncObject.vsanObjectUuid, (VsanComponentSyncState[])components.toArray(new VsanComponentSyncState[components.size()]));
            syncObjects.add(vsanSyncObject);
         }

         syncingObjectQueryResult.objects = (VsanObjectSyncState[])syncObjects.toArray(new VsanObjectSyncState[syncObjects.size()]);
         syncingObjectQueryResult.setTotalObjectsToSync(componentsCount);
         syncingObjectQueryResult.setTotalBytesToSync(componentsBytesToSync);
         syncingObjectQueryResult.setTotalRecoveryETA(recoveryEta);
         return syncingObjectQueryResult;
      }
   }

   private String intentToResyncReason(ComponentIntent intent) {
      switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$ComponentIntent()[intent.ordinal()]) {
      case 1:
      case 4:
         return ResyncComponent.ResyncReasonCode.repair.toString();
      case 2:
         return ResyncComponent.ResyncReasonCode.evacuate.toString();
      case 3:
         return ResyncComponent.ResyncReasonCode.rebalance.toString();
      case 5:
         return ResyncComponent.ResyncReasonCode.reconfigure.toString();
      case 6:
         return ResyncComponent.ResyncReasonCode.dying_evacuate.toString();
      case 7:
         return ResyncComponent.ResyncReasonCode.stale.toString();
      case 8:
         return ResyncComponent.ResyncReasonCode.merge_concat.toString();
      default:
         throw new InvalidArgument("Invalid intent code received from server side:" + intent.toString());
      }
   }

   private VsanResyncingComponentsRetriever.HostsData getHostsData(ManagedObjectReference clusterRef) throws Exception {
      VsanResyncingComponentsRetriever.HostsData hostsData = new VsanResyncingComponentsRetriever.HostsData((VsanResyncingComponentsRetriever.HostsData)null);
      Map<ManagedObjectReference, ConfigInfo> hostToVsanHostConfigInfo = new HashMap();
      HashMap hostNames = new HashMap();

      try {
         DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), HOST_PROPERTIES);
         if (response == null) {
            return hostsData;
         } else {
            Iterator var7 = response.getResourceObjects().iterator();

            while(var7.hasNext()) {
               Object resourceObject = var7.next();
               ManagedObjectReference hostRef = (ManagedObjectReference)resourceObject;
               hostNames.put(hostRef, (String)response.getProperty(hostRef, "name"));
               hostsData.hostConnectionStates.put(hostRef, (ConnectionState)response.getProperty(hostRef, "runtime.connectionState"));
               hostToVsanHostConfigInfo.put(hostRef, (ConfigInfo)response.getProperty(hostRef, "config.vsanHostConfig"));
               hostsData.hostVersions.put(hostRef, new Version((String)response.getProperty(hostRef, "config.product.version")));
            }

            var7 = hostNames.keySet().iterator();

            while(true) {
               while(var7.hasNext()) {
                  ManagedObjectReference hostRef = (ManagedObjectReference)var7.next();
                  ConfigInfo vsanConfig = (ConfigInfo)hostToVsanHostConfigInfo.get(hostRef);
                  if (vsanConfig != null && vsanConfig.enabled && vsanConfig.clusterInfo != null && vsanConfig.clusterInfo.nodeUuid != null) {
                     String nodeUuid = vsanConfig.clusterInfo.nodeUuid;
                     String hostName = (String)hostNames.get(hostRef);
                     hostsData.hostNodeUuidToHostNames.put(nodeUuid, hostName);
                  } else {
                     hostsData.hostConnectionStates.remove(hostRef);
                  }
               }

               return hostsData;
            }
         }
      } catch (Exception var11) {
         _logger.error("Failed to retrieve host names: ", var11);
         return hostsData;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$ComponentIntent() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$ComponentIntent;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[ComponentIntent.values().length];

         try {
            var0[ComponentIntent.DECOM.ordinal()] = 2;
         } catch (NoSuchFieldError var8) {
         }

         try {
            var0[ComponentIntent.FIXCOMPLIANCE.ordinal()] = 4;
         } catch (NoSuchFieldError var7) {
         }

         try {
            var0[ComponentIntent.MERGE_CONTACT.ordinal()] = 8;
         } catch (NoSuchFieldError var6) {
         }

         try {
            var0[ComponentIntent.MOVE.ordinal()] = 6;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[ComponentIntent.POLICYCHANGE.ordinal()] = 5;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[ComponentIntent.REBALANCE.ordinal()] = 3;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[ComponentIntent.REPAIR.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[ComponentIntent.STALE.ordinal()] = 7;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$ComponentIntent = var0;
         return var0;
      }
   }

   private static class HostsData {
      Map<String, String> hostNodeUuidToHostNames;
      Map<ManagedObjectReference, ConnectionState> hostConnectionStates;
      Map<ManagedObjectReference, Version> hostVersions;

      private HostsData() {
         this.hostNodeUuidToHostNames = new HashMap();
         this.hostConnectionStates = new HashMap();
         this.hostVersions = new HashMap();
      }

      // $FF: synthetic method
      HostsData(VsanResyncingComponentsRetriever.HostsData var1) {
         this();
      }
   }

   private static class VsanJsonParser {
      private static final String DOM_OBJECTS_KEY = "dom_objects";
      private static final String LSOM_OBJECTS_KEY = "lsom_objects";
      private static final String CONFIG_KEY = "config";
      private static final String CONTENT_KEY = "content";
      private static final String TYPE_KEY = "type";
      private static final String COMPONENT_TYPE = "Component";
      private static final String ATTRIBUTE_KEY = "attributes";
      private static final String OWNER_KEY = "owner";

      public static Set<VsanObject> parseVsanResyncObjects(String resyncDataJsonStr, Map<String, String> hostNodeUuidsToHostNames) throws Exception {
         HashSet result = new HashSet();

         try {
            Throwable var3 = null;
            Object var4 = null;

            try {
               VsanProfiler.Point point = VsanResyncingComponentsRetriever._profiler.point("parseVsanResyncObjects");

               VsanProfiler.Point var10000;
               try {
                  JsonNode root = Utils.getJsonRootNode(resyncDataJsonStr);
                  if (root == null) {
                     return var10000;
                  }

                  Map<String, String> componentUuidsToHostNames = getComponentUuidToHostNameMap(root, hostNodeUuidsToHostNames);
                  JsonNode domObjects = root.get("dom_objects");
                  if (domObjects != null) {
                     Iterator fnIterator = domObjects.getFieldNames();

                     while(fnIterator.hasNext()) {
                        String vsanObjectUuid = (String)fnIterator.next();
                        JsonNode vsanObjectNode = domObjects.path(vsanObjectUuid).path("config").path("content");
                        if (!vsanObjectNode.isMissingNode()) {
                           List<VsanComponent> components = getComponentObjects(vsanObjectNode, componentUuidsToHostNames);
                           if (components.size() > 0) {
                              VsanObject vmObjectData = new VsanObject(vsanObjectUuid, components);
                              result.add(vmObjectData);
                           }
                        }
                     }

                     return result;
                  }
               } finally {
                  var10000 = point;
                  if (point != null) {
                     var10000 = point;
                     point.close();
                  }

               }

               return var10000;
            } catch (Throwable var21) {
               if (var3 == null) {
                  var3 = var21;
               } else if (var3 != var21) {
                  var3.addSuppressed(var21);
               }

               throw var3;
            }
         } catch (Exception var22) {
            VsanResyncingComponentsRetriever._logger.error("Failed to parse vsan resyncing data JSON string. ", var22);
            throw var22;
         }
      }

      private static Map<String, String> getComponentUuidToHostNameMap(JsonNode root, Map<String, String> hostNodeUuidsToHostNames) {
         Map<String, String> result = new HashMap();
         JsonNode lsomObjects = root.get("lsom_objects");
         if (lsomObjects == null) {
            return result;
         } else {
            Iterator fnIterator = lsomObjects.getFieldNames();

            while(fnIterator.hasNext()) {
               String vsanObjectUuid = (String)fnIterator.next();
               JsonNode vsanObjectNode = lsomObjects.get(vsanObjectUuid);
               if (vsanObjectNode != null && !vsanObjectNode.isMissingNode()) {
                  String ownerUuid = vsanObjectNode.path("owner").getTextValue();
                  if (ownerUuid != null && hostNodeUuidsToHostNames.containsKey(ownerUuid)) {
                     result.put(vsanObjectUuid, (String)hostNodeUuidsToHostNames.get(ownerUuid));
                  }
               }
            }

            return result;
         }
      }

      private static List<VsanComponent> getComponentObjects(JsonNode node, Map<String, String> componentUuidsToHostNames) {
         List<VsanComponent> components = new ArrayList();
         if (node != null && !node.isMissingNode() && node.has("type")) {
            if ("Component".equals(node.get("type").getTextValue())) {
               JsonNode attributeNode = node.get("attributes");
               if (attributeNode == null) {
                  VsanResyncingComponentsRetriever._logger.warn("Missing attributes field for component node.");
                  return components;
               }

               VsanComponent componentData = new VsanComponent(node, attributeNode, componentUuidsToHostNames);
               components.add(componentData);
            } else {
               Iterator childNodes = node.getElements();

               while(childNodes.hasNext()) {
                  List<VsanComponent> childComponents = getComponentObjects((JsonNode)childNodes.next(), componentUuidsToHostNames);
                  components.addAll(childComponents);
               }
            }

            return components;
         } else {
            return components;
         }
      }
   }
}
