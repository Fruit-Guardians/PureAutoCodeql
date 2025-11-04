package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WitnessCandidateInventoryService extends InventoryBrowserService {
   @Autowired
   private ComputeInventoryService computeInventoryService;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) throws Exception {
      return super.getNodeChildren(parentRef, pscDetails, contextRef);
   }

   protected List<ManagedObjectReference> listChildrenRefs(ManagedObjectReference parent, PscConnectionDetails pscDetails, ManagedObjectReference context) {
      return this.computeInventoryService.listChildrenRefs(parent, pscDetails, context);
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return this.computeInventoryService.isLeafNode(item);
   }

   protected List<InventoryEntryData> createRemoteNodeModel(List<ManagedObjectReference> nodeRefs, PscConnectionDetails pscDetails) {
      throw new NotImplementedException("Withness candidate inventory does not support remote VC!");
   }

   protected Set<String> getDataServiceProperties() {
      Set<String> result = super.getDataServiceProperties();
      result.add("config.vsanHostConfig.enabled");
      result.add("isWitnessHost");
      return result;
   }

   protected InventoryEntryData createDSModel(ResultItem item) {
      PropertyValue[] var5;
      int var4 = (var5 = item.properties).length;

      for(int var3 = 0; var3 < var4; ++var3) {
         PropertyValue prop = var5[var3];
         if ((prop.propertyName.equals("config.vsanHostConfig.enabled") || prop.propertyName.equals("isWitnessHost")) && (Boolean)prop.value) {
            return null;
         }
      }

      return this.computeInventoryService.createDSModel(item);
   }
}
