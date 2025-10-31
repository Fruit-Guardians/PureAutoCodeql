package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

import com.vmware.vim.binding.vim.VsanUpgradeSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vmodl.ManagedObject;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
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
import com.vmware.vim.vsan.binding.vim.cns.VolumeManager;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vim.vsan.binding.vim.host.VsanUpdateManager;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceSystem;
import com.vmware.vim.vsan.binding.vim.vsan.VsanResourceCheckSystem;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVdsSystem;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;

public class VsanConnection extends VlsiConnection {
   private static final String VC_SESSION_COOKIE = "Cookie";

   public VsanVcClusterConfigSystem getVsanConfigSystem() {
      return (VsanVcClusterConfigSystem)this.createStub(VsanVcClusterConfigSystem.class, VsanManagedObject.CLUSTER_CONFIG_SYSTEM);
   }

   public VsanVcDiskManagementSystem getVsanDiskManagementSystem() {
      return (VsanVcDiskManagementSystem)this.createStub(VsanVcDiskManagementSystem.class, VsanManagedObject.DISK_MANAGEMENT_SYSTEM);
   }

   public VsanPerformanceManager getVsanPerformanceManager() {
      return (VsanPerformanceManager)this.createStub(VsanPerformanceManager.class, VsanManagedObject.PERFORMANCE_MANAGER);
   }

   public VsanUpgradeSystem getVsanUpgradeSystem() {
      return (VsanUpgradeSystem)this.createStub(VsanUpgradeSystem.class, VsanManagedObject.UPGRADE_SYSTEM);
   }

   public VsanUpgradeSystem getVsanLegacyUpgradeSystem() {
      return (VsanUpgradeSystem)this.createStub(VsanUpgradeSystem.class, VsanManagedObject.LEGACY_UPGRADE_SYSTEM);
   }

   public VsanUpgradeSystemEx getVsanUpgradeSystemEx() {
      return (VsanUpgradeSystemEx)this.createStub(VsanUpgradeSystemEx.class, VsanManagedObject.UPGRADE_SYSTEM_EX);
   }

   public VsanVcClusterHealthSystem getVsanVcClusterHealthSystem() {
      return (VsanVcClusterHealthSystem)this.createStub(VsanVcClusterHealthSystem.class, VsanManagedObject.VC_CLUSTER_HEALTH_SYSTEM);
   }

   public VsanObjectSystem getVsanObjectSystem() {
      return (VsanObjectSystem)this.createStub(VsanObjectSystem.class, VsanManagedObject.OBJECT_SYSTEM);
   }

   public VsanIscsiTargetSystem getVsanIscsiSystem() {
      return (VsanIscsiTargetSystem)this.createStub(VsanIscsiTargetSystem.class, VsanManagedObject.ISCSI_TARGET_SYSTEM);
   }

   public VsanSpaceReportSystem getVsanSpaceReportSystem() {
      return (VsanSpaceReportSystem)this.createStub(VsanSpaceReportSystem.class, VsanManagedObject.SPACE_REPORTING_SYSTEM);
   }

   public VsanCapabilitySystem getVsanCapabilitySystem() {
      return (VsanCapabilitySystem)this.createStub(VsanCapabilitySystem.class, VsanManagedObject.CAPABILITY_SYSTEM);
   }

   public VsanSystemEx getVsanSystemEx(ManagedObjectReference moRef) {
      ManagedObjectReference vsanSystemExRef = new ManagedObjectReference("VsanSystemEx", moRef.getValue().replace("host", "vsanSystemEx"), moRef.getServerGuid());
      return (VsanSystemEx)this.createStub(VsanSystemEx.class, vsanSystemExRef);
   }

   public VsanSystem getVsanSystem(ManagedObjectReference moRef) {
      return (VsanSystem)this.createStub(VsanSystem.class, VsanManagedObject.VSAN_SYSTEM, moRef);
   }

   public VsanUpdateManager getUpdateManager() {
      return (VsanUpdateManager)this.createStub(VsanUpdateManager.class, VsanManagedObject.VSAN_UPDATE_MANAGER);
   }

   public VsanVdsSystem getVdsSystem() {
      return (VsanVdsSystem)this.createStub(VsanVdsSystem.class, VsanManagedObject.VSAN_VDS_SYSTEM);
   }

   public VsanVcPrecheckerSystem getVsanPreCheckerSystem() {
      return (VsanVcPrecheckerSystem)this.createStub(VsanVcPrecheckerSystem.class, VsanManagedObject.VSAN_VC_PRECHECKER_SYSTEM);
   }

   public VsanPhoneHomeSystem getPhoneHomeSystem() {
      return (VsanPhoneHomeSystem)this.createStub(VsanPhoneHomeSystem.class, VsanManagedObject.VSAN_PHONEHOME_SYSTEM);
   }

   public VsanClusterMgmtInternalSystem getVsanClusterMgmtInternalSystem() {
      return (VsanClusterMgmtInternalSystem)this.createStub(VsanClusterMgmtInternalSystem.class, VsanManagedObject.VSAN_CLUSTER_MGMT_INTERNAL_SYSTEM);
   }

   public VsanVumSystem getVsanVumSystem() {
      return (VsanVumSystem)this.createStub(VsanVumSystem.class, VsanManagedObject.VSAN_VUM_SYSTEM);
   }

   public VolumeManager getCnsVolumeManager() {
      return (VolumeManager)this.createStub(VolumeManager.class, VsanManagedObject.CNS_VOLUME_MANAGER);
   }

   public VsanFileServiceSystem getVsanFileServiceSystem() {
      return (VsanFileServiceSystem)this.createStub(VsanFileServiceSystem.class, VsanManagedObject.VSAN_FILE_SERVICE_SYSTEM);
   }

   public VsanResourceCheckSystem getVsanResourceCheckSystem() {
      return (VsanResourceCheckSystem)this.createStub(VsanResourceCheckSystem.class, VsanManagedObject.VSAN_CLUSTER_RESOURCE_CHECK_SYSTEM);
   }

   public VsanVcStretchedClusterSystem getVcStretchedClusterSystem() {
      return (VsanVcStretchedClusterSystem)this.createStub(VsanVcStretchedClusterSystem.class, VsanManagedObject.STRETCHED_CLUSTER);
   }

   private <T extends ManagedObject> T createStub(Class<T> clazz, VsanManagedObject vsanMo) {
      return this.createStub(clazz, new ManagedObjectReference(vsanMo.type, vsanMo.id, (String)null));
   }

   private <T extends ManagedObject> T createStub(Class<T> clazz, VsanManagedObject vsanMo, ManagedObjectReference moRef) {
      String serverGuild = moRef != null ? moRef.getServerGuid() : null;
      return this.createStub(clazz, new ManagedObjectReference(vsanMo.type, vsanMo.id, serverGuild));
   }

   public <T extends ManagedObject> T createStub(Class<T> clazz, String moId) {
      return this.withCookie(super.createStub(clazz, moId));
   }

   public <T extends ManagedObject> T createStub(Class<T> clazz, ManagedObjectReference moRef) {
      return this.withCookie(super.createStub(clazz, moRef));
   }

   private <T extends ManagedObject> T withCookie(T obj) {
      RequestContext requestContext = new RequestContextImpl();
      requestContext.put("Cookie", this.settings.getSessionCookie());
      ((Stub)obj)._setRequestContext(requestContext);
      return obj;
   }

   public String toString() {
      return this.settings != null ? String.format("VsanConnection(host=%s)", this.settings.getHttpSettings().getHost()) : "VsanConnection(initializing)";
   }
}
