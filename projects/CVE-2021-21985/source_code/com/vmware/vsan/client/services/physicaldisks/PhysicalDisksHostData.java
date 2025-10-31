package com.vmware.vsan.client.services.physicaldisks;

import com.vmware.vim.binding.vim.host.ScsiLun.State;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import com.vmware.vsphere.client.vsan.data.HostPhysicalMappingData;
import com.vmware.vsphere.client.vsan.data.PhysicalDiskData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@data
public class PhysicalDisksHostData {
   private static final Logger logger = LoggerFactory.getLogger(PhysicalDisksHostData.class);
   public String name;
   public String iconId;
   public ManagedObjectReference hostRef;
   public String faultDomain;
   public String vsanUuid;
   public boolean isSsd;
   public String vsanDiskGroupUuid;
   public long capacity;
   public long usedCapacity;
   public long reservedCapacity;
   public PhysicalDisksHostData.DeviceState state;
   public boolean ineligible;
   public String[] issues;
   public boolean isCacheDisk;
   public long diskHealthFlag;
   public long componentsNumber;
   public String[] physicalLocation;
   public List<PhysicalDisksHostData> physicalDisks;
   private List<String> virtualObjectUuids = new ArrayList();
   public List<VirtualObjectModel> virtualObjecs = new ArrayList();
   public Object[] deviceAdaptersData;

   public PhysicalDisksHostData() {
   }

   public PhysicalDisksHostData(HostPhysicalMappingData item) {
      this.name = item.name;
      this.iconId = item.primaryIconId;
      this.hostRef = item.hostRef;
      this.faultDomain = item.faultDomain;
      this.physicalDisks = new ArrayList();
      this.capacity = 0L;
      this.usedCapacity = 0L;
      this.reservedCapacity = 0L;
      this.componentsNumber = 0L;
      this.deviceAdaptersData = item.storageAdapterDevices;
      PhysicalDisksHostData diskItem;
      if (CollectionUtils.isNotEmpty(item.physicalDisks)) {
         for(Iterator var3 = item.physicalDisks.iterator(); var3.hasNext(); this.physicalDisks.add(diskItem)) {
            PhysicalDiskData physicalDisk = (PhysicalDiskData)var3.next();
            diskItem = new PhysicalDisksHostData();
            diskItem.name = physicalDisk.diskName;
            diskItem.vsanDiskGroupUuid = physicalDisk.vsanDiskGroupUuid;
            diskItem.vsanUuid = physicalDisk.uuid;
            diskItem.isSsd = physicalDisk.isSsd;
            diskItem.capacity = physicalDisk.capacity;
            diskItem.usedCapacity = parseLong(physicalDisk.usedCapacity);
            diskItem.reservedCapacity = parseLong(physicalDisk.reservedCapacity);
            diskItem.isCacheDisk = physicalDisk.isCacheDisk;
            if (!physicalDisk.isCacheDisk) {
               this.capacity += diskItem.capacity;
               this.usedCapacity += diskItem.usedCapacity;
               this.reservedCapacity += diskItem.reservedCapacity;
            }

            diskItem.state = PhysicalDisksHostData.DeviceState.fromScsiState(physicalDisk.operationalState);
            diskItem.ineligible = physicalDisk.ineligible;
            if (physicalDisk.diskIssue != null) {
               diskItem.issues = new String[]{physicalDisk.diskIssue};
            }

            this.diskHealthFlag = parseLong(physicalDisk.diskHealthFlag);
            if (physicalDisk.virtualDiskUuids != null) {
               diskItem.componentsNumber = (long)physicalDisk.virtualDiskUuids.size();
               diskItem.physicalLocation = physicalDisk.physicalLocation;
               this.componentsNumber += (long)physicalDisk.virtualDiskUuids.size();
               this.virtualObjectUuids.addAll(physicalDisk.virtualDiskUuids);
               diskItem.virtualObjectUuids = physicalDisk.virtualDiskUuids;
            }
         }
      }

   }

   public List<String> getVirtualObjectUuids() {
      return this.virtualObjectUuids;
   }

   public void setVirtualObjectsData(List<VirtualObjectModel> virtualObjects) {
      if (!CollectionUtils.isEmpty(virtualObjects)) {
         Map<VirtualObjectModel, VirtualObjectModel> virtualObjectsMap = new LinkedHashMap();
         Iterator var4 = this.physicalDisks.iterator();

         while(true) {
            PhysicalDisksHostData diskData;
            do {
               if (!var4.hasNext()) {
                  this.virtualObjecs = new ArrayList(virtualObjectsMap.values());
                  return;
               }

               diskData = (PhysicalDisksHostData)var4.next();
            } while(CollectionUtils.isEmpty(diskData.virtualObjectUuids));

            Set<String> diskObjectUuids = new HashSet(diskData.virtualObjectUuids);
            Iterator var7 = virtualObjects.iterator();

            while(var7.hasNext()) {
               VirtualObjectModel virtualObject = (VirtualObjectModel)var7.next();
               VirtualObjectModel clone = prepareCloneVirtualObject(virtualObject, diskObjectUuids);
               if (clone != null) {
                  diskData.virtualObjecs.add(clone);
                  if (virtualObjectsMap.containsKey(virtualObject)) {
                     VirtualObjectModel vom = (VirtualObjectModel)virtualObjectsMap.get(virtualObject);
                     vom.mergeChildren(clone);
                  } else {
                     virtualObjectsMap.put(virtualObject, clone.cloneWithChildren());
                  }
               }
            }
         }
      }
   }

   private static VirtualObjectModel prepareCloneVirtualObject(VirtualObjectModel virtualObject, Collection<String> diskObjectUuids) {
      VirtualObjectModel clone = virtualObject.cloneWithoutChildren();
      if (ArrayUtils.isNotEmpty(virtualObject.children)) {
         List<VirtualObjectModel> children = new ArrayList(virtualObject.children.length);
         VirtualObjectModel[] var7;
         int var6 = (var7 = virtualObject.children).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            VirtualObjectModel child = var7[var5];
            if (diskObjectUuids.contains(child.uid)) {
               children.add(child);
            }
         }

         clone.children = (VirtualObjectModel[])children.toArray(new VirtualObjectModel[children.size()]);
      }

      return (!StringUtils.isNotEmpty(clone.uid) || !diskObjectUuids.contains(clone.uid)) && !ArrayUtils.isNotEmpty(clone.children) ? null : clone;
   }

   private static long parseLong(String value) {
      try {
         return Long.parseLong(value);
      } catch (Exception var1) {
         logger.warn("Cannot parse to long. Probably the disk is absent: " + value);
         return 0L;
      }
   }

   @data
   public static enum DeviceState {
      OK,
      OFF,
      LOST,
      ERROR,
      UNKNOWN;

      public static PhysicalDisksHostData.DeviceState fromScsiState(String[] stateKeys) {
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
}
