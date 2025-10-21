package com.vmware.vsan.client.services.diskmanagement;

import com.vmware.vim.binding.vim.host.BlockAdapterTargetTransport;
import com.vmware.vim.binding.vim.host.FibreChannelOverEthernetTargetTransport;
import com.vmware.vim.binding.vim.host.FibreChannelTargetTransport;
import com.vmware.vim.binding.vim.host.InternetScsiTargetTransport;
import com.vmware.vim.binding.vim.host.ParallelScsiTargetTransport;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.host.SerialAttachedTargetTransport;
import com.vmware.vim.binding.vim.host.StorageDeviceInfo;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Adapter;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Path;
import com.vmware.vim.binding.vim.host.PlugStoreTopology.Target;
import com.vmware.vim.binding.vim.host.ScsiLun.State;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.data.DiskLocalityType;
import com.vmware.vsphere.client.vsan.util.FormatUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public class DiskData {
   private static final Logger logger = LoggerFactory.getLogger(DiskData.class);
   private static final String ADAPTER_ID_PREFIX = "key-vim.host.PlugStoreTopology.Adapter-";
   private static final String DEVICE_ID_PREFIX = "key-vim.host.PlugStoreTopology.Device-";
   public String name;
   public boolean isFlash;
   public boolean isMappedAsCache;
   public long capacity;
   public DiskData.DeviceState deviceState;
   public String uuid;
   public String vsanUuid;
   public String diskGroup;
   public DiskLocalityType driveLocality;
   public String[] physicalLocation;
   public String diskAdapter;
   public DiskData.StorageDeviceTransport transportType;
   public String vendor;
   public ScsiDisk disk;

   public static DiskData fromScsiDisk(ScsiDisk disk, String diskGroup, boolean isMappedAsCache, String vsanUuid, Target target, Adapter adapter) {
      DiskData data = new DiskData();
      data.disk = disk;
      data.name = disk.displayName != null ? disk.displayName : disk.canonicalName;
      data.isFlash = Boolean.TRUE.equals(disk.ssd);
      data.isMappedAsCache = isMappedAsCache;
      data.capacity = disk.capacity.block * (long)disk.capacity.blockSize;
      data.deviceState = DiskData.DeviceState.fromScsiState(disk.operationalState);
      data.uuid = disk.uuid;
      if (adapter != null) {
         data.diskAdapter = extractDiskId(adapter.key, "key-vim.host.PlugStoreTopology.Adapter-");
      }

      if (target != null) {
         data.transportType = DiskData.StorageDeviceTransport.getTransport(target);
      }

      data.vsanUuid = vsanUuid;
      data.diskGroup = diskGroup;
      data.physicalLocation = disk.physicalLocation;
      data.vendor = disk.vendor + disk.model + FormatUtil.getStorageFormatted(data.capacity, 1L, 1073741824L);
      if (disk.localDisk != null) {
         data.driveLocality = disk.localDisk ? DiskLocalityType.Local : DiskLocalityType.Remote;
      } else {
         data.driveLocality = DiskLocalityType.Unknown;
      }

      return data;
   }

   public static DiskData fromScsiDisk(ScsiDisk disk, String vsanUuid, Target target, Adapter adapter) {
      return fromScsiDisk(disk, (String)null, false, vsanUuid, target, adapter);
   }

   private static String extractDiskId(String deviceId, String prefix) {
      if (!deviceId.startsWith(prefix)) {
         throw new IllegalStateException("illegal device ID: " + deviceId + ", should start with " + prefix);
      } else {
         return deviceId.substring(prefix.length());
      }
   }

   public static Map<String, Target> mapAvailableTargets(StorageDeviceInfo info) {
      Map<String, Target> result = new HashMap();
      if (info != null && info.plugStoreTopology != null && info.plugStoreTopology.target != null) {
         Target[] var5;
         int var4 = (var5 = info.plugStoreTopology.target).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            Target target = var5[var3];
            result.put(target.getKey(), target);
         }
      }

      return result;
   }

   public static Map<String, Adapter> mapAvailableAdapters(StorageDeviceInfo info) {
      Map<String, Adapter> result = new HashMap();
      if (info != null && info.plugStoreTopology != null && info.plugStoreTopology.adapter != null) {
         Adapter[] var5;
         int var4 = (var5 = info.plugStoreTopology.adapter).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            Adapter adapter = var5[var3];
            result.put(adapter.getKey(), adapter);
         }
      }

      return result;
   }

   public static Map<String, Path> mapDiskPaths(StorageDeviceInfo info) {
      Map<String, Path> disksMap = new HashMap();
      if (info != null && info.plugStoreTopology != null) {
         Path[] var5;
         int var4 = (var5 = info.plugStoreTopology.path).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            Path path = var5[var3];
            if (!StringUtils.isEmpty(path.device)) {
               disksMap.put(extractDiskId(path.device, "key-vim.host.PlugStoreTopology.Device-"), path);
            }
         }
      }

      return disksMap;
   }

   @data
   public static enum DeviceState {
      OK,
      OFF,
      LOST,
      ERROR,
      UNKNOWN;

      public static DiskData.DeviceState fromScsiState(String[] stateKeys) {
         Set<State> states = new HashSet();
         String[] var5 = stateKeys;
         int var4 = stateKeys.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            String key = var5[var3];
            states.add(State.valueOf(key));
         }

         if (states.contains(State.ok)) {
            return OK;
         } else if (states.contains(State.off)) {
            return OFF;
         } else if (states.contains(State.lostCommunication)) {
            return LOST;
         } else {
            return states.contains(State.error) ? ERROR : UNKNOWN;
         }
      }
   }

   @data
   public static enum StorageDeviceTransport {
      FCOETRANSPORT,
      FCTRANSPORT,
      ISCSITRANSPORT,
      PARALLELTRANSPORT,
      BLOCKTRANSPORT,
      SASTRANSPORT,
      PCIETRANSPORT,
      RDMATRANSPORT,
      UNKNOWN;

      public static DiskData.StorageDeviceTransport getTransport(Target target) {
         if (target != null && target.transport != null) {
            if (target.transport instanceof FibreChannelOverEthernetTargetTransport) {
               return FCOETRANSPORT;
            } else if (target.transport instanceof FibreChannelTargetTransport) {
               return FCTRANSPORT;
            } else if (target.transport instanceof InternetScsiTargetTransport) {
               return ISCSITRANSPORT;
            } else if (target.transport instanceof ParallelScsiTargetTransport) {
               return PARALLELTRANSPORT;
            } else if (target.transport instanceof BlockAdapterTargetTransport) {
               return BLOCKTRANSPORT;
            } else if (target.transport instanceof SerialAttachedTargetTransport) {
               return SASTRANSPORT;
            } else {
               DiskData.logger.warn("Unknown transport type: " + target.transport + ". Returning UNKNOWN instead.");
               return UNKNOWN;
            }
         } else {
            return null;
         }
      }
   }
}
