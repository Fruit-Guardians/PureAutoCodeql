package com.vmware.vsphere.client.vsandp.helper;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.ComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.ResourcePool;
import com.vmware.vim.binding.vim.VirtualApp;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.cluster.ConfigInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectSystem;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPairingInfo;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.Conjoiner;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.Iterator;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanDpInventoryHelper {
   private static final Log logger = LogFactory.getLog(VsanDpInventoryHelper.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   PermissionService permissionService;
   @Autowired
   private VsanClient vsanClient;
   @Autowired
   protected LookupSvcClient lsClient;

   public ManagedObjectReference getVmFolderOfDataCenter(ManagedObjectReference target) {
      if (this.vmodlHelper.isOfType(target, Datacenter.class)) {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(target.getServerGuid());

            Throwable var10000;
            label189: {
               boolean var10001;
               ManagedObjectReference var18;
               try {
                  ManagedObjectReference dcRef = ((Datacenter)vcConnection.createStub(Datacenter.class, target)).getVmFolder();
                  var18 = VmodlHelper.assignServerGuid(dcRef, target.getServerGuid());
               } catch (Throwable var16) {
                  var10000 = var16;
                  var10001 = false;
                  break label189;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label176:
               try {
                  return var18;
               } catch (Throwable var15) {
                  var10000 = var15;
                  var10001 = false;
                  break label176;
               }
            }

            var2 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var2;
         } catch (Throwable var17) {
            if (var2 == null) {
               var2 = var17;
            } else if (var2 != var17) {
               var2.addSuppressed(var17);
            }

            throw var2;
         }
      } else {
         return target;
      }
   }

   public ManagedObjectReference getVmFolder(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label315: {
            ManagedObjectReference parentFolderRef;
            boolean var10001;
            ManagedObjectReference var28;
            label313: {
               try {
                  parentFolderRef = this.getParentFolder(vmRef, vcConnection);
                  ManagedObjectReference dcRef = ((Folder)vcConnection.createStub(Folder.class, parentFolderRef)).getParent();
                  VmodlHelper.assignServerGuid(dcRef, vmRef.getServerGuid());
                  if (!this.vmodlHelper.isOfType(dcRef, Datacenter.class)) {
                     break label313;
                  }

                  ManagedObjectReference vmFolderRef = ((Datacenter)vcConnection.createStub(Datacenter.class, dcRef)).getVmFolder();
                  VmodlHelper.assignServerGuid(vmFolderRef, vmRef.getServerGuid());
                  if (!vmFolderRef.equals(parentFolderRef)) {
                     break label313;
                  }

                  var28 = dcRef;
               } catch (Throwable var26) {
                  var10000 = var26;
                  var10001 = false;
                  break label315;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return var28;
            }

            try {
               var28 = parentFolderRef;
            } catch (Throwable var25) {
               var10000 = var25;
               var10001 = false;
               break label315;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label293:
            try {
               return var28;
            } catch (Throwable var24) {
               var10000 = var24;
               var10001 = false;
               break label293;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var27) {
         if (var2 == null) {
            var2 = var27;
         } else if (var2 != var27) {
            var2.addSuppressed(var27);
         }

         throw var2;
      }
   }

   public ManagedObjectReference getVmResourcePool(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         ManagedObjectReference var37;
         label452: {
            Throwable var10000;
            label455: {
               ManagedObjectReference resourcePool;
               boolean var10001;
               label454: {
                  try {
                     if (this.isDrsEnabledOnSourceCluster(vmRef)) {
                        break label454;
                     }

                     resourcePool = ((VirtualMachine)vcConnection.createStub(VirtualMachine.class, vmRef)).getRuntime().getHost();
                     var37 = VmodlHelper.assignServerGuid(resourcePool, vmRef.getServerGuid());
                  } catch (Throwable var35) {
                     var10000 = var35;
                     var10001 = false;
                     break label455;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return var37;
               }

               ManagedObjectReference parent;
               try {
                  resourcePool = this.getParentResourcePool(vmRef, vcConnection);
                  parent = ((ResourcePool)vcConnection.createStub(ResourcePool.class, resourcePool)).getParent();
                  if (this.vmodlHelper.isOfType(parent, ResourcePool.class)) {
                     var37 = VmodlHelper.assignServerGuid(resourcePool, vmRef.getServerGuid());
                     break label452;
                  }
               } catch (Throwable var34) {
                  var10000 = var34;
                  var10001 = false;
                  break label455;
               }

               try {
                  var37 = VmodlHelper.assignServerGuid(parent, vmRef.getServerGuid());
               } catch (Throwable var33) {
                  var10000 = var33;
                  var10001 = false;
                  break label455;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label427:
               try {
                  return var37;
               } catch (Throwable var32) {
                  var10000 = var32;
                  var10001 = false;
                  break label427;
               }
            }

            var2 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var2;
         }

         if (vcConnection != null) {
            vcConnection.close();
         }

         return var37;
      } catch (Throwable var36) {
         if (var2 == null) {
            var2 = var36;
         } else if (var2 != var36) {
            var2.addSuppressed(var36);
         }

         throw var2;
      }
   }

   private boolean isDrsEnabledOnSourceCluster(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label449: {
            ManagedObjectReference clusterRef;
            boolean var10001;
            label447: {
               try {
                  clusterRef = this.getVmCluster(vmRef);
                  if (clusterRef != null) {
                     break label447;
                  }
               } catch (Throwable var35) {
                  var10000 = var35;
                  var10001 = false;
                  break label449;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return false;
            }

            ConfigInfo info;
            label448: {
               try {
                  info = ((ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef)).getConfiguration();
                  if (info != null && info.drsConfig != null) {
                     break label448;
                  }
               } catch (Throwable var34) {
                  var10000 = var34;
                  var10001 = false;
                  break label449;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return false;
            }

            boolean var37;
            try {
               var37 = info.drsConfig.enabled;
            } catch (Throwable var33) {
               var10000 = var33;
               var10001 = false;
               break label449;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label421:
            try {
               return var37;
            } catch (Throwable var32) {
               var10000 = var32;
               var10001 = false;
               break label421;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var36) {
         if (var2 == null) {
            var2 = var36;
         } else if (var2 != var36) {
            var2.addSuppressed(var36);
         }

         throw var2;
      }
   }

   @TsService
   public ManagedObjectReference getVmCluster(ManagedObjectReference vmRef) {
      return BaseUtils.getCluster(vmRef);
   }

   public ManagedObjectReference getVmDc(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label205: {
            boolean var10001;
            ManagedObjectReference var19;
            try {
               ManagedObjectReference folderRef = this.getParentFolder(vmRef, vcConnection);
               ManagedObjectReference parentRef = ((Folder)vcConnection.createStub(Folder.class, folderRef)).getParent();

               while(true) {
                  if (this.vmodlHelper.isOfType(parentRef, Datacenter.class)) {
                     var19 = VmodlHelper.assignServerGuid(parentRef, vmRef.getServerGuid());
                     break;
                  }

                  parentRef = ((Folder)vcConnection.createStub(Folder.class, parentRef)).getParent();
               }
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label205;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label194:
            try {
               return var19;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label194;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }

   public ManagedObjectReference getVmNetwork(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label313: {
            boolean var10001;
            ManagedObjectReference var26;
            label314: {
               try {
                  ManagedObjectReference[] networks = ((VirtualMachine)vcConnection.createStub(VirtualMachine.class, vmRef)).getNetwork();
                  if (networks != null && networks.length > 0) {
                     var26 = VmodlHelper.assignServerGuid(networks[0], vmRef.getServerGuid());
                     break label314;
                  }
               } catch (Throwable var24) {
                  var10000 = var24;
                  var10001 = false;
                  break label313;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               try {
                  return null;
               } catch (Throwable var23) {
                  var10000 = var23;
                  var10001 = false;
                  break label313;
               }
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label292:
            try {
               return var26;
            } catch (Throwable var22) {
               var10000 = var22;
               var10001 = false;
               break label292;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var25) {
         if (var2 == null) {
            var2 = var25;
         } else if (var2 != var25) {
            var2.addSuppressed(var25);
         }

         throw var2;
      }
   }

   public ManagedObjectReference getVsanDatastore(ManagedObjectReference moRef) throws VsanUiLocalizableException {
      try {
         PropertyValue[] vmDatastores = QueryUtil.getPropertiesForRelatedObjects(moRef, "datastore", "datastore", new String[]{"summary.type"}).getPropertyValues();
         PropertyValue[] var6 = vmDatastores;
         int var5 = vmDatastores.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            PropertyValue datastore = var6[var4];
            if (datastore.propertyName.equals("summary.type") && datastore.value.equals("vsan")) {
               return (ManagedObjectReference)datastore.resourceObject;
            }
         }

         logger.warn("No vSAN datastore found for VM " + moRef);
         return null;
      } catch (Exception var7) {
         logger.error("Unable to retrieve vSAN Datastore.", var7);
         throw new VsanUiLocalizableException("dataproviders.spbm.datastore");
      }
   }

   public ManagedObjectReference getVsanDatastore(ManagedObjectReference moRef, String datastoreUrl) throws Exception {
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(moRef, "datastore", "datastore", new String[]{"summary.type", "summary.url"});
      Iterator var5 = response.getResourceObjects().iterator();

      Object dsRef;
      do {
         if (!var5.hasNext()) {
            logger.warn(String.format("No vSAN datastore with URL %s is found for moRef %s", datastoreUrl, moRef));
            return null;
         }

         dsRef = var5.next();
      } while(!"vsan".equals(response.getProperty(dsRef, "summary.type")) || !datastoreUrl.equals(response.getProperty(dsRef, "summary.url")));

      return (ManagedObjectReference)dsRef;
   }

   public boolean hasRemoteDpActionsPrivileges(ManagedObjectReference clusterRef) throws Exception {
      boolean hasVcPermissions = this.permissionService.hasVcPermissions(clusterRef, new String[]{"StorageProfile.View", "System.Read"});
      boolean hasDatastorePermissions = this.permissionService.hasPermissions(this.getVsanDatastore(clusterRef), new String[]{"Vsan.DataProtection.Management.Remote"});
      return hasVcPermissions && hasDatastorePermissions;
   }

   public boolean isVmRestoreAllowed(ManagedObjectReference vmRef) throws Exception {
      ManagedObjectReference clusterRef = this.getVmCluster(vmRef);
      boolean hasVcPermissions = this.permissionService.hasVcPermissions(clusterRef, new String[]{"StorageProfile.View", "System.Read"});
      ManagedObjectReference vmDatastore = this.getVsanDatastore(vmRef);
      if (vmDatastore == null) {
         return false;
      } else {
         boolean hasDatastorePermissions = this.permissionService.hasPermissions(vmDatastore, new String[]{"Vsan.DataProtection.Management"});
         return hasVcPermissions && hasDatastorePermissions;
      }
   }

   public ManagedObjectReference getPeerCluster(ManagedObjectReference param1, DataProtectionPairingInfo param2, PscConnectionDetails param3) throws VsanUiLocalizableException {
      // $FF: Couldn't be decompiled
   }

   public String findServerGiud(String nodeId, PscConnectionDetails pscDetails, String localServerGiud) {
      if (nodeId == null) {
         return localServerGiud;
      } else {
         Throwable var4 = null;
         Object var5 = null;

         try {
            LookupSvcConnection lsConn = this.lsClient.getConnection(LookupSvcInfo.from(pscDetails));

            String var10000;
            try {
               Iterator var8 = (new VcLsExplorer(lsConn.getServiceRegistration())).list().iterator();

               VcRegistration vcRegistration;
               do {
                  if (!var8.hasNext()) {
                     return null;
                  }

                  vcRegistration = (VcRegistration)var8.next();
               } while(!nodeId.equals(vcRegistration.getNodeId()));

               var10000 = vcRegistration.getUuid().toString();
            } finally {
               if (lsConn != null) {
                  lsConn.close();
               }

            }

            return var10000;
         } catch (Throwable var14) {
            if (var4 == null) {
               var4 = var14;
            } else if (var4 != var14) {
               var4.addSuppressed(var14);
            }

            throw var4;
         }
      }
   }

   public boolean isVmNameUniqueForFolder(ManagedObjectReference vmLocationRef, String vmName) throws Exception {
      ManagedObjectReference folderRef = this.getVmFolderOfDataCenter(vmLocationRef);
      ObjectIdentityConstraint folderIdentityConstrait = QueryUtil.createObjectIdentityConstraint(folderRef);
      RelationalConstraint vmsConstraint = QueryUtil.createRelationalConstraint("childEntity", folderIdentityConstrait, true, VirtualMachine.class.getSimpleName());
      RelationalConstraint vAppsConstraint = QueryUtil.createRelationalConstraint("childEntity", folderIdentityConstrait, true, VirtualApp.class.getSimpleName());
      Constraint objectsCompositeConstraint = QueryUtil.combineIntoSingleConstraint(new Constraint[]{vmsConstraint, vAppsConstraint}, Conjoiner.OR);
      PropertyConstraint vmPropertyConstraint = QueryUtil.createPropertyConstraint(VirtualMachine.class.getSimpleName(), "name", Comparator.EQUALS, vmName);
      PropertyConstraint vAppPropertyConstraint = QueryUtil.createPropertyConstraint(VirtualApp.class.getSimpleName(), "name", Comparator.EQUALS, vmName);
      Constraint nameCompositeConstraint = QueryUtil.combineIntoSingleConstraint(new Constraint[]{vmPropertyConstraint, vAppPropertyConstraint}, Conjoiner.OR);
      Constraint compositeConstraint = QueryUtil.combineIntoSingleConstraint(new Constraint[]{objectsCompositeConstraint, nameCompositeConstraint}, Conjoiner.AND);
      String[] properties = new String[]{"name"};
      QuerySpec query = QueryUtil.buildQuerySpec(compositeConstraint, properties);
      return QueryUtil.getData(query).totalMatchedObjectCount == 0;
   }

   public ManagedObjectReference getResourcePoolForCompute(ManagedObjectReference objectRef) {
      if (this.vmodlHelper.isOfType(objectRef, ResourcePool.class)) {
         return objectRef;
      } else if (!this.vmodlHelper.isOfType(objectRef, HostSystem.class) && !this.vmodlHelper.isOfType(objectRef, ClusterComputeResource.class)) {
         throw new IllegalArgumentException("Expected compute type is HostSystem, ClusterComputeResource or ResourcePool, but found " + objectRef.getType());
      } else {
         ManagedObjectReference computeResourceRef = this.vmodlHelper.isOfType(objectRef, HostSystem.class) ? this.getComputeResourceOfHost(objectRef) : objectRef;
         Throwable var3 = null;
         Object var4 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(objectRef.getServerGuid());

            Throwable var10000;
            label238: {
               boolean var10001;
               ManagedObjectReference var19;
               try {
                  ComputeResource computeResource = (ComputeResource)vcConnection.createStub(ComputeResource.class, computeResourceRef);
                  var19 = VmodlHelper.assignServerGuid(computeResource.getResourcePool(), objectRef.getServerGuid());
               } catch (Throwable var17) {
                  var10000 = var17;
                  var10001 = false;
                  break label238;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label222:
               try {
                  return var19;
               } catch (Throwable var16) {
                  var10000 = var16;
                  var10001 = false;
                  break label222;
               }
            }

            var3 = var10000;
            if (vcConnection != null) {
               vcConnection.close();
            }

            throw var3;
         } catch (Throwable var18) {
            if (var3 == null) {
               var3 = var18;
            } else if (var3 != var18) {
               var3.addSuppressed(var18);
            }

            throw var3;
         }
      }
   }

   public String getVmStoragePolicyId(ManagedObjectReference vmRef) throws Exception {
      String vmNamespaceId = (String)QueryUtil.getProperty(vmRef, "config.vmStorageObjectId", (Object)null);
      ManagedObjectReference clusterRef = this.getVmCluster(vmRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanConnection vsanConnection = this.vsanClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label306: {
            VsanObjectIdentityAndHealth objectIdentityAndHealth;
            boolean var10001;
            label305: {
               try {
                  String[] uuids = new String[]{vmNamespaceId};
                  VsanObjectSystem vsanObjectSystem = vsanConnection.getVsanObjectSystem();
                  objectIdentityAndHealth = vsanObjectSystem.queryObjectIdentities(clusterRef, uuids, (String[])null, false, true, false, (Boolean)null);
                  if (objectIdentityAndHealth != null && !ArrayUtils.isEmpty(objectIdentityAndHealth.identities)) {
                     break label305;
                  }
               } catch (Throwable var29) {
                  var10000 = var29;
                  var10001 = false;
                  break label306;
               }

               if (vsanConnection != null) {
                  vsanConnection.close();
               }

               return null;
            }

            String var31;
            try {
               VsanObjectIdentity[] identities = objectIdentityAndHealth.getIdentities();
               var31 = identities[0].spbmProfileUuid;
            } catch (Throwable var28) {
               var10000 = var28;
               var10001 = false;
               break label306;
            }

            if (vsanConnection != null) {
               vsanConnection.close();
            }

            label285:
            try {
               return var31;
            } catch (Throwable var27) {
               var10000 = var27;
               var10001 = false;
               break label285;
            }
         }

         var4 = var10000;
         if (vsanConnection != null) {
            vsanConnection.close();
         }

         throw var4;
      } catch (Throwable var30) {
         if (var4 == null) {
            var4 = var30;
         } else if (var4 != var30) {
            var4.addSuppressed(var30);
         }

         throw var4;
      }
   }

   private ManagedObjectReference getParentFolder(ManagedObjectReference vmVAppRef, VcConnection vcConnection) {
      ManagedObjectReference result = null;
      if (this.vmodlHelper.isOfType(vmVAppRef, VirtualMachine.class)) {
         VirtualMachine vm = (VirtualMachine)vcConnection.createStub(VirtualMachine.class, vmVAppRef);
         result = vm.getParent() != null ? vm.getParent() : vm.getParentVApp();
      } else if (this.vmodlHelper.isOfType(vmVAppRef, VirtualApp.class)) {
         VirtualApp vApp = (VirtualApp)vcConnection.createStub(VirtualApp.class, vmVAppRef);
         result = vApp.getParentFolder() != null ? vApp.getParentFolder() : vApp.getParentVApp();
      }

      if (result == null) {
         logger.error("[getParentFolder] Unable to retrieve parent of " + vmVAppRef);
      }

      VmodlHelper.assignServerGuid(result, vmVAppRef.getServerGuid());
      return this.vmodlHelper.isOfType(result, Folder.class) ? result : this.getParentFolder(result, vcConnection);
   }

   private ManagedObjectReference getParentResourcePool(ManagedObjectReference ref, VcConnection vcConnection) {
      Class<?> moClass = this.vmodlHelper.getTypeClass(ref);
      if (ResourcePool.class.equals(moClass)) {
         return ref;
      } else {
         ManagedObjectReference rpRef = null;
         if (this.vmodlHelper.isOfType(ref, VirtualMachine.class)) {
            VirtualMachine vm = (VirtualMachine)vcConnection.createStub(VirtualMachine.class, ref);
            rpRef = vm.getResourcePool();
            if (rpRef == null) {
               rpRef = vm.getParentVApp();
            }
         } else if (VirtualApp.class.equals(moClass)) {
            VirtualApp vApp = (VirtualApp)vcConnection.createStub(VirtualApp.class, ref);
            rpRef = vApp.getParent();
         } else {
            logger.error("[getParentResourcePool] Unhandled case for " + ref);
         }

         VmodlHelper.assignServerGuid(rpRef, ref.getServerGuid());
         return this.getParentResourcePool(rpRef, vcConnection);
      }
   }

   private ManagedObjectReference getComputeResourceOfHost(ManagedObjectReference hostRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var18;
            try {
               HostSystem host = (HostSystem)vcConnection.createStub(HostSystem.class, hostRef);
               var18 = VmodlHelper.assignServerGuid(host.getParent(), hostRef.getServerGuid());
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var18;
            } catch (Throwable var15) {
               var10000 = var15;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var17) {
         if (var2 == null) {
            var2 = var17;
         } else if (var2 != var17) {
            var2.addSuppressed(var17);
         }

         throw var2;
      }
   }
}
