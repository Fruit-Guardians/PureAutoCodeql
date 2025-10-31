package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.Network;
import com.vmware.vim.binding.vim.Tag;
import com.vmware.vim.binding.vim.dvs.DistributedVirtualPortgroup;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NetworkInventoryService extends InventoryBrowserService {
   private static final String UPLINK_KEY = "SYSTEM/DVS.UPLINKPG";
   private static final String DVS_PROPERTY = "config.distributedVirtualSwitch";
   private static final Log logger = LogFactory.getLog(NetworkInventoryService.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VmodlHelper vmodlHelper;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   protected Set<String> getDataServiceProperties() {
      Set<String> result = super.getDataServiceProperties();
      result.add("config.distributedVirtualSwitch");
      return result;
   }

   protected InventoryEntryData createDSModel(ResultItem item) {
      InventoryEntryData model = super.createDSModel(item);
      PropertyValue[] var6;
      int var5 = (var6 = item.properties).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue prop = var6[var4];
         if (prop.propertyName.equals("config.distributedVirtualSwitch") && prop.value != null && prop.value instanceof ManagedObjectReference) {
            try {
               String dvsName = (String)QueryUtil.getProperty((ManagedObjectReference)prop.value, "name");
               model.name = model.name + " (" + dvsName + ")";
            } catch (Exception var8) {
               logger.error("Unable to get Distributed port group's DVS name!", var8);
            }
         }
      }

      return model;
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
         VcConnection vcConnection = this.vcClient.getConnection(parent.getServerGuid(), remotePscDetails.toLsInfo());

         label576: {
            List var39;
            label577: {
               Throwable var10000;
               label578: {
                  label580: {
                     boolean var10001;
                     try {
                        if (ClusterComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parent))) {
                           ManagedObjectReference[] networks = ((ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, parent)).getNetwork();
                           VmodlHelper.assignServerGuid(networks, parent.getServerGuid());
                           var39 = this.filterChildren(networks, remotePscDetails);
                           break label580;
                        }
                     } catch (Throwable var37) {
                        var10000 = var37;
                        var10001 = false;
                        break label578;
                     }

                     try {
                        if (Datacenter.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parent))) {
                           parent = VmodlHelper.assignServerGuid(((Datacenter)vcConnection.createStub(Datacenter.class, parent)).getNetworkFolder(), parent.getServerGuid());
                           var39 = this.filterChildren(VmodlHelper.assignServerGuid(((Folder)vcConnection.createStub(Folder.class, parent)).getChildEntity(), parent.getServerGuid()), remotePscDetails);
                           break label577;
                        }
                     } catch (Throwable var36) {
                        var10000 = var36;
                        var10001 = false;
                        break label578;
                     }

                     try {
                        if (!Folder.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parent))) {
                           break label576;
                        }

                        var39 = this.filterChildren(VmodlHelper.assignServerGuid(((Folder)vcConnection.createStub(Folder.class, parent)).getChildEntity(), parent.getServerGuid()), remotePscDetails);
                     } catch (Throwable var35) {
                        var10000 = var35;
                        var10001 = false;
                        break label578;
                     }

                     if (vcConnection != null) {
                        vcConnection.close();
                     }

                     try {
                        return var39;
                     } catch (Throwable var34) {
                        var10000 = var34;
                        var10001 = false;
                        break label578;
                     }
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return var39;
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

            return var39;
         }

         if (vcConnection != null) {
            vcConnection.close();
         }
      } catch (Throwable var38) {
         if (var5 == null) {
            var5 = var38;
         } else if (var5 != var38) {
            var5.addSuppressed(var38);
         }

         throw var5;
      }

      return Collections.emptyList();
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return this.vmodlHelper.isOfType(item, Network.class);
   }

   private List<ManagedObjectReference> filterChildren(ManagedObjectReference[] allChildren, PscConnectionDetails pscDetails) {
      List<ManagedObjectReference> result = new ArrayList();
      ManagedObjectReference[] var7 = allChildren;
      int var6 = allChildren.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference childRef = var7[var5];
         if (DistributedVirtualPortgroup.class.isAssignableFrom(this.vmodlHelper.getTypeClass(childRef))) {
            Throwable var8 = null;
            Object var9 = null;

            try {
               VcConnection vcConnection = this.vcClient.getConnection(childRef.getServerGuid(), pscDetails.toLsInfo());

               try {
                  Tag[] tags = ((DistributedVirtualPortgroup)vcConnection.createStub(DistributedVirtualPortgroup.class, childRef)).getTag();
                  if (!searchTagsForKey(tags, "SYSTEM/DVS.UPLINKPG")) {
                     result.add(childRef);
                  }
               } finally {
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

               }
            } catch (Throwable var17) {
               if (var8 == null) {
                  var8 = var17;
               } else if (var8 != var17) {
                  var8.addSuppressed(var17);
               }

               throw var8;
            }
         } else if (this.vmodlHelper.isNetworkFolder(childRef) || Network.class.isAssignableFrom(this.vmodlHelper.getTypeClass(childRef))) {
            result.add(childRef);
         }
      }

      return result;
   }

   private static boolean searchTagsForKey(Tag[] tags, String key) {
      if (!ArrayUtils.isEmpty(tags) && !StringUtils.isEmpty(key)) {
         Tag[] var5 = tags;
         int var4 = tags.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            Tag tag = var5[var3];
            if (key.equals(tag.key)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
