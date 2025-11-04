package com.vmware.vsan.client.services.hci.model;

import com.vmware.vim.binding.vim.ClusterComputeResource.DvsProfile;
import com.vmware.vim.binding.vim.ClusterComputeResource.HCIConfigSpec;
import com.vmware.vim.binding.vim.ClusterComputeResource.HostConfigurationInput;
import com.vmware.vim.binding.vim.ClusterComputeResource.HostConfigurationProfile;
import com.vmware.vim.binding.vim.ClusterComputeResource.HostVmkNicInfo;
import com.vmware.vim.binding.vim.ClusterComputeResource.VCProfile;
import com.vmware.vim.binding.vim.ClusterComputeResource.DvsProfile.DVPortgroupSpecToServiceMapping;
import com.vmware.vim.binding.vim.cluster.ConfigSpecEx;
import com.vmware.vim.binding.vim.cluster.DasConfigInfo;
import com.vmware.vim.binding.vim.cluster.DrsConfigInfo;
import com.vmware.vim.binding.vim.cluster.DasConfigInfo.ServiceState;
import com.vmware.vim.binding.vim.cluster.DasConfigInfo.VmMonitoringState;
import com.vmware.vim.binding.vim.cluster.DrsConfigInfo.DrsBehavior;
import com.vmware.vim.binding.vim.dvs.DistributedVirtualPortgroup.ConfigSpec;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.VlanIdSpec;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.VmwarePortConfigPolicy;
import com.vmware.vim.binding.vim.host.DateTimeConfig;
import com.vmware.vim.binding.vim.host.NtpConfig;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanPerfsvcConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ProactiveRebalanceInfo;
import com.vmware.vim.vsan.binding.vim.vsan.RdmaConfig;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanExtendedConfig;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVumConfig;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.data.VsanConfigSpec;
import com.vmware.vsphere.client.vsan.data.VumBaselineRecommendationType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang.StringUtils;

@data
public class ClusterConfigData {
   private static final long DEFAULT_OBJECT_REPAIR_TIMER = 60L;
   public BasicClusterConfigData basicConfig;
   public boolean enableAdmissionControl;
   public VsanConfigSpec vsanConfigSpec;
   public int hostFTT;
   public boolean enableHostMonitoring;
   public boolean enableVmMonitoring;
   public DrsAutoLevel automationLevel;
   public int migrationThreshold;
   public boolean enableEVC;
   public String selectedEvcMode;
   public LockdownMode lockdownMode;
   public String ntpServer;
   public boolean optOutOfNetConfig;
   public DvsSpec[] dvsSpecs;
   public NetServiceConfig[] netServiceConfigs;
   public boolean largeScaleClusterSupport;
   public VumBaselineRecommendationType vumBaselineRecommendationType;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$DrsAutoLevel;

   public ClusterConfigData() {
      this.lockdownMode = LockdownMode.DISABLED;
   }

   public HCIConfigSpec getHciConfigSpec(ManagedObjectReference clusterRef, boolean hasEncryptionPermissions) throws Exception {
      HCIConfigSpec hciConfigSpec = new HCIConfigSpec();
      if (this.basicConfig.vsanEnabled) {
         ReconfigSpec reconfigSpec = this.vsanConfigSpec.getReconfigSpec(clusterRef, hasEncryptionPermissions);
         reconfigSpec.perfsvcConfig = new VsanPerfsvcConfig();
         reconfigSpec.perfsvcConfig.enabled = true;
         VsanExtendedConfig extendedConfig = new VsanExtendedConfig(60L, false, true, this.largeScaleClusterSupport, (ProactiveRebalanceInfo)null);
         reconfigSpec.setExtendedConfig(extendedConfig);
         if (this.vsanConfigSpec.enableRdma) {
            reconfigSpec.rdmaConfig = new RdmaConfig();
            reconfigSpec.rdmaConfig.rdmaEnabled = true;
         }

         if (VsanCapabilityUtils.isVumBaselineRecommendationSupportedOnVc(clusterRef)) {
            reconfigSpec.vumConfig = new VsanVumConfig();
            reconfigSpec.vumConfig.setBaselinePreferenceType(this.vumBaselineRecommendationType.toString());
         }

         hciConfigSpec.vSanConfigSpec = reconfigSpec;
      }

      hciConfigSpec.vcProf = this.getVcProfile();
      hciConfigSpec.dvsProf = this.getDvsProfiles();
      hciConfigSpec.hostConfigProfile = this.getHostConfigProfile();
      return hciConfigSpec;
   }

   private VCProfile getVcProfile() {
      ConfigSpecEx configSpec = new ConfigSpecEx();
      configSpec.inHciWorkflow = null;
      if (this.basicConfig.haEnabled) {
         DasConfigInfo dasConfig = new DasConfigInfo();
         dasConfig.enabled = true;
         dasConfig.admissionControlEnabled = this.enableAdmissionControl;
         dasConfig.failoverLevel = this.hostFTT;
         dasConfig.hostMonitoring = (this.enableHostMonitoring ? ServiceState.enabled : ServiceState.disabled).toString();
         dasConfig.vmMonitoring = (this.enableVmMonitoring ? VmMonitoringState.vmMonitoringOnly : VmMonitoringState.vmMonitoringDisabled).toString();
         configSpec.dasConfig = dasConfig;
      }

      if (this.basicConfig.drsEnabled) {
         DrsConfigInfo drsConfig = new DrsConfigInfo();
         drsConfig.enabled = true;
         drsConfig.defaultVmBehavior = this.getDrsBehavior(this.automationLevel);
         drsConfig.vmotionRate = 6 - this.migrationThreshold;
         configSpec.drsConfig = drsConfig;
      }

      VCProfile vcProfile = new VCProfile();
      vcProfile.clusterSpec = configSpec;
      if (this.enableEVC) {
         vcProfile.evcModeKey = this.selectedEvcMode;
      }

      return vcProfile;
   }

   private DrsBehavior getDrsBehavior(DrsAutoLevel autoLevel) {
      switch($SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$DrsAutoLevel()[autoLevel.ordinal()]) {
      case 1:
         return DrsBehavior.fullyAutomated;
      case 2:
         return DrsBehavior.manual;
      case 3:
         return DrsBehavior.partiallyAutomated;
      default:
         return DrsBehavior.fullyAutomated;
      }
   }

   public DvsProfile[] getDvsProfiles() throws Exception {
      List<DvsProfile> dvsProfiles = new ArrayList();
      if (!this.optOutOfNetConfig && this.dvsSpecs != null) {
         DvsSpec[] var5;
         int var4 = (var5 = this.dvsSpecs).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            DvsSpec dvsSpec = var5[var3];
            DvsProfile profile = new DvsProfile();
            if (dvsSpec.existingDvsMor != null) {
               profile.dvSwitch = dvsSpec.existingDvsMor;
            } else {
               profile.dvsName = dvsSpec.name;
            }

            profile.pnicDevices = this.getPnicDevices(dvsSpec);
            profile.dvPortgroupMapping = this.getDvPortgroupMappings(dvsSpec, this.netServiceConfigs);
            dvsProfiles.add(profile);
         }
      }

      return (DvsProfile[])dvsProfiles.toArray(new DvsProfile[0]);
   }

   private String[] getPnicDevices(DvsSpec dvsSpec) {
      if (dvsSpec.adapters == null) {
         return null;
      } else {
         String[] result = new String[dvsSpec.adapters.length];

         for(int i = 0; i < dvsSpec.adapters.length; ++i) {
            result[i] = dvsSpec.adapters[i].deviceName;
         }

         return result;
      }
   }

   private DVPortgroupSpecToServiceMapping[] getDvPortgroupMappings(DvsSpec dvsSpec, NetServiceConfig[] netServiceConfigs) {
      if (dvsSpec.services != null) {
         List<DVPortgroupSpecToServiceMapping> result = new ArrayList();
         Service[] var7;
         int var6 = (var7 = dvsSpec.services).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            Service service = var7[var5];
            if (service != Service.MANAGEMENT) {
               DVPortgroupSpecToServiceMapping mapping = new DVPortgroupSpecToServiceMapping();
               mapping.service = service.getText();
               NetServiceConfig netServiceConfig = this.getNetServiceConfig(netServiceConfigs, service);
               if (netServiceConfig != null) {
                  if (netServiceConfig.existingDvpgMor != null) {
                     mapping.dvPortgroup = netServiceConfig.existingDvpgMor;
                  } else {
                     mapping.dvPortgroupSpec = new ConfigSpec();
                     mapping.dvPortgroupSpec.name = netServiceConfig.dvpgName;
                     mapping.dvPortgroupSpec.type = "earlyBinding";
                     if (netServiceConfig.useVlan) {
                        VlanIdSpec vlanIdSpec = new VlanIdSpec();
                        vlanIdSpec.vlanId = netServiceConfig.vlan;
                        VmwarePortConfigPolicy configPolicy = new VmwarePortConfigPolicy();
                        configPolicy.vlan = vlanIdSpec;
                        mapping.dvPortgroupSpec.defaultPortConfig = configPolicy;
                     }
                  }

                  result.add(mapping);
               }
            }
         }

         return (DVPortgroupSpecToServiceMapping[])result.toArray(new DVPortgroupSpecToServiceMapping[0]);
      } else {
         return null;
      }
   }

   private NetServiceConfig getNetServiceConfig(NetServiceConfig[] configs, Service service) {
      if (configs != null) {
         NetServiceConfig[] var6 = configs;
         int var5 = configs.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            NetServiceConfig config = var6[var4];
            if (config.service == service) {
               return config;
            }
         }
      }

      return null;
   }

   private HostConfigurationProfile getHostConfigProfile() {
      HostConfigurationProfile result = new HostConfigurationProfile();
      if (!StringUtils.isBlank(this.ntpServer)) {
         DateTimeConfig timeConfig = new DateTimeConfig();
         timeConfig.ntpConfig = new NtpConfig();
         String[] ntpServers = StringUtils.split(this.ntpServer, ",");

         for(int i = 0; i < ntpServers.length; ++i) {
            ntpServers[i] = ntpServers[i].trim();
         }

         timeConfig.ntpConfig.server = ntpServers;
         result.dateTimeConfig = timeConfig;
      }

      result.lockdownMode = this.lockdownMode.getVmodlLockdownMode();
      return result;
   }

   public HostConfigurationInput[] getHostConfigurationInputs(List<HostInCluster> hosts) throws Exception {
      List<HostConfigurationInput> result = new ArrayList();
      if (this.basicConfig.hciWorkflowState == HciWorkflowState.DONE || this.basicConfig.hciWorkflowState == HciWorkflowState.IN_PROGRESS && !this.optOutOfNetConfig && this.netServiceConfigs != null) {
         Iterator var4 = hosts.iterator();

         while(var4.hasNext()) {
            HostInCluster host = (HostInCluster)var4.next();
            result.add(this.getHostConfigurationInput(host));
         }
      }

      return result.size() > 0 ? (HostConfigurationInput[])result.toArray(new HostConfigurationInput[0]) : null;
   }

   private HostConfigurationInput getHostConfigurationInput(HostInCluster host) {
      HostConfigurationInput result = new HostConfigurationInput();
      result.host = host.moRef;
      result.hostVmkNics = this.getHostVmkNicInfos(host.name);
      return result;
   }

   private HostVmkNicInfo[] getHostVmkNicInfos(String hostName) {
      List<HostVmkNicInfo> result = new ArrayList();
      if (this.netServiceConfigs != null) {
         NetServiceConfig[] var6;
         int var5 = (var6 = this.netServiceConfigs).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            NetServiceConfig netServiceConfig = var6[var4];
            result.add(netServiceConfig.getHostVmkNicInfo(hostName));
         }
      }

      return (HostVmkNicInfo[])result.toArray(new HostVmkNicInfo[0]);
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$DrsAutoLevel() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$DrsAutoLevel;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[DrsAutoLevel.values().length];

         try {
            var0[DrsAutoLevel.FULLY_AUTOMATED.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[DrsAutoLevel.MANUAL.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[DrsAutoLevel.PARTIALLY_AUTOMATED.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$DrsAutoLevel = var0;
         return var0;
      }
   }
}
