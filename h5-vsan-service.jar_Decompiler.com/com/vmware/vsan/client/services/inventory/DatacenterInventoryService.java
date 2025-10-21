package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatacenterInventoryService extends InventoryBrowserService {
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
      List<ManagedObjectReference> result = new ArrayList();
      PscConnectionDetails remotePscDetails = pscDetails == null ? new PscConnectionDetails() : pscDetails;
      Throwable var6 = null;
      Object var7 = null;

      try {
         VcConnection vcConn = this.vcClient.getConnection(parent.getServerGuid(), remotePscDetails.toLsInfo());

         try {
            ManagedObjectReference[] var12;
            int var11 = (var12 = ((Folder)vcConn.createStub(Folder.class, parent)).getChildEntity()).length;

            for(int var10 = 0; var10 < var11; ++var10) {
               ManagedObjectReference childRef = var12[var10];
               VmodlHelper.assignServerGuid(childRef, parent.getServerGuid());
               if (this.vmodlHelper.isOfType(childRef, Datacenter.class)) {
                  result.add(childRef);
               } else if (this.vmodlHelper.isDatacenterFolder(childRef)) {
                  result.addAll(this.listChildrenRefs(childRef, remotePscDetails, context));
               }
            }
         } finally {
            if (vcConn != null) {
               vcConn.close();
            }

         }

         return result;
      } catch (Throwable var18) {
         if (var6 == null) {
            var6 = var18;
         } else if (var6 != var18) {
            var6.addSuppressed(var18);
         }

         throw var6;
      }
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return false;
   }
}
