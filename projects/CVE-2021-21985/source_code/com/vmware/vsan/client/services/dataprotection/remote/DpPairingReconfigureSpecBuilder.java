package com.vmware.vsan.client.services.dataprotection.remote;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vim.cluster.ConfigInfoEx;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionLoadBalancersInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPairingInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPeerSiteInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DpPairingReconfigureSpecBuilder {
   private static final Log logger = LogFactory.getLog(DpPairingReconfigureSpecBuilder.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VsanClient vsanClient;
   @Autowired
   private LookupSvcClient lsClient;
   @Autowired
   private RemoteDpConfigService dpConfigService;
   private ManagedObjectReference sourceClusterRef;
   private ManagedObjectReference targetClusterRef;
   private PscConnectionDetails pscDetails;
   private DataProtectionInfo sourceConfigInfo;
   private DataProtectionInfo targetConfigInfo;
   private DataProtectionLoadBalancersInfo sourceLbInfo;
   private DataProtectionLoadBalancersInfo targetLbInfo;
   private Datastore sourceDatastore;
   private Datastore targetDatastore;
   private boolean isSameSso;
   private boolean isSameVc;

   public void init(ManagedObjectReference sourceClusterRef, ManagedObjectReference targetClusterRef, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      this.sourceClusterRef = sourceClusterRef;
      this.targetClusterRef = targetClusterRef;
      this.pscDetails = pscDetails;
      this.isSameVc = sourceClusterRef.getServerGuid().equals(targetClusterRef.getServerGuid());
      this.isSameSso = pscDetails == null;
      this.sourceConfigInfo = this.dpConfigService.getDpConfig(sourceClusterRef, (PscConnectionDetails)null);
      this.sourceLbInfo = this.dpConfigService.getLoadBalancersInfo(this.sourceClusterRef, (PscConnectionDetails)null);
      this.sourceDatastore = this.getClusterDatastore(sourceClusterRef, (PscConnectionDetails)null);
      this.targetConfigInfo = this.dpConfigService.getDpConfig(targetClusterRef, pscDetails);
      this.targetLbInfo = this.dpConfigService.getLoadBalancersInfo(this.targetClusterRef, pscDetails);
      this.targetDatastore = this.getClusterDatastore(targetClusterRef, pscDetails);
   }

   public ReconfigSpec buildSourceReconfigureSpec() throws VsanUiLocalizableException {
      try {
         return this.buildReconfigureSpec(this.sourceClusterRef, (PscConnectionDetails)null, this.pscDetails, this.targetClusterRef, this.getCluster(this.targetClusterRef, this.pscDetails), this.sourceDatastore, this.targetDatastore, this.sourceConfigInfo, this.sourceLbInfo, this.targetLbInfo);
      } catch (MalformedURLException var2) {
         logger.error("Error while building reconfig spec for cluster " + this.sourceClusterRef, var2);
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.cluster.build.spec.error");
      }
   }

   public ReconfigSpec buildTargetReconfigureSpec() throws VsanUiLocalizableException {
      try {
         return this.buildReconfigureSpec(this.targetClusterRef, this.pscDetails, (PscConnectionDetails)null, this.sourceClusterRef, this.getCluster(this.sourceClusterRef, (PscConnectionDetails)null), this.targetDatastore, this.sourceDatastore, this.targetConfigInfo, this.targetLbInfo, this.sourceLbInfo);
      } catch (MalformedURLException var2) {
         logger.error("Error while building reconfig spec for target cluster " + this.targetClusterRef, var2);
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.peer.cluster.build.spec.error");
      }
   }

   private ReconfigSpec buildReconfigureSpec(ManagedObjectReference clusterRef, PscConnectionDetails lsConnectionDetails, PscConnectionDetails peerLsConnectionDetails, ManagedObjectReference peerClusterRef, ClusterComputeResource peerCluster, Datastore datastore, Datastore peerDatastore, DataProtectionInfo configInfo, DataProtectionLoadBalancersInfo lbInfo, DataProtectionLoadBalancersInfo peerLbInfo) throws MalformedURLException {
      DataProtectionPairingInfo pairingInfo = new DataProtectionPairingInfo();
      pairingInfo.setLocalLoadBalancers(lbInfo.getBasicLoadBalancerInfo());
      pairingInfo.setLocalDatastoreUrl(datastore.getInfo().getUrl());
      pairingInfo.setPeerSite(this.buildPeerSiteInfo(peerClusterRef, peerLsConnectionDetails));
      pairingInfo.setPeerLoadBalancers(peerLbInfo.detailedLoadBalancerInfo);
      pairingInfo.setPeerDatastoreName(peerDatastore.getName());
      pairingInfo.setPeerDatastoreUrl(peerDatastore.getInfo().getUrl());
      pairingInfo.setPeerClusterName(peerCluster.getName());
      Throwable var12 = null;
      Object var13 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(peerClusterRef.getServerGuid(), LookupSvcInfo.from(peerLsConnectionDetails));

         try {
            ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, peerClusterRef);
            ConfigInfoEx configInfoEx = (ConfigInfoEx)cluster.getConfigurationEx();
            if (configInfoEx != null && configInfoEx.vsanConfigInfo != null && configInfoEx.vsanConfigInfo.defaultConfig != null) {
               pairingInfo.setPeerClusterUuid(configInfoEx.vsanConfigInfo.defaultConfig.uuid);
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }
      } catch (Throwable var22) {
         if (var12 == null) {
            var12 = var22;
         } else if (var12 != var22) {
            var12.addSuppressed(var22);
         }

         throw var12;
      }

      ReconfigSpec reconfigureSpec = new ReconfigSpec();
      reconfigureSpec.setDataProtectionConfig(configInfo);
      reconfigureSpec.getDataProtectionConfig().setPairingInfo(new DataProtectionPairingInfo[]{pairingInfo});
      return reconfigureSpec;
   }

   private Datastore getClusterDatastore(ManagedObjectReference clusterRef, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid(), LookupSvcInfo.from(pscDetails));

         try {
            ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
            ManagedObjectReference[] var10;
            int var9 = (var10 = cluster.getDatastore()).length;

            for(int var8 = 0; var8 < var9; ++var8) {
               ManagedObjectReference datastoreRef = var10[var8];
               Datastore datastore = (Datastore)vcConnection.createStub(Datastore.class, datastoreRef);
               if (datastore.getSummary().getType().equalsIgnoreCase("vsan")) {
                  Datastore var10000 = datastore;
                  return var10000;
               }
            }

            logger.error("Unable to find vSAN datastore for cluster: " + clusterRef);
            throw new VsanUiLocalizableException("dataproviders.spbm.datastore");
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }
      } catch (Throwable var17) {
         if (var3 == null) {
            var3 = var17;
         } else if (var3 != var17) {
            var3.addSuppressed(var17);
         }

         throw var3;
      }
   }

   private ClusterComputeResource getCluster(ManagedObjectReference clusterRef, PscConnectionDetails pscDetails) {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid(), LookupSvcInfo.from(pscDetails));

         Throwable var10000;
         label173: {
            boolean var10001;
            ClusterComputeResource var18;
            try {
               var18 = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
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

         var3 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var3;
      } catch (Throwable var17) {
         if (var3 == null) {
            var3 = var17;
         } else if (var3 != var17) {
            var3.addSuppressed(var17);
         }

         throw var3;
      }
   }

   private DataProtectionPeerSiteInfo buildPeerSiteInfo(ManagedObjectReference peerClusterRef, PscConnectionDetails peerLsConnectionDetails) throws MalformedURLException {
      DataProtectionPeerSiteInfo peerSiteInfo = new DataProtectionPeerSiteInfo();
      Throwable var4 = null;
      Object var5 = null;

      try {
         LookupSvcConnection lsConn = this.lsClient.getConnection(LookupSvcInfo.from(peerLsConnectionDetails));

         Throwable var10000;
         label483: {
            VcRegistration vcReg;
            DataProtectionPeerSiteInfo var40;
            boolean var10001;
            label481: {
               try {
                  ServiceRegistration svcReg = lsConn.getServiceRegistration();
                  vcReg = (VcRegistration)(new VcLsExplorer(svcReg)).get(UUID.fromString(peerClusterRef.getServerGuid()));
                  peerSiteInfo.setName(vcReg.getVcName());
                  if (!this.isSameVc) {
                     break label481;
                  }

                  var40 = peerSiteInfo;
               } catch (Throwable var38) {
                  var10000 = var38;
                  var10001 = false;
                  break label483;
               }

               if (lsConn != null) {
                  lsConn.close();
               }

               return var40;
            }

            label482: {
               try {
                  peerSiteInfo.setNodeId(vcReg.getNodeId());
                  peerSiteInfo.setSiteId(vcReg.getSiteId());
                  if (!this.isSameSso) {
                     break label482;
                  }

                  var40 = peerSiteInfo;
               } catch (Throwable var37) {
                  var10000 = var37;
                  var10001 = false;
                  break label483;
               }

               if (lsConn != null) {
                  lsConn.close();
               }

               return var40;
            }

            try {
               peerSiteInfo.setLookupServiceUrl(vcReg.getServiceUrl().toURL().toString());
               String thumbprint = peerLsConnectionDetails == null ? this.lsClient.getLocalLsInfo().getThumbprint() : peerLsConnectionDetails.pscThumbprint;
               peerSiteInfo.setLookupServiceThumbprint(thumbprint);
               var40 = peerSiteInfo;
            } catch (Throwable var36) {
               var10000 = var36;
               var10001 = false;
               break label483;
            }

            if (lsConn != null) {
               lsConn.close();
            }

            label456:
            try {
               return var40;
            } catch (Throwable var35) {
               var10000 = var35;
               var10001 = false;
               break label456;
            }
         }

         var4 = var10000;
         if (lsConn != null) {
            lsConn.close();
         }

         throw var4;
      } catch (Throwable var39) {
         if (var4 == null) {
            var4 = var39;
         } else if (var4 != var39) {
            var4.addSuppressed(var39);
         }

         throw var4;
      }
   }
}
