package com.vmware.vsphere.client.vsan.base.util;

import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.VsanUpgradeSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
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
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.service.VsanService;
import com.vmware.vsphere.client.vsan.base.service.VsanServiceFactory;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanProviderUtils {
   public static final String HOST_VSAN_INTERNAL_SYSTEM = "configManager.vsanInternalSystem";
   public static final String HOST_VSAN_SYSTEM = "configManager.vsanSystem";
   public static final String HOST_CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   private static final Log _logger = LogFactory.getLog(VsanProviderUtils.class);
   private static VsanServiceFactory _vsanServiceFactory;
   private static VmodlHelper _vmodlHelper;

   public static void setVsanServiceFactory(VsanServiceFactory factory) {
      _vsanServiceFactory = factory;
   }

   public static void setVmodlHelper(VmodlHelper vmodlHelper) {
      _vmodlHelper = vmodlHelper;
   }

   public static VsanSystem getHostVsanSystem(ManagedObjectReference hostRef, VcConnection vcConnection) throws Exception {
      ManagedObjectReference vsanSystemRef = (ManagedObjectReference)QueryUtil.getProperty(hostRef, "configManager.vsanSystem", (Object)null);
      if (vsanSystemRef == null) {
         _logger.error("getHostVsanSystem: failed to retrieve host's vsan system.");
         return null;
      } else {
         return (VsanSystem)vcConnection.createStub(VsanSystem.class, vsanSystemRef);
      }
   }

   public static VsanInternalSystem getVsanInternalSystem(ManagedObjectReference hostOrClusterRef, VcConnection vcConnection) throws Exception {
      ManagedObjectReference vsanInternalSystemRef = null;
      PropertyValue[] values;
      if (_vmodlHelper.isOfType(hostOrClusterRef, HostSystem.class)) {
         values = QueryUtil.getProperties(hostOrClusterRef, new String[]{"configManager.vsanInternalSystem", "runtime.connectionState"}).getPropertyValues();
      } else {
         values = QueryUtil.getPropertiesForRelatedObjects(hostOrClusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"configManager.vsanInternalSystem", "runtime.connectionState"}).getPropertyValues();
      }

      Set<ManagedObjectReference> hostRefs = filterConnectedHosts(values);
      PropertyValue[] var8 = values;
      int var7 = values.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         PropertyValue val = var8[var6];
         if (hostRefs.contains(val.resourceObject) && val.propertyName == "configManager.vsanInternalSystem") {
            vsanInternalSystemRef = (ManagedObjectReference)val.value;
            if (vsanInternalSystemRef != null) {
               break;
            }
         }
      }

      if (vsanInternalSystemRef == null) {
         _logger.error("getVsanInternalSystem: failed to retrieve VsanInternalSystem for host or cluster: " + hostOrClusterRef);
         return null;
      } else {
         return (VsanInternalSystem)vcConnection.createStub(VsanInternalSystem.class, vsanInternalSystemRef);
      }
   }

   private static Set<ManagedObjectReference> filterConnectedHosts(PropertyValue[] values) {
      Set<ManagedObjectReference> result = new HashSet();
      PropertyValue[] var5 = values;
      int var4 = values.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         PropertyValue val = var5[var3];
         if (val.propertyName == "runtime.connectionState" && ConnectionState.connected.equals(val.value)) {
            result.add((ManagedObjectReference)val.resourceObject);
         }
      }

      return result;
   }

   public static VsanVcStretchedClusterSystem getVcStretchedClusterSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanStretchedClusterSystem();
   }

   public static VsanVcClusterConfigSystem getVsanConfigSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanConfigSystem();
   }

   public static VsanVcDiskManagementSystem getVcDiskManagementSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanDiskManagementSystem();
   }

   public static VsanPerformanceManager getVsanPerformanceManager(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanPerformanceManager();
   }

   public static VsanUpgradeSystem getVsanUpgradeSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanUpgradeSystem();
   }

   public static VsanVcClusterHealthSystem getVsanVcClusterHealthSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanVcClusterHealthSystem();
   }

   public static VsanFileServiceSystem getVsanVcFileServiceSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanFileServiceSystem();
   }

   public static VsanUpgradeSystemEx getVsanUpgradeSystemEx(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanUpgradeSystemEx();
   }

   public static VsanObjectSystem getVsanObjectSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanObjectSystem();
   }

   public static VsanIscsiTargetSystem getVsanIscsiSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanIscsiSystem();
   }

   public static VsanSpaceReportSystem getVsanSpaceReportSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanSpaceReportSystem();
   }

   public static VsanCapabilitySystem getVsanCapabilitySystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanCapabilitySystem();
   }

   public static VsanSystemEx getVsanSystemEx(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanSystemEx(moRef);
   }

   public static VsanSystem getVsanSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanSystem(moRef);
   }

   public static VsanUpgradeSystem getVsanLegacyUpgradeSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService == null ? null : vsanService.getVsanLegacyUpgradeSystem();
   }

   public static VsanUpdateManager getUpdateManager(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      VsanUpdateManager updateManager = vsanService.getUpdateManager();
      return updateManager;
   }

   public static VsanVdsSystem getVdsSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      VsanVdsSystem vdsSystem = vsanService.getVdsSystem();
      return vdsSystem;
   }

   public static VsanVcPrecheckerSystem getVsanPrecheckerSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService != null ? vsanService.getVsanPreCheckerSystem() : null;
   }

   public static VsanPhoneHomeSystem getVsanPhoneHomeSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService != null ? vsanService.getPhoneHomeSystem() : null;
   }

   public static VsanClusterMgmtInternalSystem getVsanClusterMgmtInternalSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService != null ? vsanService.getVsanClusterMgmtInternalSystem() : null;
   }

   public static VsanVumSystem getVsanVumSystem(ManagedObjectReference moRef) {
      VsanService vsanService = _vsanServiceFactory.getService(moRef.getServerGuid());
      return vsanService != null ? vsanService.getVsanVumSystem() : null;
   }
}
