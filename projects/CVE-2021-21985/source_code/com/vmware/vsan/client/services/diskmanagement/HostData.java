package com.vmware.vsan.client.services.diskmanagement;

import com.vmware.vim.binding.vim.host.StorageDeviceInfo;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Adapter;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Path;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Target;
import com.vmware.vim.binding.vim.vsan.host.ClusterStatus;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vim.vsan.host.HealthState;
import com.vmware.vim.binding.vim.vsan.host.DiskResult.State;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapability;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.ConnectionState;
import com.vmware.vsphere.client.vsan.base.data.VsanCapabilityData;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public class HostData {
   private static final Log logger = LogFactory.getLog(HostData.class);
   public static final String[] DS_HOST_PROPERTIES = new String[]{"name", "primaryIconId", "config.vsanHostConfig.faultDomainInfo.name", "runtime.connectionState", "runtime.inMaintenanceMode", "config.product.version"};
   private static final String UNKNOWN_KEY = "vsan.common.unknown";
   private static final String BLANK_VSAN_UUID_PATTERN = "00000000-0000-0000-0000-000000000000";
   public String name;
   public ConnectionState state;
   public boolean isInMaintenanceMode;
   public ManagedObjectReference hostRef;
   public DiskData[] disksInUse;
   public DiskData[] eligibleDisks;
   public DiskData[] ineligibleDisks;
   public String iconId;
   public String faultDomain;
   public Integer networkPartitionGroup;
   public boolean isWitnessHost;
   public DiskGroupData[] diskGroups;
   public String disksHealthAndVersionsJson;
   public HostData.HealthStatus healthStatus;
   public String version;
   public VsanCapabilityData capabilities;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$DiskResult$State;

   private static HostData parseHostProperties(Map<String, Object> hostProperties) {
      HostData hostData = new HostData();
      hostData.name = getStringProperty(hostProperties, "name");
      hostData.iconId = getStringProperty(hostProperties, "primaryIconId");
      hostData.faultDomain = getStringProperty(hostProperties, "config.vsanHostConfig.faultDomainInfo.name");
      hostData.isInMaintenanceMode = getBooleanProperty(hostProperties, "runtime.inMaintenanceMode");
      hostData.version = getStringProperty(hostProperties, "config.product.version");
      com.vmware.vim.binding.vim.HostSystem.ConnectionState hostState = (com.vmware.vim.binding.vim.HostSystem.ConnectionState)hostProperties.get("runtime.connectionState");
      hostData.state = ConnectionState.fromHostState(hostState);
      return hostData;
   }

   public static HostData create(ManagedObjectReference hostRef, boolean isWitness, Map<String, Object> hostProperties, DiskMapInfoEx[] diskGroups, DiskResult[] allDisks, String disksHealthAndVersionJson, StorageDeviceInfo hostDeviceInfo, ClusterStatus healthStatus, Integer networkPartition, VsanCapabilityData capabilities) {
      HostData hostData = parseHostProperties(hostProperties);
      hostData.hostRef = hostRef;
      hostData.isWitnessHost = isWitness;
      hostData.disksHealthAndVersionsJson = disksHealthAndVersionJson;
      hostData.networkPartitionGroup = networkPartition;
      hostData.healthStatus = HostData.HealthStatus.fromVmodl(healthStatus);
      hostData.capabilities = capabilities;
      Map<String, Target> targetsMap = DiskData.mapAvailableTargets(hostDeviceInfo);
      Map<String, Adapter> adaptersMap = DiskData.mapAvailableAdapters(hostDeviceInfo);
      Map<String, Path> disksMap = DiskData.mapDiskPaths(hostDeviceInfo);
      Map<String, DiskData> claimedDisksByUuid = new HashMap();
      List<DiskData> eligibleDisks = new ArrayList();
      List<DiskData> ineligibleDisks = new ArrayList();
      int i;
      if (allDisks != null) {
         DiskResult[] var20 = allDisks;
         int var19 = allDisks.length;

         for(i = 0; i < var19; ++i) {
            DiskResult vsanDisk = var20[i];
            State state = State.valueOf(vsanDisk.state);
            DiskData disk = createDiskData(targetsMap, adaptersMap, disksMap, vsanDisk);
            String vsanUuid = disk.vsanUuid;
            boolean isVsanUuidBlank = "00000000-0000-0000-0000-000000000000".equals(vsanUuid);
            if (StringUtils.isNotEmpty(vsanUuid) && !isVsanUuidBlank) {
               claimedDisksByUuid.put(vsanDisk.disk.uuid, disk);
            } else {
               switch($SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$DiskResult$State()[state.ordinal()]) {
               case 1:
                  claimedDisksByUuid.put(vsanDisk.disk.uuid, disk);
                  break;
               case 2:
                  eligibleDisks.add(disk);
                  break;
               case 3:
                  ineligibleDisks.add(disk);
                  break;
               default:
                  logger.warn("Unknown disk status: " + vsanDisk.state);
               }
            }
         }
      }

      hostData.eligibleDisks = (DiskData[])eligibleDisks.toArray(new DiskData[eligibleDisks.size()]);
      hostData.ineligibleDisks = (DiskData[])ineligibleDisks.toArray(new DiskData[ineligibleDisks.size()]);
      if (diskGroups != null) {
         DiskGroupData[] children = new DiskGroupData[diskGroups.length];

         for(i = 0; i < diskGroups.length; ++i) {
            DiskGroupData groupData = DiskGroupData.fromMapping(hostRef, diskGroups[i], claimedDisksByUuid);
            children[i] = groupData;
         }

         hostData.diskGroups = children;
      } else {
         hostData.diskGroups = new DiskGroupData[0];
      }

      hostData.disksInUse = (DiskData[])claimedDisksByUuid.values().toArray(new DiskData[claimedDisksByUuid.size()]);
      return hostData;
   }

   public static Integer getNetworkPartitionGroup(String nodeUuid, Collection<Set<String>> partitionGroups) {
      Iterator<Set<String>> iterator = partitionGroups.iterator();

      for(int index = 0; iterator.hasNext(); ++index) {
         Set<String> group = (Set)iterator.next();
         if (group.contains(nodeUuid)) {
            return index + 1;
         }
      }

      return null;
   }

   public static Map<ManagedObjectReference, VsanCapabilityData> mapCapabilities(VsanCapability[] vsanCapabilities, ManagedObjectReference clusterRef) {
      Map<ManagedObjectReference, VsanCapabilityData> hostCapabilities = new HashMap();
      if (vsanCapabilities != null) {
         VsanCapability[] var6 = vsanCapabilities;
         int var5 = vsanCapabilities.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanCapability data = var6[var4];
            VsanCapabilityData capabilityData = VsanCapabilityData.fromVsanCapability(data);
            ManagedObjectReference hostRef = new ManagedObjectReference(data.target.getType(), data.target.getValue(), clusterRef.getServerGuid());
            hostCapabilities.put(hostRef, capabilityData);
         }
      }

      return hostCapabilities;
   }

   private static DiskData createDiskData(Map<String, Target> targetsMap, Map<String, Adapter> adaptersMap, Map<String, Path> disksMap, DiskResult vsanDisk) {
      Target target = null;
      Adapter adapter = null;
      if (disksMap.containsKey(vsanDisk.disk.uuid)) {
         Path path = (Path)disksMap.get(vsanDisk.disk.uuid);
         if (targetsMap.containsKey(path.target)) {
            target = (Target)targetsMap.get(path.target);
         }

         if (adaptersMap.containsKey(path.adapter)) {
            adapter = (Adapter)adaptersMap.get(path.adapter);
         }
      }

      String vsanUuid = vsanDisk.vsanUuid;
      return DiskData.fromScsiDisk(vsanDisk.disk, vsanUuid, target, adapter);
   }

   private static String getStringProperty(Map<String, Object> properties, String propertyName) {
      try {
         return properties.get(propertyName).toString();
      } catch (Exception var3) {
         logger.warn("Unable to extract '" + propertyName + "' property: ", var3);
         return Utils.getLocalizedString("vsan.common.unknown");
      }
   }

   private static Boolean getBooleanProperty(Map<String, Object> properties, String propertyName) {
      try {
         return Boolean.parseBoolean(properties.get(propertyName).toString());
      } catch (Exception var3) {
         logger.warn("Unable to extract '" + propertyName + "' property: ", var3);
         return null;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$DiskResult$State() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$DiskResult$State;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[State.values().length];

         try {
            var0[State.eligible.ordinal()] = 2;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[State.inUse.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[State.ineligible.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$DiskResult$State = var0;
         return var0;
      }
   }

   @data
   public static enum HealthStatus {
      HEALTHY,
      UNHEALTHY,
      UNKNOWN;

      // $FF: synthetic field
      private static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$HealthState;

      public static HostData.HealthStatus fromVmodl(ClusterStatus status) {
         if (status != null && status.health != null) {
            switch($SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$HealthState()[HealthState.valueOf(status.health).ordinal()]) {
            case 2:
               return HEALTHY;
            case 3:
               return UNHEALTHY;
            default:
               return UNKNOWN;
            }
         } else {
            return UNKNOWN;
         }
      }

      // $FF: synthetic method
      static int[] $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$HealthState() {
         int[] var10000 = $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$HealthState;
         if (var10000 != null) {
            return var10000;
         } else {
            int[] var0 = new int[HealthState.values().length];

            try {
               var0[HealthState.healthy.ordinal()] = 2;
            } catch (NoSuchFieldError var3) {
            }

            try {
               var0[HealthState.unhealthy.ordinal()] = 3;
            } catch (NoSuchFieldError var2) {
            }

            try {
               var0[HealthState.unknown.ordinal()] = 1;
            } catch (NoSuchFieldError var1) {
            }

            $SWITCH_TABLE$com$vmware$vim$binding$vim$vsan$host$HealthState = var0;
            return var0;
         }
      }
   }
}
