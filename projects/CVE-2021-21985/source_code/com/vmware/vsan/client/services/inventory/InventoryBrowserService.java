package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ManagedEntity;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class InventoryBrowserService {
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private LookupSvcClient lsClient;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      if (ArrayUtils.isEmpty(nodeRefs)) {
         return new InventoryEntryData[0];
      } else {
         List<InventoryEntryData> result = null;
         if (pscDetails == null) {
            result = this.createLocalNodeModel(Arrays.asList(nodeRefs));
         } else if (this.vmodlHelper.isVcRootFolder(nodeRefs[0])) {
            result = this.createRemoteVcModel(Arrays.asList(nodeRefs), pscDetails);
         } else {
            result = this.createRemoteNodeModel(Arrays.asList(nodeRefs), pscDetails);
         }

         return (InventoryEntryData[])result.toArray(new InventoryEntryData[result.size()]);
      }
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentNode, PscConnectionDetails pscDetails, ManagedObjectReference contextNode) throws Exception {
      List<ManagedObjectReference> childrenRefs = this.listChildrenRefs(parentNode, pscDetails, contextNode);
      return CollectionUtils.isEmpty(childrenRefs) ? new InventoryEntryData[0] : this.getNodeInfo((ManagedObjectReference[])childrenRefs.toArray(new ManagedObjectReference[0]), pscDetails);
   }

   protected List<InventoryEntryData> createLocalNodeModel(List<ManagedObjectReference> nodeRefs) throws Exception {
      Set<String> dsProps = this.getDataServiceProperties();
      QuerySpec querySpec = QueryUtil.buildQuerySpec((ManagedObjectReference[])nodeRefs.toArray(new ManagedObjectReference[0]), (String[])dsProps.toArray(new String[0]));
      ResultSet response = QueryUtil.getData(querySpec);
      List<InventoryEntryData> result = new ArrayList();
      ResultItem[] var9;
      int var8 = (var9 = response.items).length;

      for(int var7 = 0; var7 < var8; ++var7) {
         ResultItem item = var9[var7];
         InventoryEntryData model = this.createDSModel(item);
         if (model != null) {
            result.add(model);
         }
      }

      return result;
   }

   protected InventoryEntryData createDSModel(ResultItem item) {
      InventoryEntryData model = new InventoryEntryData();
      model.nodeRef = (ManagedObjectReference)item.resourceObject;
      model.connected = true;
      PropertyValue[] var6;
      int var5 = (var6 = item.properties).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue prop = var6[var4];
         if (prop.propertyName.equals("name")) {
            model.name = "" + prop.value;
         } else if (prop.propertyName.equals("primaryIconId")) {
            model.iconShape = "" + prop.value;
         }
      }

      model.isLeafNode = this.isLeafNode(model.nodeRef);
      return model;
   }

   protected List<InventoryEntryData> createRemoteNodeModel(List<ManagedObjectReference> nodeRefs, PscConnectionDetails pscDetails) {
      List<InventoryEntryData> result = new ArrayList();
      Iterator var5 = nodeRefs.iterator();

      while(var5.hasNext()) {
         ManagedObjectReference nodeRef = (ManagedObjectReference)var5.next();
         Throwable var6 = null;
         Object var7 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(nodeRef.getServerGuid(), pscDetails.toLsInfo());

            try {
               ManagedEntity managedObject = (ManagedEntity)vcConnection.createStub(ManagedEntity.class, nodeRef);
               InventoryEntryData model = new InventoryEntryData();
               model.nodeRef = nodeRef;
               model.name = managedObject.getName();
               model.isLeafNode = this.isLeafNode(nodeRef);
               model.iconShape = this.getDefaultIcon(nodeRef);
               result.add(model);
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }
         } catch (Throwable var16) {
            if (var6 == null) {
               var6 = var16;
            } else if (var6 != var16) {
               var6.addSuppressed(var16);
            }

            throw var6;
         }
      }

      return result;
   }

   private List<InventoryEntryData> createRemoteVcModel(List<ManagedObjectReference> nodeRefs, PscConnectionDetails pscDetails) {
      List<InventoryEntryData> result = new ArrayList();
      Throwable var4 = null;
      Object var5 = null;

      try {
         LookupSvcConnection lsConn = this.lsClient.getConnection(pscDetails.toLsInfo());

         try {
            Map<UUID, VcRegistration> vcRegistrations = (new VcLsExplorer(lsConn.getServiceRegistration())).map();
            Iterator var9 = nodeRefs.iterator();

            while(var9.hasNext()) {
               ManagedObjectReference nodeRef = (ManagedObjectReference)var9.next();
               InventoryEntryData model = new InventoryEntryData();
               VcRegistration vcRegistration = (VcRegistration)vcRegistrations.get(UUID.fromString(nodeRef.getServerGuid()));
               model.name = vcRegistration.getVcName();
               model.nodeRef = nodeRef;
               model.isLeafNode = this.isLeafNode(nodeRef);
               model.iconShape = this.getDefaultIcon(nodeRef);
               result.add(model);
            }
         } finally {
            if (lsConn != null) {
               lsConn.close();
            }

         }

         return result;
      } catch (Throwable var17) {
         if (var4 == null) {
            var4 = var17;
         } else if (var4 != var17) {
            var4.addSuppressed(var17);
         }

         throw var4;
      }
   }

   protected abstract List<ManagedObjectReference> listChildrenRefs(ManagedObjectReference var1, PscConnectionDetails var2, ManagedObjectReference var3);

   protected Set<String> getDataServiceProperties() {
      Set<String> result = new HashSet();
      result.add("name");
      result.add("primaryIconId");
      return result;
   }

   protected abstract boolean isLeafNode(ManagedObjectReference var1);

   private String getDefaultIcon(ManagedObjectReference objRef) {
      if (this.vmodlHelper.isVcRootFolder(objRef)) {
         return "vsphere-icon-vcenter";
      } else {
         String var2;
         switch((var2 = objRef.getType()).hashCode()) {
         case -1450678229:
            if (var2.equals("ClusterComputeResource")) {
               return "vsphere-icon-cluster";
            }
            break;
         case -786828786:
            if (var2.equals("Network")) {
               return "vsphere-icon-network";
            }
            break;
         case -740059428:
            if (var2.equals("VirtualMachine")) {
               return "vsphere-icon-vm";
            }
            break;
         case -580407137:
            if (var2.equals("Datacenter")) {
               return "vsphere-icon-datacenter";
            }
            break;
         case 894390807:
            if (var2.equals("HostSystem")) {
               return "vsphere-icon-host";
            }
            break;
         case 1659069271:
            if (var2.equals("Datastore")) {
               return "vsphere-icon-datastore";
            }
            break;
         case 2109868174:
            if (var2.equals("Folder")) {
               return "vsphere-icon-folder";
            }
         }

         return "info";
      }
   }
}
