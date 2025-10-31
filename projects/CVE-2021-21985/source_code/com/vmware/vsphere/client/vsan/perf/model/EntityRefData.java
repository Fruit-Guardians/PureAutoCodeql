package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfEntityInfo;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import org.apache.commons.lang.StringUtils;

@data
public class EntityRefData {
   private static final long serialVersionUID = 1L;
   public String entityRefId;
   public String metricName;
   public PerformanceObjectType performanceObjectType;
   public String objectName;
   public ManagedObjectReference managedObjectRef;
   public String managedObjectName;
   public String vsanUuid;
   public boolean isEntityMissing = false;

   public EntityRefData() {
   }

   public EntityRefData(VsanPerfEntityInfo entityInfo, ManagedObjectReference clusterRef) {
      this.entityRefId = entityInfo.entityRefId;
      this.objectName = entityInfo.entityName;
      this.performanceObjectType = this.getPerformanceObjectType(entityInfo.entityRefType);
      if (StringUtils.isEmpty(entityInfo.entityRelatedMoRef)) {
         if (this.isClusterData(this.performanceObjectType)) {
            this.managedObjectRef = clusterRef;
         } else {
            this.isEntityMissing = true;
         }
      } else {
         this.managedObjectRef = BaseUtils.generateMor(entityInfo.entityRelatedMoRef, clusterRef.getServerGuid());
      }

      if (!StringUtils.isEmpty(this.entityRefId)) {
         String[] parts = this.entityRefId.split(":");
         if (parts != null && parts.length >= 2) {
            this.metricName = parts[0];
            this.vsanUuid = parts[1];
         }
      }
   }

   private PerformanceObjectType getPerformanceObjectType(String entityRefType) {
      PerformanceObjectType result = null;
      switch(entityRefType.hashCode()) {
      case -1775894832:
         if (entityRefType.equals("capacity-disk")) {
            result = PerformanceObjectType.capacityDisk;
         }

         return result;
      case -1229779238:
         if (entityRefType.equals("cluster-domcompmgr")) {
            result = PerformanceObjectType.clusterBackend;
         }

         return result;
      case -1114158933:
         if (entityRefType.equals("vsan-pnic-net")) {
            result = PerformanceObjectType.hostPnic;
         }

         return result;
      case -982244677:
         if (entityRefType.equals("vsan-host-net")) {
            result = PerformanceObjectType.hostNet;
         }

         return result;
      case -712982753:
         if (entityRefType.equals("virtual-disk")) {
            result = PerformanceObjectType.virtualDisk;
         }

         return result;
      case -624594652:
         if (entityRefType.equals("cluster-domowner")) {
            result = PerformanceObjectType.clusterDomOwner;
         }

         return result;
      case -17571576:
         if (entityRefType.equals("cache-disk")) {
            result = PerformanceObjectType.cacheDisk;
         }

         return result;
      case 94783762:
         if (entityRefType.equals("cmmds")) {
            result = PerformanceObjectType.cmmds;
         }

         return result;
      case 112500252:
         if (entityRefType.equals("vscsi")) {
            result = PerformanceObjectType.vscsi;
         }

         return result;
      case 594137398:
         if (!entityRefType.equals("host-domowner")) {
            return result;
         }
         break;
      case 752768485:
         if (entityRefType.equals("vsan-vnic-net")) {
            result = PerformanceObjectType.hostVnic;
         }

         return result;
      case 884532648:
         if (entityRefType.equals("host-domclient")) {
            result = PerformanceObjectType.hostVmConsumption;
         }

         return result;
      case 1032473077:
         if (entityRefType.equals("clom-disk-stats")) {
            result = PerformanceObjectType.clomDiskStats;
         }

         return result;
      case 1439608399:
         if (entityRefType.equals("disk-group")) {
            result = PerformanceObjectType.diskGroup;
         }

         return result;
      case 1592855429:
         if (entityRefType.equals("virtual-machine")) {
            result = PerformanceObjectType.vm;
         }

         return result;
      case 1684192704:
         if (entityRefType.equals("clom-host-stats")) {
            result = PerformanceObjectType.clomHostStats;
         }

         return result;
      case 1740616300:
         if (!entityRefType.equals("host-domcompmgr")) {
            return result;
         }
         break;
      case 1758544762:
         if (entityRefType.equals("cluster-domclient")) {
            result = PerformanceObjectType.clusterVmConsumption;
         }

         return result;
      default:
         return result;
      }

      result = PerformanceObjectType.hostBackend;
      return result;
   }

   private boolean isClusterData(PerformanceObjectType objectType) {
      if (objectType == null) {
         return false;
      } else {
         return objectType.equals(PerformanceObjectType.clusterBackend) || objectType.equals(PerformanceObjectType.clusterDomOwner) || objectType.equals(PerformanceObjectType.clusterVmConsumption);
      }
   }
}
