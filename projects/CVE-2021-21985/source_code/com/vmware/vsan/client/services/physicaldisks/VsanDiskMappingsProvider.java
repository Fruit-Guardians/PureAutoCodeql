package com.vmware.vsan.client.services.physicaldisks;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsan.data.HostPhysicalMappingData;
import com.vmware.vsphere.client.vsan.data.PhysicalDiskData;
import com.vmware.vsphere.client.vsan.data.VsanDiskAndGroupData;
import com.vmware.vsphere.client.vsan.data.VsanDiskData;
import com.vmware.vsphere.client.vsan.data.VsanDiskGroupData;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class VsanDiskMappingsProvider {
   private static final Log logger = LogFactory.getLog(VsanDiskMappingsProvider.class);
   private static final String[] PHYSICAL_DISK_MAPPINGS_HOST_PROPERTIES = new String[]{"vsanDisksAndGroupsData", "vsanPhysicalDiskVirtualMapping", "vsanStorageAdapterDevices", "name", "config.vsanHostConfig.faultDomainInfo.name", "primaryIconId"};

   public List<HostPhysicalMappingData> getVsanHostsPhysicalDiskData(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         Measure measure = new Measure("getVsanHostsPhysicalDiskData");

         List var61;
         label686: {
            Throwable var10000;
            label689: {
               ResultItem[] resultItems;
               ResultItem resultItem;
               boolean var10001;
               try {
                  QuerySpec querySpec = this.getClusterHostsQuerySpec(clusterRef, PHYSICAL_DISK_MAPPINGS_HOST_PROPERTIES);
                  Throwable var7 = null;
                  resultItem = null;

                  try {
                     Measure getClusterHostsProperties = measure.start("getClusterHostsProperties");

                     try {
                        resultItems = QueryUtil.getData(querySpec).items;
                     } finally {
                        if (getClusterHostsProperties != null) {
                           getClusterHostsProperties.close();
                        }

                     }
                  } catch (Throwable var53) {
                     if (var7 == null) {
                        var7 = var53;
                     } else if (var7 != var53) {
                        var7.addSuppressed(var53);
                     }

                     throw var7;
                  }

                  if (resultItems == null) {
                     var61 = Collections.emptyList();
                     break label686;
                  }
               } catch (Throwable var56) {
                  var10000 = var56;
                  var10001 = false;
                  break label689;
               }

               ArrayList var60;
               try {
                  List<HostPhysicalMappingData> hostPhysicalMappingsData = new ArrayList();
                  ResultItem[] var11 = resultItems;
                  int var10 = resultItems.length;
                  int var59 = 0;

                  while(true) {
                     if (var59 >= var10) {
                        var60 = hostPhysicalMappingsData;
                        break;
                     }

                     resultItem = var11[var59];
                     ManagedObjectReference hostRef = (ManagedObjectReference)resultItem.resourceObject;
                     VsanDiskMappingsProvider.HostMappingData hostMappingData = this.getHostMappingData(resultItem);
                     List<PhysicalDiskData> hostDisks = this.getHostDisks(hostMappingData.diskAndGroupData, hostMappingData.json, hostRef, clusterRef);
                     HostPhysicalMappingData hostDisksData = new HostPhysicalMappingData(clusterRef, hostRef, hostMappingData.hostName, hostMappingData.primaryIconId, hostDisks, hostMappingData.vsanStorageAdapterDevices, hostMappingData.faultDomain);
                     hostPhysicalMappingsData.add(hostDisksData);
                     ++var59;
                  }
               } catch (Throwable var55) {
                  var10000 = var55;
                  var10001 = false;
                  break label689;
               }

               if (measure != null) {
                  measure.close();
               }

               label667:
               try {
                  return var60;
               } catch (Throwable var54) {
                  var10000 = var54;
                  var10001 = false;
                  break label667;
               }
            }

            var2 = var10000;
            if (measure != null) {
               measure.close();
            }

            throw var2;
         }

         if (measure != null) {
            measure.close();
         }

         return var61;
      } catch (Throwable var57) {
         if (var2 == null) {
            var2 = var57;
         } else if (var2 != var57) {
            var2.addSuppressed(var57);
         }

         throw var2;
      }
   }

   private QuerySpec getClusterHostsQuerySpec(ManagedObjectReference clusterRef, String[] properties) {
      ObjectIdentityConstraint clusterConstraint = QueryUtil.createObjectIdentityConstraint(clusterRef);
      RelationalConstraint clusterHostsConstraint = QueryUtil.createRelationalConstraint("host", clusterConstraint, true, HostSystem.class.getSimpleName());
      QuerySpec querySpecHosts = QueryUtil.buildQuerySpec((Constraint)clusterHostsConstraint, properties);
      return querySpecHosts;
   }

   private VsanDiskMappingsProvider.HostMappingData getHostMappingData(ResultItem resultItem) {
      String jsonString = "";
      VsanDiskAndGroupData diskAndGroupData = null;
      Object[] vsanStorageAdapterDevices = null;
      String hostName = "";
      String primaryIconId = "";
      String faultDomain = "";
      PropertyValue[] var11;
      int var10 = (var11 = resultItem.properties).length;

      for(int var9 = 0; var9 < var10; ++var9) {
         PropertyValue propValue = var11[var9];
         String var12;
         switch((var12 = propValue.propertyName).hashCode()) {
         case -826278890:
            if (var12.equals("primaryIconId")) {
               primaryIconId = (String)propValue.value;
               continue;
            }
            break;
         case 3373707:
            if (var12.equals("name")) {
               hostName = (String)propValue.value;
               continue;
            }
            break;
         case 260829889:
            if (var12.equals("vsanPhysicalDiskVirtualMapping")) {
               jsonString = (String)propValue.value;
               continue;
            }
            break;
         case 707737491:
            if (var12.equals("config.vsanHostConfig.faultDomainInfo.name")) {
               faultDomain = (String)propValue.value;
               continue;
            }
            break;
         case 1063446345:
            if (var12.equals("vsanDisksAndGroupsData")) {
               diskAndGroupData = (VsanDiskAndGroupData)propValue.value;
               continue;
            }
            break;
         case 1496642655:
            if (var12.equals("vsanStorageAdapterDevices")) {
               vsanStorageAdapterDevices = (Object[])propValue.value;
               continue;
            }
         }

         logger.warn("Unknown property received: " + propValue.propertyName + " = " + propValue.value);
      }

      return new VsanDiskMappingsProvider.HostMappingData(jsonString, diskAndGroupData, vsanStorageAdapterDevices, hostName, primaryIconId, faultDomain);
   }

   private List<PhysicalDiskData> getHostDisks(VsanDiskAndGroupData diskAndGroupData, JsonNode json, ManagedObjectReference hostRef, ManagedObjectReference clusterRef) {
      List<PhysicalDiskData> hostDisks = new ArrayList();
      if (diskAndGroupData != null && diskAndGroupData.vsanGroups != null) {
         VsanDiskGroupData[] var9;
         int var8 = (var9 = diskAndGroupData.vsanGroups).length;

         for(int var7 = 0; var7 < var8; ++var7) {
            VsanDiskGroupData groupData = var9[var7];
            if (groupData.disks != null) {
               PhysicalDiskData cacheHostDisk = new PhysicalDiskData(groupData.ssd, hostRef, json, clusterRef);
               hostDisks.add(cacheHostDisk);
               VsanDiskData[] var14;
               int var13 = (var14 = groupData.disks).length;

               for(int var12 = 0; var12 < var13; ++var12) {
                  VsanDiskData diskData = var14[var12];
                  PhysicalDiskData capacityHostDisk = new PhysicalDiskData(diskData, hostRef, json, clusterRef);
                  capacityHostDisk.vsanDiskGroupUuid = cacheHostDisk.vsanDiskGroupUuid;
                  hostDisks.add(capacityHostDisk);
               }
            }
         }
      }

      return hostDisks;
   }

   private class HostMappingData {
      public VsanDiskAndGroupData diskAndGroupData;
      public Object[] vsanStorageAdapterDevices;
      public String hostName;
      public String primaryIconId;
      public String faultDomain;
      public JsonNode json;

      public HostMappingData(String jsonString, VsanDiskAndGroupData diskAndGroupData, Object[] vsanStorageAdapterDevices, String hostName, String primaryIconId, String faultDomain) {
         this.json = Utils.getJsonRootNode(jsonString);
         this.diskAndGroupData = diskAndGroupData;
         this.vsanStorageAdapterDevices = vsanStorageAdapterDevices;
         this.hostName = hostName;
         this.primaryIconId = primaryIconId;
         this.faultDomain = faultDomain;
      }
   }
}
