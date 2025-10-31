package com.vmware.vsphere.client.vsan.base.impl;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.base.data.VsanComponent;
import com.vmware.vsphere.client.vsan.base.data.VsanComponentState;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.data.VsanRaidConfig;
import com.vmware.vsphere.client.vsan.base.data.VsanRootConfig;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;

public class VsanComponentsProvider {
   public static final VsanProfiler _profiler = new VsanProfiler(VsanComponentsProvider.class);
   private static final Log _logger = LogFactory.getLog(VsanComponentsProvider.class);
   private final VcClient _vcClient;

   public VsanComponentsProvider(VcClient vcClient) {
      this._vcClient = vcClient;
   }

   @TsService
   public List<VsanObject> getVsanComponents(ManagedObjectReference clusterRef, String[] vsanObjectUuids) throws Exception {
      if (ArrayUtils.isEmpty(vsanObjectUuids)) {
         return new ArrayList(0);
      } else {
         Throwable var3 = null;
         Object var4 = null;

         try {
            Measure point = new Measure("vsanInternalSystem.queryVsanObjects");

            Throwable var10000;
            label194: {
               boolean var10001;
               List var19;
               try {
                  Future<String> future = this.getVsanComponentsAsync(clusterRef, vsanObjectUuids, point);
                  var19 = VsanComponentsProvider.VsanJsonParser.parseVsanObjects((String)future.get(), Arrays.asList(vsanObjectUuids));
               } catch (Throwable var17) {
                  var10000 = var17;
                  var10001 = false;
                  break label194;
               }

               if (point != null) {
                  point.close();
               }

               label179:
               try {
                  return var19;
               } catch (Throwable var16) {
                  var10000 = var16;
                  var10001 = false;
                  break label179;
               }
            }

            var3 = var10000;
            if (point != null) {
               point.close();
            }

            throw var3;
         } catch (Throwable var18) {
            if (var3 == null) {
               var3 = var18;
            } else if (var3 != var18) {
               var3.addSuppressed(var18);
            }

            throw var3;
         }
      }
   }

   public Future<String> getVsanComponentsAsync(ManagedObjectReference clusterRef, String[] vsanObjectUuids, Measure measure) throws Exception {
      Future<String> jsonFuture = measure.newFuture("VsanObject[]");
      Throwable var5 = null;
      Object var6 = null;

      try {
         VcConnection vcConnection = this._vcClient.getConnection(clusterRef.getServerGuid());

         try {
            VsanInternalSystem vsanInternalSystem = VsanProviderUtils.getVsanInternalSystem(clusterRef, vcConnection);
            vsanInternalSystem.queryVsanObjects(vsanObjectUuids, jsonFuture);
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }

         return jsonFuture;
      } catch (Throwable var14) {
         if (var5 == null) {
            var5 = var14;
         } else if (var5 != var14) {
            var5.addSuppressed(var14);
         }

         throw var5;
      }
   }

   public static class VsanJsonParser {
      private static final int VSAN_CONFIG_INCOMPLETE = 8;

      public static List<VsanObject> parseVsanObjects(String vsanJson, List<String> objectUuids) throws Exception {
         ArrayList<VsanObject> result = new ArrayList();
         if (vsanJson != null && objectUuids != null && objectUuids.size() != 0) {
            JsonNode root = Utils.getJsonRootNode(vsanJson);
            if (root == null) {
               return result;
            } else {
               JsonNode domObjects = root.get("dom_objects");
               JsonNode lsomObjects = root.get("lsom_objects");
               JsonNode diskObjects = root.get("disk_objects");
               if (domObjects != null && lsomObjects != null && diskObjects != null) {
                  Iterator var8 = objectUuids.iterator();

                  while(true) {
                     VsanObject vsanObject;
                     JsonNode content;
                     do {
                        if (!var8.hasNext()) {
                           return result;
                        }

                        String uuid = (String)var8.next();
                        vsanObject = new VsanObject(uuid);
                        JsonNode objNode = domObjects.findPath(uuid);
                        JsonNode config = objNode.findPath("config");
                        content = config.findPath("content");
                     } while(content == null);

                     VsanRootConfig rootConfig = new VsanRootConfig();
                     Iterator elements = content.getElements();

                     while(elements.hasNext()) {
                        JsonNode contentChild = (JsonNode)elements.next();
                        if (contentChild.has("type")) {
                           String type = contentChild.get("type").asText();
                           if ("Witness".equals(type)) {
                              VsanComponent witnessComponent = getVsanComponent(diskObjects, lsomObjects, contentChild, type);
                              rootConfig.children.add(witnessComponent);
                           } else {
                              VsanRaidConfig raidConfig;
                              if ("Component".equals(type)) {
                                 raidConfig = new VsanRaidConfig();
                                 raidConfig.type = Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.raid0");
                                 raidConfig.children = getVsanComponents(diskObjects, lsomObjects, content);
                                 rootConfig.children.add(raidConfig);
                                 break;
                              }

                              raidConfig = new VsanRaidConfig();
                              raidConfig.type = getLocalizedType(type);
                              raidConfig.children = getVsanComponents(diskObjects, lsomObjects, contentChild);
                              if (raidConfig.children.size() > 0) {
                                 rootConfig.children.add(raidConfig);
                              }
                           }
                        }
                     }

                     vsanObject.rootConfig = rootConfig;
                     result.add(vsanObject);
                  }
               } else {
                  return result;
               }
            }
         } else {
            return result;
         }
      }

      private static VsanComponent getVsanComponent(JsonNode diskObjects, JsonNode lsomObjects, JsonNode contentChild, String type) {
         VsanComponent component = new VsanComponent(true);
         JsonNode componentAttribute = contentChild.get("attributes");
         if (componentAttribute == null) {
            return component;
         } else {
            component.type = getLocalizedType(type);
            component.componentUuid = getValueStringByKey(contentChild, "componentUuid");
            int stateNumber = getValueIntByKey(componentAttribute, "componentState");
            long bytesToSyncProp = getValueLongByKey(componentAttribute, "bytesToSync");
            int flagsProp = getValueIntByKey(componentAttribute, "flags");
            component.byteToSync = bytesToSyncProp;
            if (bytesToSyncProp > 0L) {
               long recoveryETA = getValueLongByKey(componentAttribute, "recoveryETA");
               component.recoveryEta = recoveryETA;
            }

            component.state = getVirtualPhysicalComponentState(stateNumber, bytesToSyncProp, flagsProp);
            String diskUuid = getValueStringByKey(contentChild, "diskUuid");
            JsonNode disk = diskObjects.get(diskUuid);
            if (diskUuid != null && diskUuid != "") {
               component.capacityDiskUuid = diskUuid;
            }

            JsonNode lsomComponent;
            if (disk != null) {
               lsomComponent = disk.get("content");
               if (lsomComponent != null) {
                  String cacheDiskUuid = getValueStringByKey(lsomComponent, "ssdUuid");
                  if (StringUtils.isNotEmpty(cacheDiskUuid)) {
                     component.cacheDiskUuid = cacheDiskUuid;
                  }
               }
            }

            lsomComponent = lsomObjects.get(component.componentUuid);
            if (lsomComponent != null) {
               component.hostUuid = getValueStringByKey(lsomComponent, "owner");
            }

            return component;
         }
      }

      private static List<VsanComponent> getVsanComponents(JsonNode diskObjects, JsonNode lsomObjects, JsonNode content) {
         List<VsanComponent> children = new ArrayList();
         Iterator elements = content.getElements();

         while(elements.hasNext()) {
            JsonNode contentChild = (JsonNode)elements.next();
            if (contentChild.has("type")) {
               String type = contentChild.get("type").asText();
               if ("Component".equals(type)) {
                  VsanComponent item = getVsanComponent(diskObjects, lsomObjects, contentChild, type);
                  children.add(item);
               } else {
                  List<VsanComponent> childrenItems = getVsanComponents(diskObjects, lsomObjects, contentChild);
                  if (childrenItems != null && childrenItems.size() > 0) {
                     VsanRaidConfig raidItem = new VsanRaidConfig();
                     raidItem.type = getLocalizedType(type);
                     raidItem.children = childrenItems;
                     children.add(raidItem);
                  }
               }
            }
         }

         return children;
      }

      private static String getLocalizedType(String type) {
         switch(type.hashCode()) {
         case -1885104965:
            if (type.equals("RAID_0")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.raid0");
            }
            break;
         case -1885104964:
            if (type.equals("RAID_1")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.raid1");
            }
            break;
         case -1885104960:
            if (type.equals("RAID_5")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.raid5");
            }
            break;
         case -1885104959:
            if (type.equals("RAID_6")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.raid6");
            }
            break;
         case -1274991335:
            if (type.equals("Witness")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.witness");
            }
            break;
         case 353045048:
            if (type.equals("Concatenation")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.concatenation");
            }
            break;
         case 604060893:
            if (type.equals("Component")) {
               return Utils.getLocalizedString("vsan.monitor.virtualPhysicalMapping.component");
            }
         }

         VsanComponentsProvider._logger.error(String.format("Unexpected type %s found while passing vSAN component types.", type));
         return type;
      }

      private static String getValueStringByKey(JsonNode node, String key) {
         return node != null && !node.isMissingNode() && node.has(key) ? node.get(key).asText() : "";
      }

      private static long getValueLongByKey(JsonNode node, String key) {
         return node != null && !node.isMissingNode() && node.has(key) ? node.get(key).asLong() : 0L;
      }

      private static int getValueIntByKey(JsonNode node, String key) {
         return node != null && !node.isMissingNode() && node.has(key) ? node.get(key).asInt() : 0;
      }

      private static VsanComponentState getVirtualPhysicalComponentState(int stateNumber, long bytesToSyncProp, int flagsProp) {
         switch(stateNumber) {
         case 5:
            if (flagsProp == 8) {
               return VsanComponentState.ACTIVE_STALE;
            }

            return VsanComponentState.ACTIVE;
         case 6:
            if (bytesToSyncProp > 0L) {
               return VsanComponentState.ABSENT_RESYNC;
            }

            return VsanComponentState.ABSENT;
         case 7:
         case 8:
         default:
            return VsanComponentState.UNKNOWN;
         case 9:
            return VsanComponentState.DEGRADED;
         case 10:
            return VsanComponentState.RECONFIG;
         }
      }
   }
}
