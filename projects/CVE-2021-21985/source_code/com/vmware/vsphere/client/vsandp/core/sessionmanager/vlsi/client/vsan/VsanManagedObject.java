package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

public enum VsanManagedObject {
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
   VSAN_VUM_SYSTEM("VsanVumSystem", "vsan-vum-system"),
   CNS_VOLUME_MANAGER("CnsVolumeManager", "cns-volume-manager"),
   VSAN_CLUSTER_RESOURCE_CHECK_SYSTEM("VsanResourceCheckSystem", "vsan-cluster-resource-check-system");

   public final String type;
   public final String id;

   private VsanManagedObject(String type, String id) {
      this.type = type;
      this.id = id;
   }
}
