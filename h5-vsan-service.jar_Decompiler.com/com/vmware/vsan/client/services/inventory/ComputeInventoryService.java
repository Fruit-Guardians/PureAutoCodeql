package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.ComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.ResourcePool;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.cluster.DrsConfigInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ComputeInventoryService extends InventoryBrowserService {
   @Autowired
   private VcClient vcClient;
   @Autowired
   protected VmodlHelper vmodlHelper;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) throws Exception {
      return super.getNodeChildren(parentRef, pscDetails, contextRef);
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return this.vmodlHelper.getTypeClass(item) == HostSystem.class;
   }

   protected List<InventoryEntryData> createRemoteNodeModel(List<ManagedObjectReference> nodeRefs, PscConnectionDetails pscDetails) {
      throw new NotImplementedException("Compute inventory does not support remote VC!");
   }

   protected Set<String> getDataServiceProperties() {
      Set<String> result = super.getDataServiceProperties();
      result.add("configuration.drsConfig");
      result.add("runtime.connectionState");
      result.add("runtime.inMaintenanceMode");
      result.add("runtime.inQuarantineMode");
      return result;
   }

   protected InventoryEntryData createDSModel(ResultItem item) {
      InventoryEntryData model = super.createDSModel(item);
      PropertyValue[] var6;
      int var5 = (var6 = item.properties).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue prop = var6[var4];
         boolean isInQuarantine;
         if (prop.propertyName.equals("runtime.connectionState")) {
            isInQuarantine = prop.value.toString().equals(ConnectionState.connected.toString());
            if (!isInQuarantine) {
               model.connected = false;
            }
         } else if (prop.propertyName.equals("runtime.inMaintenanceMode")) {
            isInQuarantine = (Boolean)prop.value;
            if (isInQuarantine) {
               model.connected = false;
            }
         } else if (prop.propertyName.equals("runtime.inQuarantineMode")) {
            isInQuarantine = (Boolean)prop.value;
            if (isInQuarantine) {
               model.connected = false;
            }
         } else if (prop.propertyName.equals("configuration.drsConfig")) {
            DrsConfigInfo drsConfig = (DrsConfigInfo)prop.value;
            model.isDrsEnabled = drsConfig != null ? drsConfig.enabled : false;
         }
      }

      return model;
   }

   protected List<ManagedObjectReference> listChildrenRefs(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference context) {
      PscConnectionDetails remotePscDetails = pscDetails == null ? new PscConnectionDetails() : pscDetails;
      Throwable var5 = null;
      Object var6 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(parentRef.getServerGuid(), remotePscDetails.toLsInfo());

         label783: {
            Throwable var10000;
            label784: {
               List var57;
               label785: {
                  boolean var10001;
                  try {
                     if (Datacenter.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parentRef))) {
                        ManagedObjectReference hostFolderRef = ((Datacenter)vcConnection.createStub(Datacenter.class, parentRef)).getHostFolder();
                        Folder hostFolder = (Folder)vcConnection.createStub(Folder.class, hostFolderRef);
                        var57 = this.filterChildren(VmodlHelper.assignServerGuid(hostFolder.getChildEntity(), parentRef.getServerGuid()), remotePscDetails);
                        break label785;
                     }
                  } catch (Throwable var50) {
                     var10000 = var50;
                     var10001 = false;
                     break label784;
                  }

                  label786: {
                     try {
                        if (ClusterComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parentRef))) {
                           ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, parentRef);
                           ManagedObjectReference[] resourcePools = ((ResourcePool)vcConnection.createStub(ResourcePool.class, cluster.getResourcePool())).getResourcePool();
                           var57 = this.filterChildren(VmodlHelper.assignServerGuid((ManagedObjectReference[])ArrayUtils.addAll(resourcePools, cluster.getHost()), parentRef.getServerGuid()), remotePscDetails);
                           break label786;
                        }
                     } catch (Throwable var49) {
                        var10000 = var49;
                        var10001 = false;
                        break label784;
                     }

                     label788: {
                        try {
                           if (ComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parentRef))) {
                              ComputeResource host = (ComputeResource)vcConnection.createStub(ComputeResource.class, parentRef);
                              ResourcePool pool = (ResourcePool)vcConnection.createStub(ResourcePool.class, host.getResourcePool());
                              var57 = this.filterChildren(VmodlHelper.assignServerGuid(pool.getResourcePool(), parentRef.getServerGuid()), remotePscDetails);
                              break label788;
                           }
                        } catch (Throwable var48) {
                           var10000 = var48;
                           var10001 = false;
                           break label784;
                        }

                        try {
                           if (!Folder.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parentRef))) {
                              break label783;
                           }

                           Folder folder = (Folder)vcConnection.createStub(Folder.class, parentRef);
                           var57 = this.filterChildren(VmodlHelper.assignServerGuid(folder.getChildEntity(), parentRef.getServerGuid()), remotePscDetails);
                        } catch (Throwable var47) {
                           var10000 = var47;
                           var10001 = false;
                           break label784;
                        }

                        if (vcConnection != null) {
                           vcConnection.close();
                        }

                        try {
                           return var57;
                        } catch (Throwable var46) {
                           var10000 = var46;
                           var10001 = false;
                           break label784;
                        }
                     }

                     if (vcConnection != null) {
                        vcConnection.close();
                     }

                     return var57;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return var57;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return var57;
            }

            var5 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var5;
         }

         if (vcConnection != null) {
            vcConnection.close();
         }
      } catch (Throwable var51) {
         if (var5 == null) {
            var5 = var51;
         } else if (var5 != var51) {
            var5.addSuppressed(var51);
         }

         throw var5;
      }

      return new ArrayList();
   }

   protected boolean isTypeSupported(ManagedObjectReference ref) {
      return this.vmodlHelper.isOfType(ref, HostSystem.class) || this.vmodlHelper.isOfType(ref, ClusterComputeResource.class) || this.vmodlHelper.isOfType(ref, ResourcePool.class) || this.vmodlHelper.isHostFolder(ref);
   }

   private List<ManagedObjectReference> filterChildren(ManagedObjectReference[] allChildren, PscConnectionDetails pscDetails) {
      List<ManagedObjectReference> result = new ArrayList();
      ManagedObjectReference[] var7 = allChildren;
      int var6 = allChildren.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference childRef = var7[var5];
         if (this.isTypeSupported(childRef)) {
            result.add(childRef);
         } else if (this.vmodlHelper.isOfType(childRef, ComputeResource.class)) {
            Throwable var8 = null;
            Object var9 = null;

            try {
               VcConnection vcConnection = this.vcClient.getConnection(childRef.getServerGuid(), pscDetails.toLsInfo());

               try {
                  ComputeResource computeResource = (ComputeResource)vcConnection.createStub(ComputeResource.class, childRef);
                  ManagedObjectReference[] var15;
                  int var14 = (var15 = computeResource.getHost()).length;

                  for(int var13 = 0; var13 < var14; ++var13) {
                     ManagedObjectReference hostRef = var15[var13];
                     result.add(VmodlHelper.assignServerGuid(hostRef, childRef.getServerGuid()));
                  }
               } finally {
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

               }
            } catch (Throwable var21) {
               if (var8 == null) {
                  var8 = var21;
               } else if (var8 != var21) {
                  var8.addSuppressed(var21);
               }

               throw var8;
            }
         }
      }

      return result;
   }
}
