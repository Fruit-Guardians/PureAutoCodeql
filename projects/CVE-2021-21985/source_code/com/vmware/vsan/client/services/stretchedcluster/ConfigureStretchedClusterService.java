package com.vmware.vsan.client.services.stretchedcluster;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.ComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterLimitHealthResult;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcStretchedClusterSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanLimitHealthResult;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.diskGroups.data.VsanDiskMapping;
import com.vmware.vsan.client.services.diskmanagement.DiskManagementService;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.NoOpMeasure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.stretched.VsanStretchedClusterConfig;
import com.vmware.vsphere.client.vsan.stretched.VsanStretchedClusterMutationProvider;
import com.vmware.vsphere.client.vsan.stretched.VsanWitnessConfig;
import com.vmware.vsphere.client.vsan.stretched.WitnessHostData;
import com.vmware.vsphere.client.vsan.stretched.WitnessHostSpec;
import com.vmware.vsphere.client.vsan.stretched.WitnessHostValidationResult;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConfigureStretchedClusterService {
   private static final Log logger = LogFactory.getLog(ConfigureStretchedClusterService.class);
   private static final String[] DOMAIN_PROPERTIES = new String[]{"name", "isWitnessHost", "config.vsanHostConfig.faultDomainInfo.name", "primaryIconId", "runtime.inMaintenanceMode", "runtime.connectionState", "preferredFaultDomain"};
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private ObjectReferenceService refService;
   @Autowired
   private DiskManagementService diskMgmtService;
   @Autowired
   private VsanStretchedClusterMutationProvider stretchedClusterMutationProvider;

   @TsService
   public List<DomainOrHostData> getAvailableDomains(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), DOMAIN_PROPERTIES);
      List<DomainOrHostData> result = new ArrayList();
      String preferredFaultDomainName = null;
      Map<String, List<DomainOrHostData>> map = new HashMap();
      Iterator var7 = response.getResourceObjects().iterator();

      while(var7.hasNext()) {
         Object hostRef = var7.next();
         String name = (String)response.getProperty(hostRef, "name");
         boolean isWitnessHost = Boolean.valueOf("" + response.getProperty(hostRef, "isWitnessHost"));
         String domainName = (String)response.getProperty(hostRef, "config.vsanHostConfig.faultDomainInfo.name");
         String iconId = (String)response.getProperty(hostRef, "primaryIconId");
         String hostUid = this.refService.getUid(hostRef);
         boolean maintenanceMode = (Boolean)response.getProperty(hostRef, "runtime.inMaintenanceMode");
         ConnectionState connectionState = (ConnectionState)response.getProperty(hostRef, "runtime.connectionState");
         String hostPreferredFaultDomainName = (String)response.getProperty(hostRef, "preferredFaultDomain");
         if (isWitnessHost) {
            preferredFaultDomainName = hostPreferredFaultDomainName;
         } else {
            if (domainName != null && domainName.length() == 0) {
               domainName = null;
            }

            if (domainName != null) {
               DomainOrHostData hostData = DomainOrHostData.createHostData(hostUid, name, iconId, maintenanceMode, com.vmware.vsan.client.services.common.data.ConnectionState.fromHostState(connectionState));
               List<DomainOrHostData> addTo = (List)map.get(domainName);
               if (addTo == null) {
                  addTo = new ArrayList();
                  map.put(domainName, addTo);
               }

               ((List)addTo).add(hostData);
            } else {
               result.add(DomainOrHostData.createHostData(hostUid, name, iconId, maintenanceMode, com.vmware.vsan.client.services.common.data.ConnectionState.fromHostState(connectionState)));
            }
         }
      }

      var7 = map.keySet().iterator();

      while(var7.hasNext()) {
         String domainName = (String)var7.next();
         result.add(DomainOrHostData.createDomainData(domainName, domainName, domainName.equals(preferredFaultDomainName), (List)map.get(domainName)));
      }

      return result;
   }

   @TsService
   public List<HostStorageConsumptionData> getStorageConsumptionByHost(ManagedObjectReference clusterRef) throws Exception {
      boolean isHostReservedCapacitySupported = VsanCapabilityUtils.isHostReservedCapacitySupportedOnVc(clusterRef);
      Throwable var3 = null;
      Object var4 = null;

      try {
         Measure measure = new Measure("Calculating storage consumption by host");

         ArrayList var36;
         label372: {
            Throwable var10000;
            label375: {
               Map hostNameAndMoRefMap;
               VsanClusterLimitHealthResult result;
               ArrayList consumptionList;
               boolean var10001;
               try {
                  VsanVcClusterHealthSystem clusterHealthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
                  Future<VsanClusterLimitHealthResult> future = measure.newFuture("Query host capacity usage");
                  clusterHealthSystem.queryCheckLimits(clusterRef, future);
                  hostNameAndMoRefMap = this.getHostNameAndMoRefMap(clusterRef);
                  result = (VsanClusterLimitHealthResult)future.get();
                  consumptionList = new ArrayList();
                  if (ArrayUtils.isEmpty(result.hostResults)) {
                     var36 = consumptionList;
                     break label372;
                  }
               } catch (Throwable var34) {
                  var10000 = var34;
                  var10001 = false;
                  break label375;
               }

               try {
                  VsanLimitHealthResult[] var14;
                  int var13 = (var14 = result.hostResults).length;
                  int var12 = 0;

                  while(true) {
                     if (var12 >= var13) {
                        var36 = consumptionList;
                        break;
                     }

                     VsanLimitHealthResult hostResult = var14[var12];
                     HostStorageConsumptionData hostData = new HostStorageConsumptionData();
                     hostData.totalCapacity = hostResult.totalDiskSpaceB;
                     hostData.userCapacity = hostResult.usedDiskSpaceB;
                     if (isHostReservedCapacitySupported && hostResult.cdReservedSizeB != null) {
                        hostData.reservedCapacity = hostResult.cdReservedSizeB;
                     }

                     hostData.hostRef = (ManagedObjectReference)hostNameAndMoRefMap.get(hostResult.hostname);
                     consumptionList.add(hostData);
                     ++var12;
                  }
               } catch (Throwable var33) {
                  var10000 = var33;
                  var10001 = false;
                  break label375;
               }

               if (measure != null) {
                  measure.close();
               }

               label354:
               try {
                  return var36;
               } catch (Throwable var32) {
                  var10000 = var32;
                  var10001 = false;
                  break label354;
               }
            }

            var3 = var10000;
            if (measure != null) {
               measure.close();
            }

            throw var3;
         }

         if (measure != null) {
            measure.close();
         }

         return var36;
      } catch (Throwable var35) {
         if (var3 == null) {
            var3 = var35;
         } else if (var3 != var35) {
            var3.addSuppressed(var35);
         }

         throw var3;
      }
   }

   private Map<String, ManagedObjectReference> getHostNameAndMoRefMap(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse response = QueryUtil.getPropertyForRelatedObjects(clusterRef, "host", ClusterComputeResource.class.getSimpleName(), "name");
      Map<String, ManagedObjectReference> result = new HashMap();
      PropertyValue[] var7;
      int var6 = (var7 = response.getPropertyValues()).length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue value = var7[var5];
         result.put((String)value.value, (ManagedObjectReference)value.resourceObject);
      }

      return result;
   }

   @TsService
   public boolean hasDiskGroups(ManagedObjectReference witnessHost) {
      try {
         Map<ManagedObjectReference, Future<DiskMapInfoEx[]>> diskGroupsFutureByHost = this.diskMgmtService.getDiskMappingsAsync(Arrays.asList(witnessHost), new NoOpMeasure());
         DiskMapInfoEx[] diskGroups = (DiskMapInfoEx[])((Future)diskGroupsFutureByHost.get(witnessHost)).get();
         return !ArrayUtils.isEmpty(diskGroups);
      } catch (Exception var4) {
         logger.warn("Failed to check disk groups for host: " + witnessHost);
         return false;
      }
   }

   @TsService("validateWitnessHost")
   public WitnessHostValidationResult getWitnessHostValidationError(ManagedObjectReference clusterRef, ManagedObjectReference witnessHost) throws Exception {
      WitnessHostSpec validationSpec = new WitnessHostSpec();
      validationSpec.witnessHost = this.getWitnessHostRef(witnessHost);
      return this.stretchedClusterMutationProvider.validateWitnessHost(clusterRef, validationSpec);
   }

   @TsService("configureStretchedClusterTask")
   public ManagedObjectReference configureStretchedCluster(ManagedObjectReference clusterRef, String preferredName, DomainOrHostData[] preferredDomains, String secondaryName, DomainOrHostData[] secondaryDomains, ManagedObjectReference witnessHost, VsanDiskMapping witnessHostDiskMapping) throws Exception {
      VsanStretchedClusterConfig spec = new VsanStretchedClusterConfig();
      spec.isFaultDomainConfigurationChanged = this.isDomainConfigChanged(preferredName, preferredDomains) || this.isDomainConfigChanged(secondaryName, secondaryDomains);
      spec.preferredSiteName = preferredName;
      spec.preferredSiteHosts = new ArrayList();
      DomainOrHostData[] var12 = preferredDomains;
      int var11 = preferredDomains.length;

      DomainOrHostData domain;
      int var10;
      DomainOrHostData host;
      int var14;
      int var15;
      DomainOrHostData[] var16;
      for(var10 = 0; var10 < var11; ++var10) {
         domain = var12[var10];
         if (domain.isHost) {
            spec.preferredSiteHosts.add((ManagedObjectReference)this.refService.getReference(domain.uid));
         } else {
            var15 = (var16 = domain.children).length;

            for(var14 = 0; var14 < var15; ++var14) {
               host = var16[var14];
               spec.preferredSiteHosts.add((ManagedObjectReference)this.refService.getReference(host.uid));
            }
         }
      }

      spec.secondarySiteName = secondaryName;
      spec.secondarySiteHosts = new ArrayList();
      var12 = secondaryDomains;
      var11 = secondaryDomains.length;

      for(var10 = 0; var10 < var11; ++var10) {
         domain = var12[var10];
         if (domain.isHost) {
            spec.secondarySiteHosts.add((ManagedObjectReference)this.refService.getReference(domain.uid));
         } else {
            var15 = (var16 = domain.children).length;

            for(var14 = 0; var14 < var15; ++var14) {
               host = var16[var14];
               spec.secondarySiteHosts.add((ManagedObjectReference)this.refService.getReference(host.uid));
            }
         }
      }

      spec.witnessHost = this.getWitnessHostRef(witnessHost);
      if (witnessHostDiskMapping != null) {
         spec.witnessHostDiskMapping = witnessHostDiskMapping.toVmodl();
      }

      return this.stretchedClusterMutationProvider.configureStretchedCluster(clusterRef, spec);
   }

   @TsService
   public WitnessHostData getWitnessHostData(ManagedObjectReference clusterRef) throws Exception {
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      VSANWitnessHostInfo[] witnessInfos = stretchedClusterSystem.getWitnessHosts(clusterRef);
      if (witnessInfos != null && witnessInfos.length != 0) {
         ManagedObjectReference witnessRef = new ManagedObjectReference(witnessInfos[0].host.getType(), witnessInfos[0].host.getValue(), clusterRef.getServerGuid());
         Map<String, ? extends Object> properties = QueryUtil.getProperties(witnessRef, new String[]{"name", "primaryIconId"}).getMap();
         properties = (Map)(properties.containsKey(witnessRef) ? properties.get(witnessRef) : Collections.emptyMap());
         WitnessHostData result = new WitnessHostData();
         result.preferredFaultDomainName = witnessInfos[0].preferredFdName;
         result.witnessHost = witnessRef;
         result.witnessHostName = properties.get("name") instanceof String ? (String)properties.get("name") : Utils.getLocalizedString("vsan.faultDomains.witnessOutOfInventory");
         result.witnessHostIcon = properties.get("primaryIconId") instanceof String ? (String)properties.get("primaryIconId") : "host";
         return result;
      } else {
         return null;
      }
   }

   @TsService("changeWitnessHostTask")
   public ManagedObjectReference changeWitnessHost(ManagedObjectReference clusterRef, String preferredName, ManagedObjectReference witnessHost, VsanDiskMapping witnessHostDiskMapping) throws Exception {
      VsanWitnessConfig spec = new VsanWitnessConfig();
      spec.host = this.getWitnessHostRef(witnessHost);
      spec.preferredFaultDomain = preferredName;
      if (witnessHostDiskMapping != null) {
         spec.diskMapping = witnessHostDiskMapping.toVmodl();
      }

      return this.stretchedClusterMutationProvider.setWitnessHost(clusterRef, spec);
   }

   @TsService
   public ManagedObjectReference getWitnessHostRef(ManagedObjectReference hostOrComputeResource) throws Exception {
      if (this.vmodlHelper.isOfType(hostOrComputeResource, ComputeResource.class)) {
         hostOrComputeResource = (ManagedObjectReference)QueryUtil.getProperty(hostOrComputeResource, "host", (Object)null);
      }

      return hostOrComputeResource;
   }

   private boolean isDomainConfigChanged(String name, DomainOrHostData[] domainsAndHosts) {
      return domainsAndHosts.length != 1 || domainsAndHosts[0].isHost || !domainsAndHosts[0].uid.equals(name);
   }

   public VsanHostsResult collectVsanHosts(ManagedObjectReference clusterRef, boolean includeWitness, Measure measure) throws Exception {
      VsanVcStretchedClusterSystem stretchedClusterSystem = VsanProviderUtils.getVcStretchedClusterSystem(clusterRef);
      Future<VSANWitnessHostInfo[]> witnessHostsFuture = null;
      if (includeWitness) {
         witnessHostsFuture = measure.newFuture("VSANWitnessHostInfo[]");
         stretchedClusterSystem.getWitnessHosts(clusterRef, witnessHostsFuture);
      }

      Throwable var7 = null;
      VSANWitnessHostInfo info = null;

      PropertyValue[] hostProps;
      try {
         Measure hostsMeasure = measure.start("DS(hosts)");

         try {
            hostProps = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"runtime.connectionState"}).getPropertyValues();
         } finally {
            if (hostsMeasure != null) {
               hostsMeasure.close();
            }

         }
      } catch (Throwable var17) {
         if (var7 == null) {
            var7 = var17;
         } else if (var7 != var17) {
            var7.addSuppressed(var17);
         }

         throw var7;
      }

      VSANWitnessHostInfo[] witnessHostInfos = witnessHostsFuture != null ? (VSANWitnessHostInfo[])witnessHostsFuture.get() : null;
      if (witnessHostInfos != null) {
         VSANWitnessHostInfo[] var11 = witnessHostInfos;
         int var10 = witnessHostInfos.length;

         for(int var19 = 0; var19 < var10; ++var19) {
            info = var11[var19];
            info.host = new ManagedObjectReference(info.host.getType(), info.host.getValue(), clusterRef.getServerGuid());
         }
      }

      return new VsanHostsResult(hostProps, witnessHostInfos);
   }
}
