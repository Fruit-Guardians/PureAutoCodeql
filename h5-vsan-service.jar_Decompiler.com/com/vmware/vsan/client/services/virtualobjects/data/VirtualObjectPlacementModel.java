package com.vmware.vsan.client.services.virtualobjects.data;

import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.data.VsanComponent;
import com.vmware.vsphere.client.vsan.base.data.VsanComponentState;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.data.VsanRaidConfig;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@data
public class VirtualObjectPlacementModel {
   private static final Log logger = LogFactory.getLog(VirtualObjectPlacementModel.class);
   private static final String INVALID_DISK_UUID = "Object not found";
   public String nodeUuid;
   public String label;
   public String iconId;
   public VsanComponentState state;
   public VirtualObjectPlacementModel host;
   public ManagedObjectReference navigationTarget;
   public String faultDomain;
   public VirtualObjectPlacementModel cacheDisk;
   public VirtualObjectPlacementModel capacityDisk;
   public List<VirtualObjectPlacementModel> children;

   public static class Builder {
      private Map<ManagedObjectReference, Map<String, Object>> hostData;
      private Map<String, DiskResult> diskByUuid = new HashMap();

      public Builder(List<DiskResult> claimedDisks, DataServiceResponse hostData) {
         this.hostData = hostData.getMap();
         Iterator var4 = claimedDisks.iterator();

         while(var4.hasNext()) {
            DiskResult disk = (DiskResult)var4.next();
            this.diskByUuid.put(disk.vsanUuid, disk);
         }

      }

      public List<VirtualObjectPlacementModel> build(VsanObject virtualObject) {
         if (virtualObject.rootConfig != null && !CollectionUtils.isEmpty(virtualObject.rootConfig.children)) {
            List<VirtualObjectPlacementModel> models = new ArrayList();
            Iterator var4 = virtualObject.rootConfig.children.iterator();

            while(var4.hasNext()) {
               VsanComponent component = (VsanComponent)var4.next();
               if (component instanceof VsanRaidConfig) {
                  models.add(this.buildRaid((VsanRaidConfig)component));
               } else {
                  models.add(this.buildComponent(component));
               }
            }

            return models;
         } else {
            return Collections.emptyList();
         }
      }

      private VirtualObjectPlacementModel buildRaid(VsanRaidConfig raidConfig) {
         VirtualObjectPlacementModel result = new VirtualObjectPlacementModel();
         result.label = raidConfig.type;
         if (CollectionUtils.isEmpty(raidConfig.children)) {
            return result;
         } else {
            result.children = new ArrayList();
            Iterator var4 = raidConfig.children.iterator();

            while(var4.hasNext()) {
               VsanComponent component = (VsanComponent)var4.next();
               if (component instanceof VsanRaidConfig) {
                  result.children.add(this.buildRaid((VsanRaidConfig)component));
               } else {
                  result.children.add(this.buildComponent(component));
               }
            }

            return result;
         }
      }

      private VirtualObjectPlacementModel buildComponent(VsanComponent component) {
         VirtualObjectPlacementModel result = new VirtualObjectPlacementModel();
         result.nodeUuid = component.componentUuid;
         result.label = component.type;
         result.state = component.state;
         result.host = this.buildHost(component.hostUuid);
         result.cacheDisk = this.buildDisk(component.cacheDiskUuid);
         result.capacityDisk = this.buildDisk(component.capacityDiskUuid);
         return result;
      }

      private VirtualObjectPlacementModel buildHost(String nodeUuid) {
         if (StringUtils.isEmpty(nodeUuid)) {
            return null;
         } else {
            VirtualObjectPlacementModel result = new VirtualObjectPlacementModel();
            result.nodeUuid = nodeUuid;
            Map<String, Object> hostProperties = this.getHostData(nodeUuid);
            if (hostProperties != null) {
               result.label = "" + hostProperties.get("name");
               result.iconId = "" + hostProperties.get("primaryIconId");
               result.faultDomain = "" + hostProperties.get("config.vsanHostConfig.faultDomainInfo.name");
               result.navigationTarget = (ManagedObjectReference)hostProperties.get("__resourceObject");
            } else {
               result.label = nodeUuid;
               result.iconId = "vsphere-icon-host-error";
            }

            return result;
         }
      }

      private VirtualObjectPlacementModel buildDisk(String nodeUuid) {
         if (!StringUtils.isEmpty(nodeUuid) && !"Object not found".equals(nodeUuid)) {
            VirtualObjectPlacementModel result = new VirtualObjectPlacementModel();
            result.nodeUuid = nodeUuid;
            if (this.diskByUuid.containsKey(nodeUuid)) {
               ScsiDisk disk = ((DiskResult)this.diskByUuid.get(nodeUuid)).disk;
               result.label = disk.displayName;
               if (BooleanUtils.isTrue(disk.ssd)) {
                  result.iconId = "ssd-disk-icon";
               } else {
                  result.iconId = "disk-icon";
               }
            } else {
               result.label = nodeUuid;
            }

            return result;
         } else {
            return null;
         }
      }

      private Map<String, Object> getHostData(String vsanUuid) {
         Iterator var3 = this.hostData.keySet().iterator();

         while(var3.hasNext()) {
            ManagedObjectReference hostRef = (ManagedObjectReference)var3.next();
            Map<String, Object> hostProperties = (Map)this.hostData.get(hostRef);
            if (vsanUuid.equals(hostProperties.get("config.vsanHostConfig.clusterInfo.nodeUuid"))) {
               return hostProperties;
            }
         }

         VirtualObjectPlacementModel.logger.warn("Host data not found: nodeUuid=" + vsanUuid);
         return null;
      }
   }
}
