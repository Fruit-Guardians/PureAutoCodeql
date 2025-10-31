package com.vmware.vsphere.client.vsan.data;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;

@data
public class PhysicalDiskData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public static final String DISK_USED_CAPACITY_KEY = "capacityUsed";
   public static final String DISK_RESERVED_CAPACITY_KEY = "capacityReserved";
   public static final String DISK_HEALTH_KEY = "disk_health";
   public static final String DISK_HEALTH_FLAGS_KEY = "healthFlags";
   public static final String LSOM_OBJECTS = "lsom_objects";
   public static final String CONTENT = "content";
   public static final String COMPOSITE_UUID = "compositeUuid";
   public ManagedObjectReference hostRef;
   public ManagedObjectReference clusterRef;
   public String diskName;
   public String uuid;
   public long capacity;
   public String[] operationalState;
   public Boolean ineligible;
   public String diskIssue;
   public Boolean isSsd;
   public boolean isCacheDisk;
   public String vsanDiskGroupUuid;
   public String[] physicalLocation;
   public String usedCapacity;
   public String reservedCapacity;
   public String diskHealthFlag;
   public List<String> virtualDiskUuids = new ArrayList();

   public PhysicalDiskData() {
   }

   public PhysicalDiskData(VsanDiskData diskData, ManagedObjectReference diskHostRef, JsonNode json, ManagedObjectReference clusterRef) {
      this.hostRef = diskHostRef;
      this.clusterRef = clusterRef;
      ScsiDisk disk = diskData.disk;
      this.capacity = BaseUtils.lbaToBytes(disk.capacity);
      this.operationalState = disk.operationalState;
      this.ineligible = diskData.ineligible;
      if (this.ineligible) {
         if (!StringUtils.isEmpty(diskData.stateReason)) {
            this.diskIssue = diskData.stateReason;
         }
      } else if (!ArrayUtils.isEmpty(diskData.issues)) {
         this.diskIssue = diskData.issues[0];
      }

      this.isSsd = disk.ssd;
      this.isCacheDisk = diskData.isCacheDisk;
      this.diskName = getDiskName(disk);
      this.uuid = disk.uuid;
      this.vsanDiskGroupUuid = diskData.vsanUuid;
      this.physicalLocation = disk.physicalLocation;
      JsonNode contentRoot = json != null ? json.get(diskData.vsanUuid) : null;
      if (contentRoot != null) {
         this.usedCapacity = contentRoot.get("capacityUsed").toString();
         this.reservedCapacity = contentRoot.get("capacityReserved").toString();
         JsonNode diskHealthNode = contentRoot.get("disk_health");
         this.diskHealthFlag = diskHealthNode.get("healthFlags").toString();
         JsonNode lsomObjects = contentRoot.get("lsom_objects");
         if (lsomObjects instanceof ArrayNode) {
            ArrayNode lsomArray = (ArrayNode)lsomObjects;
            Iterator iterator = lsomArray.iterator();

            while(iterator.hasNext()) {
               JsonNode lsomObjectNode = (JsonNode)iterator.next();
               JsonNode contentNode = lsomObjectNode.get("content");
               if (contentNode != null) {
                  JsonNode compositeUuidNode = contentNode.get("compositeUuid");
                  if (compositeUuidNode != null) {
                     this.virtualDiskUuids.add(compositeUuidNode.asText());
                  }
               }
            }
         }
      }

   }

   private static String getDiskName(ScsiDisk disk) {
      if (disk.displayName != null) {
         return disk.displayName;
      } else {
         return disk.canonicalName != null ? disk.canonicalName : "";
      }
   }
}
