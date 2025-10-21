package com.vmware.vsphere.client.vsan.impl;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.host.Capability;
import com.vmware.vim.binding.vim.host.ScsiDisk;
import com.vmware.vim.binding.vim.host.VsanInternalSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.host.VsanInternalSystem.DecomParam;
import com.vmware.vim.binding.vim.host.VsanInternalSystem.DecommissioningBatch;
import com.vmware.vim.binding.vim.host.VsanInternalSystem.DecommissioningSatisfiability;
import com.vmware.vim.binding.vim.vsan.host.ClusterStatus;
import com.vmware.vim.binding.vim.vsan.host.DecommissionMode;
import com.vmware.vim.binding.vim.vsan.host.DiskResult;
import com.vmware.vim.binding.vim.vsan.host.DecommissionMode.ObjectAction;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.base.util.Version;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.VsanDiskData;
import com.vmware.vsphere.client.vsan.spec.VsanQueryDataEvacuationInfoSpec;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanPropertyProvider {
   @Autowired
   private VcClient _vcClient;
   private static final String DATASTORE_TYPE_PROPERTY = "summary.type";
   private static final String VSAN_DATASTORE_TYPE = "vsan";
   private static final String DATASTORE_PROPERTY = "datastore";
   private static final String VC_CLUSTERS_PROPERTY = "allClusters";
   private static final String HOST_VERSION_OP = "5.5.0";
   private static final Log _logger = LogFactory.getLog(VsanPropertyProvider.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanPropertyProvider.class);

   @TsService
   public ManagedObjectReference getAnyVsanCluster(ManagedObjectReference vcRef) throws Exception {
      DataServiceResponse propertiesForRelatedObjects = QueryUtil.getPropertiesForRelatedObjects(vcRef, "allClusters", ClusterComputeResource.class.getSimpleName(), new String[]{"configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled"});
      PropertyValue[] properties = propertiesForRelatedObjects.getPropertyValues();
      if (ArrayUtils.isEmpty(properties)) {
         return null;
      } else {
         PropertyValue[] var7 = properties;
         int var6 = properties.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PropertyValue propertyValue = var7[var5];
            Boolean vsanEnabled = (Boolean)propertyValue.value;
            if (vsanEnabled != null && vsanEnabled) {
               return (ManagedObjectReference)propertyValue.resourceObject;
            }
         }

         return null;
      }
   }

   @TsService
   public boolean getIsVsanClusterPartitioned(ManagedObjectReference clusterRef) throws Exception {
      if (clusterRef != null && this.isVsanEnabledOnCluster(clusterRef)) {
         Collection<VsanPropertyProvider.VsanHostResourceData> hostsInCluster = this.getVsanHostResourcesInCluster(clusterRef);
         if (hostsInCluster != null && !hostsInCluster.isEmpty()) {
            int numberHostsInUse = 0;
            int numberHostsInClusterNode = 0;
            Iterator var6 = hostsInCluster.iterator();

            while(var6.hasNext()) {
               VsanPropertyProvider.VsanHostResourceData hostData = (VsanPropertyProvider.VsanHostResourceData)var6.next();
               if (hostData != null && hostData.isVsanEnabled != null && hostData.isVsanEnabled) {
                  if (numberHostsInUse == 0 && hostData.vsanHostClusterStatus != null && hostData.vsanHostClusterStatus.memberUuid != null) {
                     numberHostsInClusterNode = hostData.vsanHostClusterStatus.memberUuid.length;
                  }

                  ++numberHostsInUse;
               }
            }

            numberHostsInUse += this.getNumberOfWitnessHosts(clusterRef);
            if (numberHostsInClusterNode != numberHostsInUse) {
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         _logger.warn("Null cluster reference or vsan not enabled on cluster, returning false.");
         return false;
      }
   }

   @TsService
   public VsanDiskData[] getEligibleDisks(ManagedObjectReference hostRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this._vcClient.getConnection(hostRef.getServerGuid());

         Throwable var10000;
         label373: {
            DiskResult[] results;
            boolean var10001;
            label371: {
               try {
                  VsanSystem vsanSystem = VsanProviderUtils.getHostVsanSystem(hostRef, vcConnection);
                  results = this.getHostDisksForVsan(vsanSystem);
                  if (results != null) {
                     break label371;
                  }
               } catch (Throwable var31) {
                  var10000 = var31;
                  var10001 = false;
                  break label373;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return null;
            }

            VsanDiskData[] var33;
            try {
               ArrayList<VsanDiskData> eligibleDisks = new ArrayList();
               DiskResult[] var11 = results;
               int var10 = results.length;
               int var9 = 0;

               while(true) {
                  if (var9 >= var10) {
                     var33 = (VsanDiskData[])eligibleDisks.toArray(new VsanDiskData[eligibleDisks.size()]);
                     break;
                  }

                  DiskResult result = var11[var9];
                  if (Utils.isDiskEligible(result)) {
                     VsanDiskData diskData = new VsanDiskData();
                     diskData.disk = result.disk;
                     diskData.inUse = false;
                     if (result.error != null) {
                        diskData.stateReason = result.error.getLocalizedMessage();
                     }

                     eligibleDisks.add(diskData);
                  }

                  ++var9;
               }
            } catch (Throwable var30) {
               var10000 = var30;
               var10001 = false;
               break label373;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label354:
            try {
               return var33;
            } catch (Throwable var29) {
               var10000 = var29;
               var10001 = false;
               break label354;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var32) {
         if (var2 == null) {
            var2 = var32;
         } else if (var2 != var32) {
            var2.addSuppressed(var32);
         }

         throw var2;
      }
   }

   private DiskResult[] getHostDisksForVsan(VsanSystem vsanSystem) {
      if (vsanSystem == null) {
         return null;
      } else {
         DiskResult[] disks = null;

         try {
            Throwable var3 = null;
            Object var4 = null;

            try {
               VsanProfiler.Point point = _profiler.point("vsanSystem.queryDisksForVsan(null)");

               try {
                  disks = vsanSystem.queryDisksForVsan((String[])null);
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var13) {
               if (var3 == null) {
                  var3 = var13;
               } else if (var3 != var13) {
                  var3.addSuppressed(var13);
               }

               throw var3;
            }
         } catch (Exception var14) {
            _logger.error(var14.getMessage());
         }

         return disks;
      }
   }

   @TsService
   public Boolean getIsAllFlashAvailable(ManagedObjectReference hostRef) {
      return VsanCapabilityUtils.isAllFlashSupportedOnHost(hostRef);
   }

   @TsService
   public long getVsanDataEvacuationInfo(ManagedObjectReference hostRef, VsanQueryDataEvacuationInfoSpec spec) throws Exception {
      if (spec.disks != null && spec.disks.length != 0) {
         Map<String, Object> hostProps = BaseUtils.getProperties(hostRef, new String[]{"config.vsanHostConfig.clusterInfo.nodeUuid"});
         String vsanHostUuid = (String)hostProps.get("config.vsanHostConfig.clusterInfo.nodeUuid");
         if (vsanHostUuid == null) {
            _logger.warn("Failed to retrieve vsanHostUuid.");
            return 0L;
         } else {
            Throwable var5 = null;
            Object var6 = null;

            try {
               VcConnection vcConnection = this._vcClient.getConnection(hostRef.getServerGuid());

               Throwable var10000;
               label846: {
                  label851: {
                     DecommissioningSatisfiability[] decommissionCosts;
                     boolean var10001;
                     try {
                        VsanInternalSystem vsanInternalSystem = VsanProviderUtils.getVsanInternalSystem(hostRef, vcConnection);
                        DecommissioningBatch batch = createNewDecommissioningBatch(spec.disks, vsanHostUuid);

                        try {
                           Throwable var11 = null;
                           Object var12 = null;

                           try {
                              VsanProfiler.Point p = _profiler.point("vsanInternalSystem.canDecommission");

                              try {
                                 decommissionCosts = vsanInternalSystem.canDecommission(new DecommissioningBatch[]{batch});
                              } finally {
                                 if (p != null) {
                                    p.close();
                                 }

                              }
                           } catch (Throwable var60) {
                              if (var11 == null) {
                                 var11 = var60;
                              } else if (var11 != var60) {
                                 var11.addSuppressed(var60);
                              }

                              throw var11;
                           }
                        } catch (Exception var61) {
                           _logger.error("Failed to retrieve vsan evacuation data!", var61);
                           throw var61;
                        }

                        if (decommissionCosts == null) {
                           _logger.error("Failed to retrieve vsan evacuation data: invalid result!");
                           break label851;
                        }
                     } catch (Throwable var64) {
                        var10000 = var64;
                        var10001 = false;
                        break label846;
                     }

                     long var68;
                     try {
                        long dataToEvacuate = 0L;
                        DecommissioningSatisfiability[] var16 = decommissionCosts;
                        int var15 = decommissionCosts.length;

                        for(int var14 = 0; var14 < var15; ++var14) {
                           DecommissioningSatisfiability cost = var16[var14];
                           if (cost != null && cost.cost != null) {
                              dataToEvacuate += cost.cost.usedDataSize;
                           }
                        }

                        var68 = dataToEvacuate;
                     } catch (Throwable var63) {
                        var10000 = var63;
                        var10001 = false;
                        break label846;
                     }

                     if (vcConnection != null) {
                        vcConnection.close();
                     }

                     try {
                        return var68;
                     } catch (Throwable var62) {
                        var10000 = var62;
                        var10001 = false;
                        break label846;
                     }
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return 0L;
               }

               var5 = var10000;
               if (vcConnection != null) {
                  vcConnection.close();
               }

               throw var5;
            } catch (Throwable var65) {
               if (var5 == null) {
                  var5 = var65;
               } else if (var5 != var65) {
                  var5.addSuppressed(var65);
               }

               throw var5;
            }
         }
      } else {
         return 0L;
      }
   }

   private static DecommissioningBatch createNewDecommissioningBatch(ScsiDisk[] disks, String vsanHostUuid) {
      DecommissioningBatch batch = new DecommissioningBatch();
      DecomParam[] decomParams = new DecomParam[disks.length];

      for(int i = 0; i < disks.length; ++i) {
         decomParams[i] = new DecomParam();
         decomParams[i].scsiDisk = disks[i];
         decomParams[i].nodeUUID = vsanHostUuid;
      }

      batch.dp = decomParams;
      batch.mode = new DecommissionMode();
      batch.mode.objectAction = ObjectAction.evacuateAllData.toString();
      return batch;
   }

   @TsService
   public Boolean getVsanEnabledOnHost(ManagedObjectReference hostRef) throws Exception {
      if (!this.getVsanSupportedForHost(hostRef)) {
         return false;
      } else {
         Boolean vsanEnabled = (Boolean)QueryUtil.getProperty(hostRef, "config.vsanHostConfig.enabled", (Object)null);
         return vsanEnabled == null ? false : vsanEnabled;
      }
   }

   @TsService
   public boolean getVsanSupportedForHost(ManagedObjectReference hostRef) throws Exception {
      if (!this.getIsEsxOPorLater(hostRef)) {
         return false;
      } else {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VcConnection vcConnection = this._vcClient.getConnection(hostRef.getServerGuid());

            Throwable var10000;
            label488: {
               HostSystem host;
               boolean var10001;
               label485: {
                  try {
                     host = (HostSystem)vcConnection.createStub(HostSystem.class, hostRef);
                     if (host != null) {
                        break label485;
                     }

                     _logger.error("Null host reference encountered.");
                  } catch (Throwable var35) {
                     var10000 = var35;
                     var10001 = false;
                     break label488;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  return false;
               }

               boolean var37;
               label489: {
                  try {
                     Capability hostCapability = host.getCapability();
                     if (hostCapability != null) {
                        var37 = hostCapability.vsanSupported;
                        break label489;
                     }
                  } catch (Throwable var34) {
                     var10000 = var34;
                     var10001 = false;
                     break label488;
                  }

                  if (vcConnection != null) {
                     vcConnection.close();
                  }

                  try {
                     return false;
                  } catch (Throwable var33) {
                     var10000 = var33;
                     var10001 = false;
                     break label488;
                  }
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               label456:
               try {
                  return var37;
               } catch (Throwable var32) {
                  var10000 = var32;
                  var10001 = false;
                  break label456;
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
   }

   @TsService
   public Boolean getIsEsxOPorLater(ManagedObjectReference hostRef) throws Exception {
      String version = (String)QueryUtil.getProperty(hostRef, "config.product.version", (Object)null);
      Version esxVersion = new Version(version);
      return esxVersion.compareTo(new Version("5.5.0")) >= 0 ? true : false;
   }

   @TsService
   public boolean getIsWitnessHost(ManagedObjectReference hostRef) throws Exception {
      if (!this.getVsanSupportedForHost(hostRef)) {
         return false;
      } else {
         VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(hostRef);
         boolean isWitnessHost = false;
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.isWitnessHost");

            try {
               stretchedClusterSystem.isWitnessHost(hostRef);
            } finally {
               if (point != null) {
                  point.close();
               }

            }

            return isWitnessHost;
         } catch (Throwable var12) {
            if (var4 == null) {
               var4 = var12;
            } else if (var4 != var12) {
               var4.addSuppressed(var12);
            }

            throw var4;
         }
      }
   }

   @TsService
   public Boolean getIsVmOnVsanDatastore(ManagedObjectReference vmRef) throws Exception {
      Constraint vmDatastoresConstraint = QueryUtil.createConstraintForRelationship(vmRef, "datastore", Datastore.class.getSimpleName());
      QuerySpec query = QueryUtil.buildQuerySpec(vmDatastoresConstraint, new String[]{"summary.type"});
      ResultSet resultSet = null;

      try {
         resultSet = QueryUtil.getData(query);
      } catch (Exception var13) {
         _logger.error(var13.getMessage());
         throw var13;
      }

      if (resultSet != null && resultSet.items != null) {
         ResultItem[] var8;
         int var7 = (var8 = resultSet.items).length;

         for(int var6 = 0; var6 < var7; ++var6) {
            ResultItem item = var8[var6];
            if (item != null && item.properties != null) {
               PropertyValue[] var12;
               int var11 = (var12 = item.properties).length;

               for(int var10 = 0; var10 < var11; ++var10) {
                  PropertyValue pv = var12[var10];
                  if ("summary.type".equals(pv.propertyName) && "vsan".equalsIgnoreCase((String)pv.value)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @TsService
   public int getNumberOfWitnessHosts(ManagedObjectReference clusterRef) {
      if (clusterRef == null) {
         _logger.error("Null cluster reference encountered.");
         return 0;
      } else {
         int witnessHosts = 0;

         try {
            Throwable var3 = null;
            Object var4 = null;

            try {
               VsanProfiler.Point point = _profiler.point("stretchedClusterSystem.getNumOfWitnessHosts");

               try {
                  VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
                  witnessHosts = stretchedClusterSystem.getNumOfWitnessHosts(clusterRef);
               } finally {
                  if (point != null) {
                     point.close();
                  }

               }
            } catch (Throwable var14) {
               if (var3 == null) {
                  var3 = var14;
               } else if (var3 != var14) {
                  var3.addSuppressed(var14);
               }

               throw var3;
            }
         } catch (Exception var15) {
            _logger.error("Could not retrieve witness hosts for cluster " + var15.getMessage());
         }

         return witnessHosts;
      }
   }

   private Collection<VsanPropertyProvider.VsanHostResourceData> getVsanHostResourcesInCluster(ManagedObjectReference clusterRef) {
      if (clusterRef == null) {
         _logger.error("Null cluster reference encountered.");
         return null;
      } else {
         PropertyValue[] hostProperties = null;

         try {
            hostProperties = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"vsanHostClusterStatus", "config.vsanHostConfig.enabled"}).getPropertyValues();
         } catch (Exception var11) {
            _logger.error("Failed to get hosts in cluster!", var11);
         }

         if (hostProperties == null) {
            return null;
         } else {
            Map<ManagedObjectReference, VsanPropertyProvider.VsanHostResourceData> hosts = new HashMap();
            PropertyValue[] var7 = hostProperties;
            int var6 = hostProperties.length;

            for(int var5 = 0; var5 < var6; ++var5) {
               PropertyValue propValue = var7[var5];
               ManagedObjectReference hostRef = (ManagedObjectReference)propValue.resourceObject;
               VsanPropertyProvider.VsanHostResourceData hostData = (VsanPropertyProvider.VsanHostResourceData)hosts.get(hostRef);
               if (hostData == null) {
                  hostData = new VsanPropertyProvider.VsanHostResourceData((VsanPropertyProvider.VsanHostResourceData)null);
                  hosts.put(hostRef, hostData);
               }

               if ("vsanHostClusterStatus".equals(propValue.propertyName)) {
                  hostData.vsanHostClusterStatus = (ClusterStatus)propValue.value;
               } else if ("config.vsanHostConfig.enabled".equals(propValue.propertyName)) {
                  Boolean vsanEnabled = (Boolean)propValue.value;
                  hostData.isVsanEnabled = vsanEnabled == null ? false : vsanEnabled;
               }
            }

            return hosts.values();
         }
      }
   }

   @TsService
   public Boolean isVsanEnabledOnCluster(ManagedObjectReference clusterRef) throws Exception {
      return (Boolean)QueryUtil.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled", (Object)null);
   }

   @TsService
   public Boolean isVsanNonEmptyCluster(ManagedObjectReference clusterRef) throws Exception {
      int hostsNumber = (Integer)QueryUtil.getProperty(clusterRef, "host._length", (Object)null);
      return hostsNumber > 0 ? true : false;
   }

   private class VsanHostResourceData {
      public ClusterStatus vsanHostClusterStatus;
      public Boolean isVsanEnabled;

      private VsanHostResourceData() {
      }

      // $FF: synthetic method
      VsanHostResourceData(VsanPropertyProvider.VsanHostResourceData var2) {
         this();
      }
   }
}
