package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.binding.vim.VsanUpgradeSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vmodl.ManagedObject;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.types.VmodlType;
import com.vmware.vim.vmomi.core.types.VmodlTypeMap;
import com.vmware.vim.vsan.binding.vim.VsanPhoneHomeSystem;
import com.vmware.vim.vsan.binding.vim.VsanUpgradeSystemEx;
import com.vmware.vim.vsan.binding.vim.VsanVcPrecheckerSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanCapabilitySystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterMgmtInternalSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerformanceManager;
import com.vmware.vim.vsan.binding.vim.cluster.VsanSpaceReportSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcDiskManagementSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVumSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vim.vsan.binding.vim.host.VsanUpdateManager;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceSystem;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVdsSystem;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanServiceImpl implements VsanService {
   private static final Log _logger = LogFactory.getLog(VsanServiceImpl.class);
   private static final String MO_ID_START_INDEX = "-";
   private final VmodlTypeMap _typeMap;
   private final RequestContext _sessionContext;
   private final Client _vmomiClient;
   private final String _serviceGuid;
   private VsanVcStretchedClusterSystem _vsanStretchedClusterSystem;
   private VsanVcDiskManagementSystem _vsanDiskManagementSystem;
   private VsanVcClusterConfigSystem _vsanConfigSystem;
   private VsanPerformanceManager _vsanPerformanceManager;
   private VsanUpgradeSystem _vsanUpgradeSystem;
   private VsanUpgradeSystem _vsanLegacyUpgradeSystem;
   private VsanUpgradeSystemEx _vsanupgradeSystemEx;
   private VsanVcClusterHealthSystem _vsanVcClusterHealthSystem;
   private VsanObjectSystem _vsanObjectSystem;
   private VsanIscsiTargetSystem _vsanIscsiSystem;
   private VsanSpaceReportSystem _vsanSpaceReportSystem;
   private VsanCapabilitySystem _vsanCapabilitySystem;
   private VsanUpdateManager _vsanUpdateManager;
   private VsanVdsSystem _vsanVdsSystem;
   private VsanVcPrecheckerSystem _vsanPrecheckerSystem;
   private VsanPhoneHomeSystem _vsanPhoneHomeSystem;
   private VsanClusterMgmtInternalSystem _vsanClusterMgmtInternalSystem;
   private VsanVumSystem _vsanVumSystem;
   private VsanFileServiceSystem _vsanFileServiceSystem;

   public VsanServiceImpl(Client vmomiClient, VmodlTypeMap vmodlTypeMap, RequestContext sessionContext, String serviceGuid) {
      this._typeMap = vmodlTypeMap;
      this._sessionContext = sessionContext;
      this._vmomiClient = vmomiClient;
      this._serviceGuid = serviceGuid;
   }

   public <T extends ManagedObject> T getManagedObject(ManagedObjectReference moRef) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      ManagedObject var7;
      try {
         Thread.currentThread().setContextClassLoader(VsanServiceImpl.class.getClassLoader());
         VmodlType vmodlType = this._typeMap.getVmodlType(moRef.getType());
         Class<T> typeClass = vmodlType.getTypeClass();
         T result = this._vmomiClient.createStub(typeClass, moRef);
         ((Stub)result)._setRequestContext(this._sessionContext);
         var7 = result;
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var7;
   }

   private <T extends ManagedObject> T createStub(VsanServiceImpl.VsanMO vsanMo) {
      return this.createStub(vsanMo.type, vsanMo.id, (ManagedObjectReference)null);
   }

   private <T extends ManagedObject> T createStub(VsanServiceImpl.VsanMO vsanMo, ManagedObjectReference moRef) {
      if (moRef == null) {
         _logger.warn("The given Managed Object is null.");
         return this.createStub(vsanMo);
      } else {
         String id = vsanMo.id;
         String moId = getMoId(moRef);
         if (!StringUtils.isEmpty(moId)) {
            id = id + moId;
         } else {
            _logger.warn("The ID cannot be extracted from this ManagedObject: " + moId);
         }

         return this.createStub(vsanMo.type, id, moRef);
      }
   }

   private static String getMoId(ManagedObjectReference moRef) {
      int index = moRef.getValue().indexOf("-");
      return moRef.getValue().substring(index);
   }

   private <T extends ManagedObject> T createStub(String moRefType, String moRefId, ManagedObjectReference moRef) {
      String serverGuild = moRef != null ? moRef.getServerGuid() : null;
      return this.getManagedObject(new ManagedObjectReference(moRefType, moRefId, serverGuild));
   }

   public String getServiceGuid() {
      return this._serviceGuid;
   }

   public void logout() {
      try {
         if (this._vmomiClient != null) {
            this._vmomiClient.shutdown();
         }
      } catch (Exception var2) {
         _logger.error("Failed to shutdown vlsi client: " + var2.getMessage());
      }

   }

   public VsanVcStretchedClusterSystem getVsanStretchedClusterSystem() {
      if (this._vsanStretchedClusterSystem == null) {
         this._vsanStretchedClusterSystem = (VsanVcStretchedClusterSystem)this.createStub(VsanServiceImpl.VsanMO.STRETCHED_CLUSTER);
      }

      return this._vsanStretchedClusterSystem;
   }

   public VsanVcClusterConfigSystem getVsanConfigSystem() {
      if (this._vsanConfigSystem == null) {
         this._vsanConfigSystem = (VsanVcClusterConfigSystem)this.createStub(VsanServiceImpl.VsanMO.CLUSTER_CONFIG_SYSTEM);
      }

      return this._vsanConfigSystem;
   }

   public VsanVcDiskManagementSystem getVsanDiskManagementSystem() {
      if (this._vsanDiskManagementSystem == null) {
         this._vsanDiskManagementSystem = (VsanVcDiskManagementSystem)this.createStub(VsanServiceImpl.VsanMO.DISK_MANAGEMENT_SYSTEM);
      }

      return this._vsanDiskManagementSystem;
   }

   public VsanPerformanceManager getVsanPerformanceManager() {
      if (this._vsanPerformanceManager == null) {
         this._vsanPerformanceManager = (VsanPerformanceManager)this.createStub(VsanServiceImpl.VsanMO.PERFORMANCE_MANAGER);
      }

      return this._vsanPerformanceManager;
   }

   public VsanUpgradeSystem getVsanUpgradeSystem() {
      if (this._vsanUpgradeSystem == null) {
         this._vsanUpgradeSystem = (VsanUpgradeSystem)this.createStub(VsanServiceImpl.VsanMO.UPGRADE_SYSTEM);
      }

      return this._vsanUpgradeSystem;
   }

   public VsanUpgradeSystem getVsanLegacyUpgradeSystem() {
      if (this._vsanLegacyUpgradeSystem == null) {
         this._vsanLegacyUpgradeSystem = (VsanUpgradeSystem)this.createStub(VsanServiceImpl.VsanMO.LEGACY_UPGRADE_SYSTEM);
      }

      return this._vsanLegacyUpgradeSystem;
   }

   public VsanUpgradeSystemEx getVsanUpgradeSystemEx() {
      if (this._vsanupgradeSystemEx == null) {
         this._vsanupgradeSystemEx = (VsanUpgradeSystemEx)this.createStub(VsanServiceImpl.VsanMO.UPGRADE_SYSTEM_EX);
      }

      return this._vsanupgradeSystemEx;
   }

   public VsanVcClusterHealthSystem getVsanVcClusterHealthSystem() {
      if (this._vsanVcClusterHealthSystem == null) {
         this._vsanVcClusterHealthSystem = (VsanVcClusterHealthSystem)this.createStub(VsanServiceImpl.VsanMO.VC_CLUSTER_HEALTH_SYSTEM);
      }

      return this._vsanVcClusterHealthSystem;
   }

   public VsanObjectSystem getVsanObjectSystem() {
      if (this._vsanObjectSystem == null) {
         this._vsanObjectSystem = (VsanObjectSystem)this.createStub(VsanServiceImpl.VsanMO.OBJECT_SYSTEM);
      }

      return this._vsanObjectSystem;
   }

   public VsanIscsiTargetSystem getVsanIscsiSystem() {
      if (this._vsanIscsiSystem == null) {
         this._vsanIscsiSystem = (VsanIscsiTargetSystem)this.createStub(VsanServiceImpl.VsanMO.ISCSI_TARGET_SYSTEM);
      }

      return this._vsanIscsiSystem;
   }

   public VsanSpaceReportSystem getVsanSpaceReportSystem() {
      if (this._vsanSpaceReportSystem == null) {
         this._vsanSpaceReportSystem = (VsanSpaceReportSystem)this.createStub(VsanServiceImpl.VsanMO.SPACE_REPORTING_SYSTEM);
      }

      return this._vsanSpaceReportSystem;
   }

   public VsanCapabilitySystem getVsanCapabilitySystem() {
      if (this._vsanCapabilitySystem == null) {
         this._vsanCapabilitySystem = (VsanCapabilitySystem)this.createStub(VsanServiceImpl.VsanMO.CAPABILITY_SYSTEM);
      }

      return this._vsanCapabilitySystem;
   }

   public VsanSystemEx getVsanSystemEx(ManagedObjectReference moRef) {
      VsanSystemEx vsanSystemEx = (VsanSystemEx)this.createStub(VsanServiceImpl.VsanMO.SYSTEM_EX, moRef);
      return vsanSystemEx;
   }

   public VsanSystem getVsanSystem(ManagedObjectReference moRef) {
      VsanSystem vsanSystem = (VsanSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_SYSTEM, moRef);
      return vsanSystem;
   }

   public VsanUpdateManager getUpdateManager() {
      if (this._vsanUpdateManager == null) {
         this._vsanUpdateManager = (VsanUpdateManager)this.createStub(VsanServiceImpl.VsanMO.VSAN_UPDATE_MANAGER);
      }

      return this._vsanUpdateManager;
   }

   public VsanVdsSystem getVdsSystem() {
      if (this._vsanVdsSystem == null) {
         this._vsanVdsSystem = (VsanVdsSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_VDS_SYSTEM);
      }

      return this._vsanVdsSystem;
   }

   public VsanVcPrecheckerSystem getVsanPreCheckerSystem() {
      if (this._vsanPrecheckerSystem == null) {
         this._vsanPrecheckerSystem = (VsanVcPrecheckerSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_VC_PRECHECKER_SYSTEM);
      }

      return this._vsanPrecheckerSystem;
   }

   public VsanPhoneHomeSystem getPhoneHomeSystem() {
      if (this._vsanPhoneHomeSystem == null) {
         this._vsanPhoneHomeSystem = (VsanPhoneHomeSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_PHONEHOME_SYSTEM);
      }

      return this._vsanPhoneHomeSystem;
   }

   public VsanClusterMgmtInternalSystem getVsanClusterMgmtInternalSystem() {
      if (this._vsanClusterMgmtInternalSystem == null) {
         this._vsanClusterMgmtInternalSystem = (VsanClusterMgmtInternalSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_CLUSTER_MGMT_INTERNAL_SYSTEM);
      }

      return this._vsanClusterMgmtInternalSystem;
   }

   public VsanFileServiceSystem getVsanFileServiceSystem() {
      if (this._vsanFileServiceSystem == null) {
         this._vsanFileServiceSystem = (VsanFileServiceSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_FILE_SERVICE_SYSTEM);
      }

      return this._vsanFileServiceSystem;
   }

   public VsanVumSystem getVsanVumSystem() {
      if (this._vsanVumSystem == null) {
         this._vsanVumSystem = (VsanVumSystem)this.createStub(VsanServiceImpl.VsanMO.VSAN_VUM_SYSTEM);
      }

      return this._vsanVumSystem;
   }

   private static enum VsanMO {
      VC_CLUSTER_HEALTH_SYSTEM("VsanVcClusterHealthSystem", "vsan-cluster-health-system"),
      UPGRADE_SYSTEM("VsanUpgradeSystem", "vsan-upgrade-system2"),
      LEGACY_UPGRADE_SYSTEM("VsanUpgradeSystem", "vsan-upgrade-system"),
      UPGRADE_SYSTEM_EX("VsanUpgradeSystemEx", "vsan-upgrade-systemex"),
      STRETCHED_CLUSTER("VimClusterVsanVcStretchedClusterSystem", "vsan-stretched-cluster-system"),
      CLUSTER_CONFIG_SYSTEM("VsanVcClusterConfigSystem", "vsan-cluster-config-system"),
      DISK_MANAGEMENT_SYSTEM("VimClusterVsanVcDiskManagementSystem", "vsan-disk-management-system"),
      PERFORMANCE_MANAGER("VsanPerformanceManager", "vsan-performance-manager"),
      CAPABILITY_SYSTEM("VsanCapabilitySystem", "vsan-vc-capability-system"),
      OBJECT_SYSTEM("VsanObjectSystem", "vsan-cluster-object-system"),
      ISCSI_TARGET_SYSTEM("VsanIscsiTargetSystem", "vsan-cluster-iscsi-target-system"),
      SPACE_REPORTING_SYSTEM("VsanSpaceReportSystem", "vsan-cluster-space-report-system"),
      SYSTEM_EX("VsanSystemEx", "vsanSystemEx"),
      VSAN_SYSTEM("HostVsanSystem", "vsanSystem"),
      VSAN_VDS_SYSTEM("VsanVdsSystem", "vsan-vds-system"),
      VSAN_VC_PRECHECKER_SYSTEM("VsanVcPrecheckerSystem", "ha-vsan-vc-prechecker-system"),
      VSAN_PHONEHOME_SYSTEM("VsanPhoneHomeSystem", "vsan-phonehome-system"),
      VSAN_UPDATE_MANAGER("VsanUpdateManager", "vsan-update-manager"),
      VSAN_CLUSTER_MGMT_INTERNAL_SYSTEM("VsanClusterMgmtInternalSystem", "vsan-cluster-mgmt-internal-system"),
      VSAN_FILE_SERVICE_SYSTEM("VsanFileServiceSystem", "vsan-cluster-file-service-system"),
      VSAN_VUM_SYSTEM("VsanVumSystem", "vsan-vum-system");

      private String type;
      private String id;

      private VsanMO(String type, String id) {
         this.type = type;
         this.id = id;
      }
   }
}
