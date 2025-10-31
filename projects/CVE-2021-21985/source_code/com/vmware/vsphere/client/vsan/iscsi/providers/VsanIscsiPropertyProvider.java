package com.vmware.vsphere.client.vsan.iscsi.providers;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.EnvironmentBrowser;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.host.VirtualNic;
import com.vmware.vim.binding.vim.vm.ConfigTarget;
import com.vmware.vim.binding.vim.vm.DatastoreInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetServiceConfig;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.Conjoiner;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.Version;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.iscsi.models.config.VsanIscsiConfig;
import com.vmware.vsphere.client.vsan.iscsi.models.config.VsanIscsiTargetConfig;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

public class VsanIscsiPropertyProvider {
   private static final VsanProfiler _profiler = new VsanProfiler(VsanIscsiPropertyProvider.class);
   private static final String HOST_KEY = "hostKey";
   private static final Version HOST_VERSION_2015 = new Version("6.0.0");
   private static final String VNIC_NAME_PROPERTY = "config.network.vnic";
   private VcClient vcClient;

   public void setVcClient(VcClient vcClient) {
      this.vcClient = vcClient;
   }

   @TsService
   public VsanIscsiConfig getVsanIscsiConfig(ManagedObjectReference clusterRef) throws Exception {
      if (!VsanCapabilityUtils.isIscsiTargetsSupportedOnVc(clusterRef)) {
         return null;
      } else {
         VsanIscsiConfig vsanIscsiConfig = new VsanIscsiConfig();
         VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
         ConfigInfoEx config = null;
         Throwable var5 = null;
         VsanObjectInformation vsanObjectInformation = null;

         try {
            VsanProfiler.Point point = _profiler.point("vsanConfigSystem.getConfigInfoEx");

            try {
               config = vsanConfigSystem.getConfigInfoEx(clusterRef);
            } finally {
               if (point != null) {
                  point.close();
               }

            }
         } catch (Throwable var31) {
            if (var5 == null) {
               var5 = var31;
            } else if (var5 != var31) {
               var5.addSuppressed(var31);
            }

            throw var5;
         }

         if (config != null && config.getIscsiConfig() != null && config.getIscsiConfig().enabled != null) {
            vsanIscsiConfig.vsanIscsiTargetServiceConfig = config.getIscsiConfig();
            if (config.enabled && config.defaultConfig != null && vsanIscsiConfig.vsanIscsiTargetServiceConfig.enabled) {
               VsanIscsiTargetSystem vsanIscsiSystem = VsanProviderUtils.getVsanIscsiSystem(clusterRef);
               vsanObjectInformation = null;

               try {
                  Throwable var36 = null;
                  Object var8 = null;

                  try {
                     VsanProfiler.Point p = _profiler.point("vsanIscsiSystem.getHomeObject");

                     try {
                        vsanObjectInformation = vsanIscsiSystem.getHomeObject(clusterRef);
                     } finally {
                        if (p != null) {
                           p.close();
                        }

                     }
                  } catch (Throwable var33) {
                     if (var36 == null) {
                        var36 = var33;
                     } else if (var36 != var33) {
                        var36.addSuppressed(var33);
                     }

                     throw var36;
                  }
               } catch (Exception var34) {
               }

               vsanIscsiConfig.vsanObjectInformation = vsanObjectInformation;
            }

            return vsanIscsiConfig;
         } else {
            vsanIscsiConfig.vsanIscsiTargetServiceConfig = new VsanIscsiTargetServiceConfig();
            vsanIscsiConfig.vsanIscsiTargetServiceConfig.enabled = false;
            return vsanIscsiConfig;
         }
      }
   }

   @TsService
   public VsanIscsiTargetConfig getVsanIscsiTargetConfig(ManagedObjectReference clusterRef) throws Exception {
      VsanIscsiConfig config = this.getVsanIscsiConfig(clusterRef);
      if (config == null) {
         return null;
      } else {
         Profile spbmProfile = null;
         if (config.vsanObjectInformation != null && config.vsanObjectInformation.vsanObjectUuid != null && config.vsanObjectInformation.spbmProfileUuid != null) {
            ManagedObjectReference vcRootRef = VmodlHelper.getRootFolder(clusterRef.getServerGuid());

            try {
               PropertyValue[] resultset = QueryUtil.getPropertiesForRelatedObjects(vcRootRef, "pbmProfiles", "PbmRequirementStorageProfile", new String[]{"profileContent"}).getPropertyValues();
               PropertyValue[] var9 = resultset;
               int var8 = resultset.length;

               for(int var7 = 0; var7 < var8; ++var7) {
                  PropertyValue profileContent = var9[var7];
                  Profile profile = (Profile)profileContent.value;
                  if (profile.profileId.uniqueId.equals(config.vsanObjectInformation.spbmProfileUuid)) {
                     spbmProfile = profile;
                     break;
                  }
               }
            } catch (Exception var11) {
               throw Utils.getMethodFault(var11);
            }
         }

         VsanIscsiTargetConfig targetConfig = new VsanIscsiTargetConfig(config, this.isEmptyClusterForIscsi(clusterRef), VsanCapabilityUtils.isIscsiTargetsSupportedOnCluster(clusterRef), this.getIsHostsVersionValid(clusterRef), spbmProfile);
         return targetConfig;
      }
   }

   @TsService
   public boolean getIsVsanIscsiEnabledOnHost(ManagedObjectReference hostRef) throws Exception {
      Boolean result = false;
      PropertyValue[] values = QueryUtil.getPropertyForRelatedObjects(hostRef, "parent", "ClusterComputeResource", "isVsanIscsiEnabled").getPropertyValues();
      if (values.length > 0) {
         result = (Boolean)values[0].value;
      }

      return result;
   }

   public boolean isEmptyClusterForIscsi(ManagedObjectReference clusterRef) throws Exception {
      DatastoreInfo[] vsanDatastoresByCluster = this.getVsanDatastoresByCluster(clusterRef);
      if (ArrayUtils.isEmpty(vsanDatastoresByCluster)) {
         return true;
      } else {
         DatastoreInfo[] var6 = vsanDatastoresByCluster;
         int var5 = vsanDatastoresByCluster.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            DatastoreInfo datastoreInfo = var6[var4];
            ManagedObjectReference vsanDatastoreRef = datastoreInfo.datastore.datastore;
            if (vsanDatastoreRef == null) {
               return true;
            }

            ManagedObjectReference hostRef = this.getConnectedHost(vsanDatastoreRef);
            if (hostRef == null) {
               return true;
            }
         }

         return false;
      }
   }

   @TsService
   public DatastoreInfo[] getVsanDatastoresByCluster(ManagedObjectReference clusterRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

         label638: {
            Throwable var10000;
            label644: {
               ManagedObjectReference envBrowserRef;
               boolean var10001;
               label640: {
                  try {
                     ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
                     envBrowserRef = cluster.getEnvironmentBrowser();
                     if (envBrowserRef != null) {
                        break label640;
                     }
                  } catch (Throwable var43) {
                     var10000 = var43;
                     var10001 = false;
                     break label644;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return null;
               }

               EnvironmentBrowser eBrowser;
               label641: {
                  try {
                     eBrowser = (EnvironmentBrowser)vcConnection.createStub(EnvironmentBrowser.class, envBrowserRef);
                     if (eBrowser != null) {
                        break label641;
                     }
                  } catch (Throwable var42) {
                     var10000 = var42;
                     var10001 = false;
                     break label644;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return null;
               }

               DatastoreInfo[] var45;
               try {
                  ConfigTarget configTarget = eBrowser.queryConfigTarget((ManagedObjectReference)null);
                  DatastoreInfo[] datastoreInfos = configTarget.datastore;
                  if (datastoreInfos == null || datastoreInfos.length <= 0) {
                     break label638;
                  }

                  List<DatastoreInfo> vsanDatastores = new ArrayList();
                  DatastoreInfo[] var14 = datastoreInfos;
                  int var13 = datastoreInfos.length;
                  int var12 = 0;

                  while(true) {
                     if (var12 >= var13) {
                        var45 = (DatastoreInfo[])vsanDatastores.toArray(new DatastoreInfo[vsanDatastores.size()]);
                        break;
                     }

                     DatastoreInfo datastoreInfo = var14[var12];
                     if (datastoreInfo != null && datastoreInfo.datastore != null && datastoreInfo.datastore.type != null && datastoreInfo.datastore.type.toLowerCase().equals("vsan") && datastoreInfo.datastore.datastore != null) {
                        VmodlHelper.assignServerGuid(datastoreInfo.datastore.datastore, clusterRef.getServerGuid());
                        vsanDatastores.add(datastoreInfo);
                     }

                     ++var12;
                  }
               } catch (Throwable var41) {
                  var10000 = var41;
                  var10001 = false;
                  break label644;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label611:
               try {
                  return var45;
               } catch (Throwable var40) {
                  var10000 = var40;
                  var10001 = false;
                  break label611;
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

         return null;
      } catch (Throwable var44) {
         if (var2 == null) {
            var2 = var44;
         } else if (var2 != var44) {
            var2.addSuppressed(var44);
         }

         throw var2;
      }
   }

   @TsService
   public Map getHostSystemObjects(ManagedObjectReference clusterRef) throws Exception {
      PropertyValue[] props = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"name"}).getPropertyValues();
      if (!ArrayUtils.isNotEmpty(props)) {
         return null;
      } else {
         Map<String, String> hostMap = new HashMap();
         PropertyValue[] var7 = props;
         int var6 = props.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PropertyValue propValue = var7[var5];
            hostMap.put(((ManagedObjectReference)propValue.resourceObject).getValue(), (String)propValue.value);
         }

         return hostMap;
      }
   }

   @TsService
   public ManagedObjectReference[] getClusterConnectedHosts(ManagedObjectReference clusterRef) throws Exception {
      if (clusterRef == null) {
         return null;
      } else {
         ResultSet resultSet = this.queryConnectedHosts(clusterRef, "host");
         if (resultSet != null && resultSet.items != null) {
            List<ManagedObjectReference> list = new ArrayList();
            ResultItem[] var7;
            int var6 = (var7 = resultSet.items).length;

            for(int var5 = 0; var5 < var6; ++var5) {
               ResultItem resultItem = var7[var5];
               if (!ArrayUtils.isEmpty(resultItem.properties)) {
                  ManagedObjectReference connectedHostRef = (ManagedObjectReference)resultItem.resourceObject;
                  list.add(connectedHostRef);
               }
            }

            ManagedObjectReference[] hosts = new ManagedObjectReference[list.size()];
            return (ManagedObjectReference[])list.toArray(hosts);
         } else {
            return null;
         }
      }
   }

   @TsService
   public Boolean getIsHostsVersionValid(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference[] hosts = this.getClusterConnectedHosts(clusterRef);
      if (!ArrayUtils.isNotEmpty(hosts)) {
         return false;
      } else {
         ManagedObjectReference[] var6 = hosts;
         int var5 = hosts.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            ManagedObjectReference hostRef = var6[var4];
            boolean iscsiSupportedOnHost = VsanCapabilityUtils.isIscsiTargetsSupportedOnHost(hostRef);
            if (!iscsiSupportedOnHost) {
               return iscsiSupportedOnHost;
            }
         }

         return true;
      }
   }

   @TsService
   public String[] getHostsCommonVnicList(ManagedObjectReference clusterRef) throws Exception {
      Constraint hostsForCluster = QueryUtil.createConstraintForRelationship(clusterRef, "host", HostSystem.class.getSimpleName());
      QuerySpec qSpec = QueryUtil.buildQuerySpec(hostsForCluster, new String[]{"name", "config.network.vnic"});
      ResultSet resultSet = QueryUtil.getData(qSpec);
      if (resultSet == null) {
         return null;
      } else {
         Set<String> vnicSet = new HashSet();

         for(int i = 0; i < resultSet.items.length; ++i) {
            ResultItem resultItem = resultSet.items[i];
            if (resultItem.properties != null && resultItem.properties.length > 1) {
               Set<String> currentVnicSet = new HashSet();
               PropertyValue propertyValue = resultItem.properties[1];
               if (propertyValue != null) {
                  Object value = propertyValue.value;
                  if (value != null) {
                     if (value instanceof VirtualNic) {
                        VirtualNic vnic = (VirtualNic)value;
                        if (vnic != null && !StringUtils.isWhitespace(vnic.device)) {
                           currentVnicSet.add(vnic.device);
                        }
                     } else if (value instanceof VirtualNic[]) {
                        VirtualNic[] vnicArray = (VirtualNic[])value;
                        if (vnicArray != null) {
                           VirtualNic[] var15 = vnicArray;
                           int var14 = vnicArray.length;

                           for(int var13 = 0; var13 < var14; ++var13) {
                              VirtualNic everyVnic = var15[var13];
                              if (everyVnic != null && !StringUtils.isWhitespace(everyVnic.device)) {
                                 currentVnicSet.add(everyVnic.device);
                              }
                           }
                        }
                     }
                  }
               }

               if (i == 0) {
                  vnicSet.addAll(currentVnicSet);
               } else {
                  vnicSet.retainAll(currentVnicSet);
               }
            }
         }

         return (String[])vnicSet.toArray(new String[0]);
      }
   }

   private ResultSet queryConnectedHosts(ManagedObjectReference mor, String relationShip) throws Exception {
      if (mor == null) {
         return null;
      } else {
         Constraint dsHostsConstraint = QueryUtil.createConstraintForRelationship(mor, relationShip, HostSystem.class.getSimpleName());
         Constraint connectedHostsConstraint = QueryUtil.createPropertyConstraint(HostSystem.class.getSimpleName(), "runtime.connectionState", Comparator.EQUALS, ConnectionState.connected.name());
         Constraint dsConnectedHosts = QueryUtil.combineIntoSingleConstraint(new Constraint[]{dsHostsConstraint, connectedHostsConstraint}, Conjoiner.AND);
         QuerySpec qSpec = QueryUtil.buildQuerySpec(dsConnectedHosts, new String[]{"config.product.version"});
         qSpec.name = mor.getValue();
         ResultSet resultSet = QueryUtil.getData(qSpec);
         return resultSet;
      }
   }

   private ManagedObjectReference getConnectedHost(ManagedObjectReference datastore) throws Exception {
      if (datastore == null) {
         return null;
      } else {
         ResultSet resultSet = this.queryConnectedHosts(datastore, "hostKey");
         if (resultSet != null && resultSet.items != null) {
            ResultItem[] var6;
            int var5 = (var6 = resultSet.items).length;

            for(int var4 = 0; var4 < var5; ++var4) {
               ResultItem resultItem = var6[var4];
               if (!ArrayUtils.isEmpty(resultItem.properties)) {
                  String version = (String)resultItem.properties[0].value;
                  Version esxVersion = new Version(version);
                  if (esxVersion.compareTo(HOST_VERSION_2015) >= 0) {
                     ManagedObjectReference connectedHostRef = (ManagedObjectReference)resultSet.items[0].resourceObject;
                     return connectedHostRef;
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }
}
