package com.vmware.vsphere.client.vsandp.controllers.vm.action.promote;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.VirtualMachine.PowerState;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.util.VcPropertiesFacade;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PromoteActionController {
   private static final String MANUAL_SNAPSHOT_ISSUE = "manualSnapshotExists";
   private static final String POWER_OFF_ISSUE = "vmIsPoweredOff";
   private static final String VM_SNAPSHOT = "snapshot";
   private static final String VM_POWER_STATE = "runtime.powerState";
   private static final String VM_DEVICES = "config.hardware.device";
   @Autowired
   private VcPropertiesFacade vcPropertiesFacade;
   @Autowired
   private VcClient vcClient;

   @TsService
   public Map<String, Collection<ManagedObjectReference>> validate(ManagedObjectReference[] vmRefs) throws Exception {
      PropertyValue[] values = QueryUtil.getProperties(vmRefs, new String[]{"snapshot", "runtime.powerState"}).getPropertyValues();
      ListMultimap<String, ManagedObjectReference> result = ArrayListMultimap.create();
      PropertyValue[] var7 = values;
      int var6 = values.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue item = var7[var5];
         String var8;
         switch((var8 = item.propertyName).hashCode()) {
         case 284874180:
            if (var8.equals("snapshot") && item.value != null) {
               result.put("manualSnapshotExists", (ManagedObjectReference)item.resourceObject);
            }
            break;
         case 541171298:
            if (var8.equals("runtime.powerState") && item.value.equals(PowerState.poweredOff)) {
               result.put("vmIsPoweredOff", (ManagedObjectReference)item.resourceObject);
            }
         }
      }

      return result.asMap();
   }

   @TsService
   public void promote(ManagedObjectReference[] vmRefs) throws Exception {
      PropertyValue[] vmDeviceProperties = QueryUtil.getProperties(vmRefs, new String[]{"config.hardware.device"}).getPropertyValues();
      PropertyValue[] var6 = vmDeviceProperties;
      int var5 = vmDeviceProperties.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue property = var6[var4];
         ArrayList<VirtualDisk> disks = new ArrayList();
         VirtualDevice[] var11;
         int var10 = (var11 = (VirtualDevice[])property.value).length;

         for(int var9 = 0; var9 < var10; ++var9) {
            VirtualDevice device = var11[var9];
            if (device instanceof VirtualDisk) {
               VirtualDisk disk = (VirtualDisk)device;
               if (this.vcPropertiesFacade.isNativeUnmanagedLinkedClone(disk)) {
                  disks.add(disk);
               }
            }
         }

         ManagedObjectReference vmRef = (ManagedObjectReference)property.resourceObject;
         Throwable var20 = null;
         Object var21 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

            try {
               VirtualMachine vm = (VirtualMachine)vcConnection.createStub(VirtualMachine.class, vmRef);
               vm.promoteDisks(true, (VirtualDisk[])disks.toArray(new VirtualDisk[disks.size()]));
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }
         } catch (Throwable var18) {
            if (var20 == null) {
               var20 = var18;
            } else if (var20 != var18) {
               var20.addSuppressed(var18);
            }

            throw var20;
         }
      }

   }
}
