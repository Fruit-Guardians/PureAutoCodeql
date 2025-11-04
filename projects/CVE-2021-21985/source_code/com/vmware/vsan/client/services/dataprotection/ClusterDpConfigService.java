package com.vmware.vsan.client.services.dataprotection;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Datastore.HostMount;
import com.vmware.vim.binding.vim.Datastore.Summary;
import com.vmware.vim.binding.vim.host.MountInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterMgmtInternalSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionArchivalLocation;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionLoadBalancersInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPairingInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPeerSiteInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.dataprotection.model.ClusterDpConfigData;
import com.vmware.vsan.client.services.dataprotection.model.DatastoreData;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.services.dataprotection.remote.RemoteAuthenticationService;
import com.vmware.vsan.client.services.dataprotection.remote.RemoteDpConfigService;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.FormatUtil;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClusterDpConfigService {
   public static final String DATASTORE_SUMMARY_PROPERTY = "summary";
   public static final String DATASTORE_SPECIFIC_TYPE_PROPERTY = "specificType";
   public static final String DATASTORE_HOST_PROPERTY = "host";
   public static final String CLUSTER_HOST_PROPRERTY = "host";
   public static final String DATASTORE_READ_WRITE_MOUNT_MODE = "readWrite";
   public static final List<DatastoreData.Type> ARCHIVE_DP_DATASTORE_TYPES = new ArrayList() {
      {
         this.add(DatastoreData.Type.NFS_3);
      }
   };
   private static final String DATASTORE_PRIMARY_ICON_PROPERTY = "primaryIconId";
   private static final String DATASTORE_URL_PROPERTY = "summary.url";
   private static final String DATASTORE_NAME_PROPERTY = "name";
   private static final String CLUSTER_TYPE = "ClusterComputeResource";
   private static final String DATASTORE_RELATION = "datastore";
   private static final VsanProfiler _profiler = new VsanProfiler(ClusterDpConfigService.class);
   private static final Log logger = LogFactory.getLog(ClusterDpConfigService.class);
   private static final VsanProfiler profiler = new VsanProfiler(ClusterDpConfigService.class);
   private int PORT_NOT_SET = -1;
   @Autowired
   private LookupSvcClient lsClient;
   @Autowired
   private RemoteAuthenticationService authenticationService;
   @Autowired
   private RemoteDpConfigService dpConfigService;
   @Autowired
   private VsanDpInventoryHelper dpInventoryHelper;
   @Autowired
   private VmodlHelper vmodlHelper;

   @TsService
   public ClusterDpConfigData getClusterDpConfig(ManagedObjectReference clusterRef) throws Exception {
      ClusterDpConfigData dpConfigData = new ClusterDpConfigData();
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx vsanConfig = vsanConfigSystem.getConfigInfoEx(clusterRef);
      if (vsanConfig != null && vsanConfig.dataProtectionConfig != null) {
         dpConfigData.consumptionLimit = vsanConfig.dataProtectionConfig.usageThreshold;
         dpConfigData.remoteReplicationPort = vsanConfig.dataProtectionConfig.incomingReplicationPort;
         dpConfigData.isArchiveDpSupported = VsanCapabilityUtils.isArchiveDataProtectionSupported(clusterRef);
         if (dpConfigData.isArchiveDpSupported) {
            this.populateArchiveConfigData(dpConfigData, clusterRef, vsanConfig);
         }

         dpConfigData.isRemoteDpSupported = VsanCapabilityUtils.isRemoteDataProtectionSupported(clusterRef);
         if (dpConfigData.isRemoteDpSupported) {
            this.populateRemoteConfigData(dpConfigData, clusterRef, vsanConfig);
         }

         return dpConfigData;
      } else {
         logger.error("Vsan Data protection config is missing!");
         return null;
      }
   }

   @TsService
   public DatastoreData[] getArchiveSuitableDpDatastores(ManagedObjectReference clusterRef) throws Exception {
      return this.getArchiveDpDatastores(clusterRef, false);
   }

   public DatastoreData[] getArchiveDpDatastores(ManagedObjectReference clusterRef, boolean restoreOnly) throws Exception {
      PropertyValue[] foundProperties = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "datastore", "ClusterComputeResource", new String[]{"primaryIconId", "summary", "specificType", "host"}).getPropertyValues();
      if (ArrayUtils.isEmpty(foundProperties)) {
         return new DatastoreData[0];
      } else {
         Map<ManagedObjectReference, List<PropertyValue>> propertiesByObject = QueryUtil.groupPropertiesByObject(foundProperties);
         List<ManagedObjectReference> vsanHosts = this.getVsanHosts(clusterRef);
         List<DatastoreData> allDatastores = new ArrayList();
         Iterator var8 = propertiesByObject.keySet().iterator();

         while(var8.hasNext()) {
            ManagedObjectReference datastoreMor = (ManagedObjectReference)var8.next();
            List<PropertyValue> datastoreProperties = (List)propertiesByObject.get(datastoreMor);
            boolean suitableForArchive = true;
            DatastoreData datastoreData = new DatastoreData();
            Iterator var13 = datastoreProperties.iterator();

            while(var13.hasNext()) {
               PropertyValue property = (PropertyValue)var13.next();
               String var14;
               switch((var14 = property.propertyName).hashCode()) {
               case -1857640538:
                  if (var14.equals("summary")) {
                     Summary summary = (Summary)property.value;
                     datastoreData.mor = summary.datastore;
                     datastoreData.name = summary.name;
                     datastoreData.capacity = FormatUtil.getStorageFormatted(summary.capacity, 1L, 1073741824L);
                     datastoreData.freeSpace = FormatUtil.getStorageFormatted(summary.freeSpace, 1L, 1073741824L);
                     datastoreData.url = summary.url;
                  }
                  break;
               case -1205140596:
                  if (var14.equals("specificType")) {
                     datastoreData.type = DatastoreData.Type.fromString((String)property.value);
                     if (!ARCHIVE_DP_DATASTORE_TYPES.contains(datastoreData.type)) {
                        suitableForArchive = false;
                     }
                  }
                  break;
               case -826278890:
                  if (var14.equals("primaryIconId")) {
                     datastoreData.primaryIconId = (String)property.value;
                  }
                  break;
               case 3208616:
                  if (var14.equals("host") && !restoreOnly && !this.isDatastoreMountSuitableForArchive((HostMount[])property.value, vsanHosts)) {
                     suitableForArchive = false;
                  }
               }
            }

            if (suitableForArchive) {
               allDatastores.add(datastoreData);
            }
         }

         return (DatastoreData[])allDatastores.toArray(new DatastoreData[0]);
      }
   }

   @TsService
   public ManagedObjectReference configureDatastoreConsumptionLimit(ManagedObjectReference clusterRef, Integer dpUsageThreshold) throws Exception {
      Validate.notNull(clusterRef);
      if (VsanCapabilityUtils.isLocalDataProtectionSupported(clusterRef) && VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         DataProtectionInfo vsanDpConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).dataProtectionConfig;
         vsanDpConfig.setUsageThreshold(dpUsageThreshold);
         ReconfigSpec reconfigSpec = new ReconfigSpec();
         reconfigSpec.setDataProtectionConfig(vsanDpConfig);
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = profiler.point("vsanConfigSystem.reconfigureEx - DataProtectionConfig");

            Throwable var10000;
            label197: {
               boolean var10001;
               ManagedObjectReference var22;
               try {
                  ManagedObjectReference configureClusterTask = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
                  var22 = VmodlHelper.assignServerGuid(configureClusterTask, clusterRef.getServerGuid());
               } catch (Throwable var20) {
                  var10000 = var20;
                  var10001 = false;
                  break label197;
               }

               if (p != null) {
                  p.close();
               }

               label183:
               try {
                  return var22;
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label183;
               }
            }

            var6 = var10000;
            if (p != null) {
               p.close();
            }

            throw var6;
         } catch (Throwable var21) {
            if (var6 == null) {
               var6 = var21;
            } else if (var6 != var21) {
               var6.addSuppressed(var21);
            }

            throw var6;
         }
      } else {
         return null;
      }
   }

   @TsService
   public ManagedObjectReference configureArchiveDataProtection(ManagedObjectReference clusterRef, String archivalDpDatastoreUrl) throws Exception {
      Validate.notNull(clusterRef);
      if (VsanCapabilityUtils.isArchiveDataProtectionSupported(clusterRef) && VsanCapabilityUtils.isClusterConfigSystemSupportedOnVc(clusterRef)) {
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         DataProtectionInfo vsanDpConfig = vsanConfigSystem.getConfigInfoEx(clusterRef).dataProtectionConfig;
         vsanDpConfig.setArchivalTarget(getArchivalLocation(archivalDpDatastoreUrl));
         ReconfigSpec reconfigSpec = new ReconfigSpec();
         reconfigSpec.setDataProtectionConfig(vsanDpConfig);
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = profiler.point("vsanConfigSystem.reconfigureEx - DataProtectionConfig");

            Throwable var10000;
            label197: {
               boolean var10001;
               ManagedObjectReference var22;
               try {
                  ManagedObjectReference configureClusterTask = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
                  var22 = VmodlHelper.assignServerGuid(configureClusterTask, clusterRef.getServerGuid());
               } catch (Throwable var20) {
                  var10000 = var20;
                  var10001 = false;
                  break label197;
               }

               if (p != null) {
                  p.close();
               }

               label183:
               try {
                  return var22;
               } catch (Throwable var19) {
                  var10000 = var19;
                  var10001 = false;
                  break label183;
               }
            }

            var6 = var10000;
            if (p != null) {
               p.close();
            }

            throw var6;
         } catch (Throwable var21) {
            if (var6 == null) {
               var6 = var21;
            } else if (var6 != var21) {
               var6.addSuppressed(var21);
            }

            throw var6;
         }
      } else {
         return null;
      }
   }

   private static DataProtectionArchivalLocation getArchivalLocation(String archivalDpDatastoreUrl) {
      DataProtectionArchivalLocation archivalLocation = new DataProtectionArchivalLocation();
      archivalLocation.setDatastoreUrl(archivalDpDatastoreUrl != null ? archivalDpDatastoreUrl : "");
      return archivalLocation;
   }

   @TsService
   public ManagedObjectReference remediateClusterConfiguration(ManagedObjectReference clusterRef) throws Exception {
      VsanClusterMgmtInternalSystem clusterMgmtInternalSystem = VsanProviderUtils.getVsanClusterMgmtInternalSystem(clusterRef);
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = _profiler.point("clusterMgmtInternalSystem.remediateDataProtectionConfig");

         Throwable var10000;
         label173: {
            boolean var10001;
            ManagedObjectReference var19;
            try {
               ManagedObjectReference taskRef = clusterMgmtInternalSystem.remediateDataProtectionConfig(clusterRef);
               var19 = new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), clusterRef.getServerGuid());
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (p != null) {
               p.close();
            }

            label162:
            try {
               return var19;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (p != null) {
            p.close();
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

   @TsService
   public ManagedObjectReference disableRemotePairing(ManagedObjectReference clusterRef, boolean disconnectBothClusters, String username, String password, PscConnectionDetails remotePscDetails) throws VsanUiLocalizableException {
      DataProtectionInfo localDpConfig = this.dpConfigService.getDpConfig(clusterRef, (PscConnectionDetails)null);
      DataProtectionPairingInfo localPairingInfo = this.dpConfigService.getPairingInfo(localDpConfig);
      if (localPairingInfo == null) {
         logger.warn("No remote pairing info found for cluster " + clusterRef);
         return null;
      } else {
         if (disconnectBothClusters) {
            if (remotePscDetails != null) {
               this.authenticationService.authenticate(remotePscDetails, username, password);
            }

            ManagedObjectReference peerCluster = this.dpInventoryHelper.getPeerCluster(clusterRef, localPairingInfo, remotePscDetails);
            DataProtectionInfo peerDpConfig = this.dpConfigService.getDpConfig(peerCluster, remotePscDetails);
            this.unpairCluster(peerCluster, peerDpConfig, remotePscDetails);
         }

         return this.unpairCluster(clusterRef, localDpConfig, (PscConnectionDetails)null);
      }
   }

   private ManagedObjectReference unpairCluster(ManagedObjectReference clusterRef, DataProtectionInfo dpConfig, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      DataProtectionPairingInfo pairingInfo = this.dpConfigService.getPairingInfo(dpConfig);
      if (pairingInfo == null) {
         logger.warn("No remote pairing info found for cluster " + clusterRef);
         return null;
      } else {
         pairingInfo.setDeletePairing(true);
         ReconfigSpec reconfigureSpec = new ReconfigSpec();
         reconfigureSpec.dataProtectionConfig = dpConfig;
         return this.dpConfigService.reconfigureCluster(clusterRef, pscDetails, reconfigureSpec);
      }
   }

   @TsService
   public ManagedObjectReference reconnectRemoteDP(ManagedObjectReference sourceCluster, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      DataProtectionInfo sourceDpConfig = null;
      sourceDpConfig = this.dpConfigService.getDpConfig(sourceCluster, (PscConnectionDetails)null);
      DataProtectionPairingInfo sourcePairingInfo = this.dpConfigService.getPairingInfo(sourceDpConfig);
      if (sourcePairingInfo == null) {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.not.configured.error");
      } else {
         ManagedObjectReference remoteCluster = this.dpInventoryHelper.getPeerCluster(sourceCluster, sourcePairingInfo, pscDetails);
         DataProtectionInfo remoteDpConfig = this.dpConfigService.getDpConfig(remoteCluster, pscDetails);
         this.validateRemotePairing(sourcePairingInfo.localDatastoreUrl, remoteDpConfig);
         this.updateThumbprint(sourcePairingInfo, pscDetails);
         DataProtectionLoadBalancersInfo sourceLbInfo = this.dpConfigService.getLoadBalancersInfo(sourceCluster, (PscConnectionDetails)null);
         DataProtectionLoadBalancersInfo remoteLbInfo = this.dpConfigService.getLoadBalancersInfo(remoteCluster, pscDetails);
         ManagedObjectReference taskRef = this.updateLoadBalancerInfo(sourceCluster, (PscConnectionDetails)null, sourceDpConfig, sourceLbInfo, remoteLbInfo);
         this.updateLoadBalancerInfo(remoteCluster, pscDetails, remoteDpConfig, remoteLbInfo, sourceLbInfo);
         return taskRef;
      }
   }

   private void validateRemotePairing(String sourceDatastoreUrl, DataProtectionInfo remoteDpConfig) throws VsanUiLocalizableException {
      DataProtectionPairingInfo remotePairingInfo = this.dpConfigService.getPairingInfo(remoteDpConfig);
      if (remotePairingInfo == null) {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.peer.cluster.not.configured.error");
      } else {
         String peerClusterPeerDatastoreUrl = remotePairingInfo.getPeerDatastoreUrl();
         if (!sourceDatastoreUrl.equals(peerClusterPeerDatastoreUrl)) {
            throw new VsanUiLocalizableException("vsan.dataProtection.remote.peer.cluster.connected.error");
         }
      }
   }

   private ManagedObjectReference updateLoadBalancerInfo(ManagedObjectReference cluster, PscConnectionDetails pscDetails, DataProtectionInfo dpConfig, DataProtectionLoadBalancersInfo sourceLbInfo, DataProtectionLoadBalancersInfo remoteLbInfo) throws VsanUiLocalizableException {
      DataProtectionPairingInfo pairingInfo = this.dpConfigService.getPairingInfo(dpConfig);
      pairingInfo.setLocalLoadBalancers(sourceLbInfo.getBasicLoadBalancerInfo());
      pairingInfo.setPeerLoadBalancers(remoteLbInfo.getDetailedLoadBalancerInfo());
      return this.dpConfigService.reconfigureCluster(cluster, pscDetails, this.buildReconfigSpec(dpConfig));
   }

   private void updateThumbprint(DataProtectionPairingInfo sourcePairingInfo, PscConnectionDetails pscDetails) {
      String remoteLsThumbprint = pscDetails == null ? null : pscDetails.pscThumbprint;
      DataProtectionPeerSiteInfo peerInfo = sourcePairingInfo.getPeerSite();
      peerInfo.setLookupServiceThumbprint(remoteLsThumbprint);
   }

   private ReconfigSpec buildReconfigSpec(DataProtectionInfo dpConfig) {
      ReconfigSpec spec = new ReconfigSpec();
      spec.setDataProtectionConfig(dpConfig);
      return spec;
   }

   @TsService
   public ManagedObjectReference editRemoteReplicationPort(ManagedObjectReference cluster, int updatedPort) throws VsanUiLocalizableException {
      DataProtectionInfo dataProtectionInfo = this.dpConfigService.getDpConfig(cluster, (PscConnectionDetails)null);
      dataProtectionInfo.setIncomingReplicationPort(updatedPort);
      dataProtectionInfo.setPairingInfo((DataProtectionPairingInfo[])null);
      dataProtectionInfo.setArchivalTarget((DataProtectionArchivalLocation)null);
      return this.dpConfigService.reconfigureCluster(cluster, (PscConnectionDetails)null, this.buildReconfigSpec(dataProtectionInfo));
   }

   private List<ManagedObjectReference> getVsanHosts(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference[] hostRefs = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host", (Object)null);
      return Arrays.asList(hostRefs);
   }

   private boolean isDatastoreMountSuitableForArchive(HostMount[] hostMounts, List<ManagedObjectReference> vsanHosts) {
      List<ManagedObjectReference> readWriteHosts = new ArrayList();
      HostMount[] var7 = hostMounts;
      int var6 = hostMounts.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         HostMount hostMount = var7[var5];
         MountInfo mountInfo = hostMount.getMountInfo();
         if (mountInfo.getAccessMode().equals("readWrite")) {
            readWriteHosts.add(hostMount.getKey());
         }
      }

      return readWriteHosts.containsAll(vsanHosts);
   }

   private void populateArchiveConfigData(ClusterDpConfigData dpConfigData, ManagedObjectReference clusterRef, ConfigInfoEx vsanConfig) throws Exception {
      if (vsanConfig.dataProtectionConfig.archivalTarget != null && !StringUtils.isEmpty(vsanConfig.dataProtectionConfig.archivalTarget.datastoreUrl)) {
         String datastoreUrl = vsanConfig.dataProtectionConfig.archivalTarget.datastoreUrl;
         dpConfigData.archivalDpDatastoreUrl = datastoreUrl;
         PropertyValue[] datastoreProperties = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "datastore", "ClusterComputeResource", new String[]{"summary.url", "name"}).getPropertyValues();
         Map<ManagedObjectReference, List<PropertyValue>> propertiesByObj = QueryUtil.groupPropertiesByObject(datastoreProperties);
         boolean matchFound = false;
         Iterator var9 = propertiesByObj.values().iterator();

         while(var9.hasNext()) {
            List<PropertyValue> properties = (List)var9.next();
            Iterator var11 = properties.iterator();

            while(var11.hasNext()) {
               PropertyValue property = (PropertyValue)var11.next();
               String var12;
               switch((var12 = property.propertyName).hashCode()) {
               case -1193995481:
                  if (var12.equals("summary.url") && property.value.equals(datastoreUrl)) {
                     matchFound = true;
                  }
                  break;
               case 3373707:
                  if (var12.equals("name")) {
                     dpConfigData.archivalDpDatastoreName = (String)property.value;
                     dpConfigData.archivalDpDatastoreRef = (ManagedObjectReference)property.resourceObject;
                  }
               }
            }

            if (matchFound) {
               break;
            }
         }

         if (!matchFound) {
            logger.error("Unable to find datasore with url: " + datastoreUrl + ", for clusterRef: " + clusterRef);
            dpConfigData.errorMessages.add(Utils.getLocalizedString("vsan.dataprotection.archive.datastore.not.found"));
         }

      }
   }

   private void populateRemoteConfigData(ClusterDpConfigData dpConfigData, ManagedObjectReference clusterRef, ConfigInfoEx localClusterVsanConfig) throws Exception {
      if (!ArrayUtils.isEmpty(localClusterVsanConfig.dataProtectionConfig.pairingInfo)) {
         DataProtectionPairingInfo pairingInfo = localClusterVsanConfig.dataProtectionConfig.pairingInfo[0];
         dpConfigData.sourceDatastoreUrl = pairingInfo.localDatastoreUrl;
         dpConfigData.sourceDatastoreRef = this.dpInventoryHelper.getVsanDatastore(clusterRef, dpConfigData.sourceDatastoreUrl);
         dpConfigData.remoteClusterName = pairingInfo.peerClusterName;
         if (pairingInfo.peerSite == null) {
            logger.error("Missing remote peer site info! Perhaps pairing was done using API with incomplete details");
            dpConfigData.errorMessages.add(Utils.getLocalizedString("vsan.dataprotection.remote.cluster.not.configured"));
         } else {
            DataProtectionPeerSiteInfo peerSite = pairingInfo.peerSite;
            dpConfigData.remoteVcName = peerSite.name;

            try {
               if (peerSite.getLookupServiceUrl() == null) {
                  logger.debug("Remote data protection pairing to the VC from same SSO.");
                  String remoteServerGuid = this.dpInventoryHelper.findServerGiud(peerSite.nodeId, (PscConnectionDetails)null, clusterRef.getServerGuid());
                  dpConfigData.remoteVcRef = VmodlHelper.getRootFolder(remoteServerGuid);
                  dpConfigData.remoteClusterRef = this.dpInventoryHelper.getPeerCluster(clusterRef, pairingInfo, (PscConnectionDetails)null);
               } else {
                  logger.debug("Remote data protection pairing to the VC from different SSO.");
                  dpConfigData.lsThumbprint = peerSite.getLookupServiceThumbprint();
                  URL lsUrl = this.getLsUrl(peerSite.getLookupServiceUrl());
                  dpConfigData.lsHost = lsUrl.getHost();
                  dpConfigData.lsPort = lsUrl.getPort() == this.PORT_NOT_SET ? lsUrl.getDefaultPort() : lsUrl.getPort();
               }
            } catch (Exception var7) {
               logger.error("Unable to determine the remote pairing peer cluster.", var7);
               dpConfigData.errorMessages.add(Utils.getLocalizedString("vsan.dataprotection.remote.cluster.not.found"));
            }
         }

      }
   }

   private URL getLsUrl(String lsAddress) throws MalformedURLException {
      return StringUtils.isEmpty(lsAddress) ? null : URI.create(lsAddress).toURL();
   }
}
