package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VmInventoryService extends InventoryBrowserService {
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VmodlHelper vmodlHelper;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) throws Exception {
      return super.getNodeChildren(parentRef, pscDetails, contextRef);
   }

   protected List<ManagedObjectReference> listChildrenRefs(ManagedObjectReference parent, PscConnectionDetails pscDetails, ManagedObjectReference context) {
      PscConnectionDetails remotePscDetails = pscDetails == null ? new PscConnectionDetails() : pscDetails;
      Throwable var5 = null;
      Object var6 = null;

      try {
         VcConnection vcConnection;
         Throwable var10000;
         label273: {
            boolean var10001;
            List var21;
            label280: {
               vcConnection = this.vcClient.getConnection(parent.getServerGuid(), remotePscDetails.toLsInfo());

               try {
                  if (Datacenter.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parent))) {
                     parent = VmodlHelper.assignServerGuid(((Datacenter)vcConnection.createStub(Datacenter.class, parent)).getVmFolder(), parent.getServerGuid());
                  }

                  if (Folder.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parent))) {
                     ManagedObjectReference[] children = ((Folder)vcConnection.createStub(Folder.class, parent)).getChildEntity();
                     var21 = this.filterChildren(children);
                     break label280;
                  }
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label273;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return Collections.emptyList();
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label249:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label249;
            }
         }

         var5 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var5;
      } catch (Throwable var20) {
         if (var5 == null) {
            var5 = var20;
         } else if (var5 != var20) {
            var5.addSuppressed(var20);
         }

         throw var5;
      }
   }

   private List<ManagedObjectReference> filterChildren(ManagedObjectReference[] allChildren) {
      List<ManagedObjectReference> result = new ArrayList();
      ManagedObjectReference[] var6 = allChildren;
      int var5 = allChildren.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference childRef = var6[var4];
         if (this.vmodlHelper.isVmFolder(childRef) || this.vmodlHelper.isOfType(childRef, VirtualMachine.class)) {
            result.add(childRef);
         }
      }

      return result;
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return this.vmodlHelper.isOfType(item, VirtualMachine.class);
   }
}
