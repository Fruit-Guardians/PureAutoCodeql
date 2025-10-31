package com.vmware.vsan.client.services.configurecluster;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.vsan.host.DiskMapInfoEx;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.common.data.ConnectionState;
import com.vmware.vsan.client.services.diskmanagement.DiskManagementService;
import com.vmware.vsan.client.util.NoOpMeasure;
import com.vmware.vsphere.client.vsan.data.VsanConfigSpec;
import com.vmware.vsphere.client.vsan.impl.ConfigureVsanClusterMutationProvider;
import com.vmware.vsphere.client.vsan.stretched.VsanStretchedClusterPropertyProvider;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConfigureClusterService {
   private static final Logger logger = LoggerFactory.getLogger(ConfigureClusterService.class);
   private static final String HOST_RELATION = "host";
   private static final String CLUSTER_TYPE = ClusterComputeResource.class.getSimpleName();
   private static final String HOST_NAME_PROPERTY = "name";
   private static final String HOST_PRIMARY_ICON_PROPERTY = "primaryIconId";
   private static final String HOST_CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   private static final String HOST_VERSION_PROPERTY = "config.product.version";
   private static final String FAULT_DOMAIN_NAME_PROPERTY = "config.vsanHostConfig.faultDomainInfo.name";
   private static final String HA_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.enabled";
   private static final String DPM_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dpmConfigInfo.enabled";
   @Autowired
   private VsanStretchedClusterPropertyProvider stretchedClusterProvider;
   @Autowired
   private ObjectReferenceService refService;
   @Autowired
   private ConfigureVsanClusterMutationProvider mutationProvider;
   @Autowired
   private DiskManagementService diskMgmtService;

   @TsService
   public List<HostFaultDomainData> getClusterHostFaultDomainData(ManagedObjectReference clusterRef) throws Exception {
      PropertyValue[] props = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", CLUSTER_TYPE, new String[]{"name", "primaryIconId", "runtime.connectionState", "config.product.version", "config.vsanHostConfig.faultDomainInfo.name"}).getPropertyValues();
      Map<ManagedObjectReference, List<PropertyValue>> propsMap = QueryUtil.groupPropertiesByObject(props);
      List<HostFaultDomainData> result = new ArrayList();
      Iterator var6 = propsMap.keySet().iterator();

      while(var6.hasNext()) {
         ManagedObjectReference mor = (ManagedObjectReference)var6.next();
         List<PropertyValue> objectProps = (List)propsMap.get(mor);
         HostFaultDomainData.Builder builder = new HostFaultDomainData.Builder();
         builder.hostUid(this.refService.getUid(mor));
         Iterator var10 = objectProps.iterator();

         while(var10.hasNext()) {
            PropertyValue property = (PropertyValue)var10.next();
            String var11;
            switch((var11 = property.propertyName).hashCode()) {
            case -826278890:
               if (var11.equals("primaryIconId")) {
                  builder.primaryIconId((String)property.value);
               }
               break;
            case 3373707:
               if (var11.equals("name")) {
                  builder.name((String)property.value);
               }
               break;
            case 707737491:
               if (var11.equals("config.vsanHostConfig.faultDomainInfo.name")) {
                  builder.faultDomainName((String)property.value);
               }
               break;
            case 1445673005:
               if (var11.equals("config.product.version")) {
                  builder.version((String)property.value);
               }
               break;
            case 2004020797:
               if (var11.equals("runtime.connectionState")) {
                  ConnectionState state = ConnectionState.valueOf(((com.vmware.vim.binding.vim.HostSystem.ConnectionState)property.value).name());
                  builder.connectionState(state);
               }
            }
         }

         result.add(builder.createHostFaultDomainData());
      }

      return result;
   }

   @TsService
   public String getPrerequisitesWarning(ManagedObjectReference clusterRef) throws Exception {
      Map<Object, Map<String, Object>> result = QueryUtil.getProperties(clusterRef, new String[]{"configurationEx[@type='ClusterConfigInfoEx'].dasConfig.enabled", "configurationEx[@type='ClusterConfigInfoEx'].dpmConfigInfo.enabled"}).getMap();
      Map<String, Object> properties = (Map)result.get(clusterRef);
      boolean haEnabled = Boolean.valueOf("" + properties.get("configurationEx[@type='ClusterConfigInfoEx'].dasConfig.enabled"));
      boolean dpmEnabled = Boolean.valueOf("" + properties.get("configurationEx[@type='ClusterConfigInfoEx'].dpmConfigInfo.enabled"));
      if (haEnabled && dpmEnabled) {
         return Utils.getLocalizedString("vsan.generalConfig.haAndDpm.enabled.warning");
      } else if (haEnabled) {
         return Utils.getLocalizedString("vsan.generalConfig.ha.enabled.warning");
      } else {
         return dpmEnabled ? Utils.getLocalizedString("vsan.generalConfig.dpm.enabled.warning") : null;
      }
   }

   @TsService
   public boolean getStretchClusterSupported(ManagedObjectReference clusterRef) throws Exception {
      return this.stretchedClusterProvider.getIsStretchedClusterSupported(clusterRef);
   }

   @TsService
   public boolean hasHybridDiskGroups(ManagedObjectReference clusterRef) {
      try {
         ManagedObjectReference[] hosts = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host", (Object)null);
         Map<ManagedObjectReference, Future<DiskMapInfoEx[]>> diskGroupsFutureByHost = this.diskMgmtService.getDiskMappingsAsync(Arrays.asList(hosts), new NoOpMeasure());
         Iterator var5 = diskGroupsFutureByHost.keySet().iterator();

         while(true) {
            DiskMapInfoEx[] diskGroups;
            do {
               if (!var5.hasNext()) {
                  return false;
               }

               ManagedObjectReference hostRef = (ManagedObjectReference)var5.next();
               diskGroups = (DiskMapInfoEx[])((Future)diskGroupsFutureByHost.get(hostRef)).get();
            } while(diskGroups == null);

            DiskMapInfoEx[] var10 = diskGroups;
            int var9 = diskGroups.length;

            for(int var8 = 0; var8 < var9; ++var8) {
               DiskMapInfoEx info = var10[var8];
               if (!info.isAllFlash) {
                  return true;
               }
            }
         }
      } catch (Exception var11) {
         logger.warn("Failed to check disk groups for cluster: " + clusterRef);
         return false;
      }
   }

   @TsService("configureClusterTask")
   public ManagedObjectReference configureCluster(ManagedObjectReference clusterRef, VsanConfigSpec configSpec) throws Exception {
      return this.mutationProvider.configure(clusterRef, configSpec);
   }
}
