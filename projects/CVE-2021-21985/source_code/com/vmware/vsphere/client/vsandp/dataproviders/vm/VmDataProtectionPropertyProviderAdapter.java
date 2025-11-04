package com.vmware.vsphere.client.vsandp.dataproviders.vm;

import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.InventoryService.CgMemberQuery;
import com.vmware.vise.data.PropertySpec;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.VcPropertiesFacade;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmDataProtectionPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final Logger logger = LoggerFactory.getLogger(VmDataProtectionPropertyProviderAdapter.class);
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private VcPropertiesFacade vcPropertiesFacade;
   private static final String VM_IS_LINKED_CLONE = "isVmLinkedClone";
   private static final String VM_DATA_PROTECTION_STATE = "isVmDataProtected";
   private static final String VM_RESTORE_ALLOWED = "isVmRestoreAllowed";

   @Autowired
   public void setDataServiceExtensionRegistry(DataServiceExtensionRegistry registry) {
      TypeInfo ti = new TypeInfo();
      ti.type = VirtualMachine.class.getSimpleName();
      ti.properties = new String[]{"isVmDataProtected", "isVmLinkedClone", "isVmRestoreAllowed"};
      registry.registerDataAdapter(this, new TypeInfo[]{ti});
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      ResultSet result = new ResultSet();
      result.items = new ResultItem[0];
      ManagedObjectReference[] vmRefs = (ManagedObjectReference[])Arrays.copyOf(propertyRequest.objects, propertyRequest.objects.length, ManagedObjectReference[].class);

      try {
         PropertyValue[] vmProperties = QueryUtil.getProperties(vmRefs, new String[]{"config.hardware.device", "cluster"}).getPropertyValues();
         PropertySpec[] var8;
         int var7 = (var8 = propertyRequest.properties).length;

         for(int var6 = 0; var6 < var7; ++var6) {
            PropertySpec propertySpec = var8[var6];
            if (ArrayUtils.contains(propertySpec.propertyNames, "isVmDataProtected")) {
               result.items = (ResultItem[])ArrayUtils.addAll(result.items, this.isVmDataProtected(vmRefs, vmProperties));
            }

            ArrayList<ResultItem> actionsResult = new ArrayList();
            ManagedObjectReference vmRef;
            int var11;
            int var12;
            ManagedObjectReference[] var13;
            if (ArrayUtils.contains(propertySpec.propertyNames, "isVmLinkedClone") && ArrayUtils.contains(propertySpec.propertyNames, "isVmRestoreAllowed")) {
               var13 = vmRefs;
               var12 = vmRefs.length;

               for(var11 = 0; var11 < var12; ++var11) {
                  vmRef = var13[var11];
                  boolean isLinkedClone = this.isVmLinkedClone(vmRef, vmProperties);
                  actionsResult.add(QueryUtil.createResultItem("isVmLinkedClone", isLinkedClone, vmRef));
                  if (isLinkedClone) {
                     actionsResult.add(QueryUtil.createResultItem("isVmRestoreAllowed", false, vmRef));
                  } else {
                     boolean restoreSupported = this.isVmRestoreAllowed(vmRef, vmProperties);
                     actionsResult.add(QueryUtil.createResultItem("isVmRestoreAllowed", restoreSupported, vmRef));
                  }
               }

               result.items = (ResultItem[])ArrayUtils.addAll(result.items, actionsResult.toArray(new ResultItem[vmRefs.length]));
            } else if (ArrayUtils.contains(propertySpec.propertyNames, "isVmLinkedClone")) {
               var13 = vmRefs;
               var12 = vmRefs.length;

               for(var11 = 0; var11 < var12; ++var11) {
                  vmRef = var13[var11];
                  actionsResult.add(QueryUtil.createResultItem("isVmLinkedClone", this.isVmLinkedClone(vmRef, vmProperties), vmRef));
               }
            } else if (ArrayUtils.contains(propertySpec.propertyNames, "isVmRestoreAllowed")) {
               var13 = vmRefs;
               var12 = vmRefs.length;

               for(var11 = 0; var11 < var12; ++var11) {
                  vmRef = var13[var11];
                  actionsResult.add(QueryUtil.createResultItem("isVmRestoreAllowed", this.isVmRestoreAllowed(vmRef, vmProperties), vmRef));
               }
            }
         }
      } catch (Exception var16) {
         result.error = var16;
         logger.error("Unable to return VM data protection related properties!", var16);
      }

      return result;
   }

   private ResultItem[] isVmDataProtected(ManagedObjectReference[] vmRefs, PropertyValue[] vmProperties) {
      ArrayList<ResultItem> result = new ArrayList();
      ManagedObjectReference[] var7 = vmRefs;
      int var6 = vmRefs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference vmRef = var7[var5];
         ManagedObjectReference cluster = (ManagedObjectReference)this.getVmProperty(vmRef, vmProperties, "cluster");
         if (cluster != null && VsanCapabilityUtils.isLocalDataProtectionSupported(cluster) && this.isDataProtectionActive(vmRef, cluster)) {
            result.add(QueryUtil.createResultItem("isVmDataProtected", true, vmRef));
         } else {
            result.add(QueryUtil.createResultItem("isVmDataProtected", false, vmRef));
         }
      }

      return (ResultItem[])result.toArray(new ResultItem[vmRefs.length]);
   }

   private boolean isDataProtectionActive(ManagedObjectReference vmRef, ManagedObjectReference clusterRef) {
      try {
         CgMemberQuery cgBasicInfoResult = this.cgProvider.queryCgByObject(vmRef, clusterRef);
         return cgBasicInfoResult.getError() == null && cgBasicInfoResult.getResult() != null;
      } catch (Exception var4) {
         logger.error("Unable to query VM data protection CG info!", var4);
         return false;
      }
   }

   private boolean isVmRestoreAllowed(ManagedObjectReference vmRef, PropertyValue[] vmProperties) throws Exception {
      ManagedObjectReference cluster = (ManagedObjectReference)this.getVmProperty(vmRef, vmProperties, "cluster");
      boolean hasPermissions = this.inventoryHelper.isVmRestoreAllowed(vmRef);
      return hasPermissions && cluster != null && VsanCapabilityUtils.isLocalDataProtectionSupported(cluster) && this.isDataProtectionActive(vmRef, cluster) && !this.isUnmanagedDiskPresent(vmRef, vmProperties);
   }

   private boolean isVmLinkedClone(ManagedObjectReference vmRef, PropertyValue[] vmProperties) throws Exception {
      ManagedObjectReference cluster = (ManagedObjectReference)this.getVmProperty(vmRef, vmProperties, "cluster");
      return cluster != null && VsanCapabilityUtils.isLocalDataProtectionSupported(cluster) && this.isUnmanagedDiskPresent(vmRef, vmProperties);
   }

   private boolean isUnmanagedDiskPresent(ManagedObjectReference vm, PropertyValue[] vmProperties) throws Exception {
      VirtualDevice[] devices = (VirtualDevice[])this.getVmProperty(vm, vmProperties, "config.hardware.device");
      VirtualDevice[] var7 = devices;
      int var6 = devices.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         VirtualDevice device = var7[var5];
         if (device instanceof VirtualDisk) {
            VirtualDisk disk = (VirtualDisk)device;
            if (this.vcPropertiesFacade.isNativeUnmanagedLinkedClone(disk)) {
               return true;
            }
         }
      }

      return false;
   }

   private <T> T getVmProperty(ManagedObjectReference vm, PropertyValue[] vmProperties, String propertyName) {
      PropertyValue[] var7 = vmProperties;
      int var6 = vmProperties.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue property = var7[var5];
         if (property.propertyName.equals(propertyName) && property.resourceObject.equals(vm)) {
            return property.value;
         }
      }

      return null;
   }
}
