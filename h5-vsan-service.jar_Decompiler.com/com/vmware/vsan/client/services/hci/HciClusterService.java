package com.vmware.vsan.client.services.hci;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.AuthorizationManager;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.EVCMode;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.NumericRange;
import com.vmware.vim.binding.vim.SDDCBase;
import com.vmware.vim.binding.vim.AuthorizationManager.DisabledMethodInfo;
import com.vmware.vim.binding.vim.ClusterComputeResource.DVSSetting;
import com.vmware.vim.binding.vim.ClusterComputeResource.DvsProfile;
import com.vmware.vim.binding.vim.ClusterComputeResource.HCIConfigInfo;
import com.vmware.vim.binding.vim.ClusterComputeResource.HCIConfigSpec;
import com.vmware.vim.binding.vim.ClusterComputeResource.HostConfigurationInput;
import com.vmware.vim.binding.vim.ClusterComputeResource.ValidationResultBase;
import com.vmware.vim.binding.vim.ClusterComputeResource.DVSSetting.DVPortgroupToServiceMapping;
import com.vmware.vim.binding.vim.cluster.EVCManager;
import com.vmware.vim.binding.vim.dvs.DistributedVirtualPortgroup;
import com.vmware.vim.binding.vim.dvs.HostMember;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.PvlanSpec;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.TrunkVlanSpec;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.VlanIdSpec;
import com.vmware.vim.binding.vim.dvs.VmwareDistributedVirtualSwitch.VlanSpec;
import com.vmware.vim.binding.vim.fault.InvalidState;
import com.vmware.vim.binding.vim.host.PhysicalNic;
import com.vmware.vim.binding.vim.host.CpuPackage.Vendor;
import com.vmware.vim.binding.vim.option.OptionManager;
import com.vmware.vim.binding.vim.option.OptionValue;
import com.vmware.vim.binding.vmodl.LocalizableMessage;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthSummary;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHealthTest;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.VsanExtendedConfig;
import com.vmware.vim.vsan.binding.vim.vsan.VsanHealthPerspective;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.common.CeipService;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.services.common.TaskService;
import com.vmware.vsan.client.services.encryption.EncryptionPropertyProvider;
import com.vmware.vsan.client.services.encryption.EncryptionStatus;
import com.vmware.vsan.client.services.hci.model.BasicClusterConfigData;
import com.vmware.vsan.client.services.hci.model.ClusterConfigData;
import com.vmware.vsan.client.services.hci.model.ConfigCardData;
import com.vmware.vsan.client.services.hci.model.ConfigureWizardData;
import com.vmware.vsan.client.services.hci.model.DrsAutoLevel;
import com.vmware.vsan.client.services.hci.model.DvsData;
import com.vmware.vsan.client.services.hci.model.EvcModeConfigData;
import com.vmware.vsan.client.services.hci.model.EvcModeData;
import com.vmware.vsan.client.services.hci.model.ExistingDvpgData;
import com.vmware.vsan.client.services.hci.model.ExistingDvsData;
import com.vmware.vsan.client.services.hci.model.HciWorkflowState;
import com.vmware.vsan.client.services.hci.model.HostAdapter;
import com.vmware.vsan.client.services.hci.model.HostInCluster;
import com.vmware.vsan.client.services.hci.model.QuickstartViewData;
import com.vmware.vsan.client.services.hci.model.Service;
import com.vmware.vsan.client.services.hci.model.ValidationData;
import com.vmware.vsan.client.services.hci.model.VlanData;
import com.vmware.vsan.client.services.hci.model.VlanType;
import com.vmware.vsan.client.services.hci.model.VsanClusterType;
import com.vmware.vsan.client.services.hci.model.VsanHealthCheck;
import com.vmware.vsan.client.services.vum.VumBaselineRecommendationService;
import com.vmware.vsan.client.util.StringUtil;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.EncryptionState;
import com.vmware.vsphere.client.vsan.data.VsanConfigSpec;
import com.vmware.vsphere.client.vsan.health.VsanHealthStatus;
import com.vmware.vsphere.client.vsan.health.VsanTestData;
import com.vmware.vsphere.client.vsan.health.util.VsanHealthUtil;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HciClusterService {
   private static final String VSAN_HIDE_CEIP_PAGE_IN_HCI_WOFKFLOW = "config.vsan.hide_ceip_page_in_hci_wofkflow";
   private static final String HOST_NICS_PROPERTY = "config.network.pnic";
   private static final String HOST_IS_WITNESS_PROPERTY = "isWitnessHost";
   private static final String SUPPORTED_EVC_MODE_PROPERTY = "supportedEvcMode";
   private static final String HOST_MAX_EVC_MODE_KEY_PROPERTY = "summary.maxEVCModeKey";
   private static final String DATACENTER_HOST_FOLDER_PROPERTY = "hostFolder";
   private static final String HCI_CONFIG_PROPERTY = "hciConfig";
   private static final String HA_PROPERTY = "configurationEx.dasConfig.enabled";
   private static final String HA_FAILOVER_LEVEL_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.failoverLevel";
   private static final String HA_HOST_MONITORING_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.hostMonitoring";
   private static final String HA_ADMISSION_CONTROL_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.admissionControlEnabled";
   private static final String HA_VM_MONITORING_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.vmMonitoring";
   private static final String DRS_PROPERTY = "configurationEx.drsConfig.enabled";
   private static final String DRS_AUTOMATION_LEVEL_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.defaultVmBehavior";
   private static final String DRS_MIGRATION_THRESHOLD_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.vmotionRate";
   private static final String VSAN_PROPERTY = "configurationEx.vsanConfigInfo.enabled";
   private static final String DVPG_VLAN_PROPERTY = "config.defaultPortConfig.vlan";
   private static final String CONFIGURE_HCI_DISABLED_METHOD = "configureHCI";
   private static final String EXTEND_HCI_DISABLED_METHOD = "extendHCI";
   private static final String VERSION_PROPERTY = "config.productInfo.version";
   private static final String DVS_HOST_PROPERTY = "config.host";
   private static final String NIOC_VERSION_PROPERTY = "lacpVersionColumnLabelDerived";
   private static final String LACP_VERSION_PROPERTY = "niocVersionColumnLabel";
   private static final String DVS_PORTGROUP_RELATION = "portgroup";
   private static final String DVPG_UPLINK_PROPERTY = "config.uplink";
   private static final int LARGE_SCALE_CLUSTER_SUPPORT_THRESHOLD = 32;
   private static final String[] BASIC_CLUSTER_CONFIG_PROPERTIES = new String[]{"host._length", "hciConfig", "configurationEx.drsConfig.enabled", "configurationEx.dasConfig.enabled", "configurationEx.vsanConfigInfo.enabled"};
   private static final String[] CLUSTER_CONFIG_PROPERTIES = new String[]{"configurationEx[@type='ClusterConfigInfoEx'].dasConfig.admissionControlEnabled", "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.failoverLevel", "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.hostMonitoring", "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.vmMonitoring", "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.defaultVmBehavior", "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.vmotionRate"};
   private static final String[] EXISTING_DVS_PROPERTIES = new String[]{"name", "config.productInfo.version", "lacpVersionColumnLabelDerived", "niocVersionColumnLabel", "config.host"};
   private static final String BASIC_CARD_ACTION_ID = "vsphere.core.cluster.actions.edit";
   private static final String ADD_HOSTS_CARD_ACTION_ID = "vsphere.core.hci.addHosts";
   private static final String CONFIGURE_CARD_ACTION_ID = "com.vmware.vsan.client.h5vsanui.cluster.configureHciCluster";
   private static final String OBJECT_NAME_SEPARATOR = " ";
   private static final String GENERAL_ENABLED = "enabled";
   private static final String VM_MONITORING_DISABLED = "vmMonitoringDisabled";
   private static final String DEFAULT_VLAN = "0";
   private static final int MAX_DVS = 3;
   private static final Log logger = LogFactory.getLog(HciClusterService.class);
   private static final VsanProfiler _profiler = new VsanProfiler(HciClusterService.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private ObjectReferenceService refService;
   @Autowired
   private TaskService taskService;
   @Autowired
   private PermissionService permissionService;
   @Autowired
   private EncryptionPropertyProvider encryptionPropertyProvider;
   @Autowired
   private CeipService ceipService;
   @Autowired
   private VumBaselineRecommendationService baselineRecommendationService;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$HciWorkflowState;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$Service;

   @TsService
   public QuickstartViewData getGettingStartedData(ManagedObjectReference clusterRef) throws Exception {
      QuickstartViewData result = new QuickstartViewData();
      BasicClusterConfigData basicClusterData = this.getBasicClusterConfigData(clusterRef);
      boolean hasEditClusterPermission = this.permissionService.hasPermissions(clusterRef, new String[]{"Host.Inventory.EditCluster"});
      this.populateQuickstartInfoContainer(result, basicClusterData, hasEditClusterPermission);
      result.configurationCards = new ConfigCardData[]{this.getBasicConfigCard(basicClusterData, hasEditClusterPermission), this.getAddHostsCard(basicClusterData, this.hasAddHostsPermissions(clusterRef, basicClusterData.vsanEnabled, hasEditClusterPermission)), this.getConfigureCard(basicClusterData, hasEditClusterPermission, clusterRef)};
      return result;
   }

   private void populateQuickstartInfoContainer(QuickstartViewData viewData, BasicClusterConfigData basicClusterData, boolean hasEditClusterPermission) {
      switch($SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$HciWorkflowState()[basicClusterData.hciWorkflowState.ordinal()]) {
      case 1:
         viewData.header = Utils.getLocalizedString("vsan.hci.gettingStarted.createWorkflow.header");
         viewData.text = Utils.getLocalizedString("vsan.hci.gettingStarted.createWorkflow.text");
         viewData.showSendFeedbackLink = false;
         break;
      case 2:
         if (basicClusterData.hciWorkflowState == HciWorkflowState.DONE) {
            if (basicClusterData.notConfiguredHosts == 0) {
               viewData.header = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.initial.header");
               viewData.text = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.initial.text");
               viewData.showSendFeedbackLink = true;
            } else {
               viewData.header = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.inProgress.header");
               viewData.text = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.inProgress.text");
               viewData.showSendFeedbackLink = false;
            }
         }
         break;
      case 3:
         viewData.header = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.abandoned.header");
         viewData.text = Utils.getLocalizedString("vsan.hci.gettingStarted.extendWorkflow.abandoned.text");
         viewData.showSendFeedbackLink = true;
         break;
      default:
         viewData.header = Utils.getLocalizedString("vsan.hci.gettingStarted.createWorkflow.header");
         viewData.text = Utils.getLocalizedString("vsan.hci.gettingStarted.createWorkflow.text");
         viewData.showSendFeedbackLink = false;
      }

      viewData.showCloseQuickstartButton = basicClusterData.hciWorkflowState == HciWorkflowState.IN_PROGRESS && hasEditClusterPermission;
      viewData.extendCard = basicClusterData.hosts > 0;
   }

   @TsService
   public BasicClusterConfigData getBasicClusterConfigData(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse response = QueryUtil.getProperties(clusterRef, BASIC_CLUSTER_CONFIG_PROPERTIES);
      BasicClusterConfigData result = new BasicClusterConfigData();
      this.populateBasicClusterConfigData(clusterRef, result, response);
      return result;
   }

   private void populateBasicClusterConfigData(ManagedObjectReference clusterRef, BasicClusterConfigData basicConfig, DataServiceResponse response) throws Exception {
      if (response != null) {
         PropertyValue[] propertyValues = response.getPropertyValues();
         if (propertyValues != null) {
            HCIConfigInfo hciConfigInfo = null;
            PropertyValue[] var9 = propertyValues;
            int var8 = propertyValues.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               PropertyValue propValue = var9[var7];
               if (propValue.propertyName.equals("host._length")) {
                  basicConfig.hosts = (Integer)propValue.value;
               } else if (propValue.propertyName.equals("configurationEx.drsConfig.enabled")) {
                  basicConfig.drsEnabled = (Boolean)propValue.value;
               } else if (propValue.propertyName.equals("configurationEx.dasConfig.enabled")) {
                  basicConfig.haEnabled = (Boolean)propValue.value;
               } else if (propValue.propertyName.equals("configurationEx.vsanConfigInfo.enabled")) {
                  basicConfig.vsanEnabled = (Boolean)propValue.value;
               } else if (propValue.propertyName.equals("hciConfig")) {
                  hciConfigInfo = (HCIConfigInfo)propValue.value;
               }
            }

            if (hciConfigInfo != null) {
               basicConfig.notConfiguredHosts = this.getNotConfiguredHostsCount(basicConfig.hosts, hciConfigInfo);
               basicConfig.hciWorkflowState = HciWorkflowState.fromString(hciConfigInfo.workflowState);
               if (basicConfig.hciWorkflowState == HciWorkflowState.DONE && basicConfig.notConfiguredHosts > 0) {
                  basicConfig.dvsDataByService = this.getDvsInfoData(hciConfigInfo);
               }
            } else {
               basicConfig.notConfiguredHosts = 0;
               basicConfig.hciWorkflowState = HciWorkflowState.NOT_IN_HCI_WORKFLOW;
            }
         }
      }

   }

   private int getNotConfiguredHostsCount(int hosts, HCIConfigInfo hciConfigInfo) {
      ManagedObjectReference[] configuredHosts = hciConfigInfo == null ? null : hciConfigInfo.configuredHosts;
      return configuredHosts == null ? hosts : hosts - configuredHosts.length;
   }

   private ManagedObjectReference[] getNotConfiguredHosts(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse response = QueryUtil.getProperties(clusterRef, new String[]{"host", "hciConfig"});
      ManagedObjectReference[] hosts = (ManagedObjectReference[])response.getProperty(clusterRef, "host");
      HCIConfigInfo hciConfigInfo = (HCIConfigInfo)response.getProperty(clusterRef, "hciConfig");
      ManagedObjectReference[] configuredHosts = hciConfigInfo == null ? null : hciConfigInfo.configuredHosts;
      ManagedObjectReference[] notConfiguredHosts = new ManagedObjectReference[0];
      if (hosts != null) {
         if (configuredHosts == null) {
            notConfiguredHosts = hosts;
         } else if (hosts.length == configuredHosts.length) {
            notConfiguredHosts = new ManagedObjectReference[0];
         } else {
            List<String> configuredHostsIds = new ArrayList();
            ManagedObjectReference[] var11 = configuredHosts;
            int var10 = configuredHosts.length;

            for(int var9 = 0; var9 < var10; ++var9) {
               ManagedObjectReference host = var11[var9];
               configuredHostsIds.add(host.getValue());
            }

            List<ManagedObjectReference> hostsList = new ArrayList();
            ManagedObjectReference[] var12 = hosts;
            int var15 = hosts.length;

            for(var10 = 0; var10 < var15; ++var10) {
               ManagedObjectReference host = var12[var10];
               if (!configuredHostsIds.contains(host.getValue())) {
                  hostsList.add(host);
               }
            }

            notConfiguredHosts = (ManagedObjectReference[])hostsList.toArray(new ManagedObjectReference[0]);
         }
      }

      return notConfiguredHosts;
   }

   @TsService
   public List<HostInCluster> getClusterHosts(ManagedObjectReference clusterRef) throws Exception {
      List<HostInCluster> result = new ArrayList();
      PropertyValue[] hostNameValues = QueryUtil.getPropertyForRelatedObjects(clusterRef, "host", ClusterComputeResource.class.getSimpleName(), "name").getPropertyValues();
      PropertyValue[] var7 = hostNameValues;
      int var6 = hostNameValues.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue nameValue = var7[var5];
         result.add(HostInCluster.create((ManagedObjectReference)nameValue.resourceObject, this.refService.getUid(nameValue.resourceObject), (String)nameValue.value));
      }

      return result;
   }

   @TsService
   public List<HostInCluster> getNotConfiguredClusterHosts(ManagedObjectReference clusterRef) throws Exception {
      List<HostInCluster> result = new ArrayList();
      ManagedObjectReference[] notConfiguredHosts = this.getNotConfiguredHosts(clusterRef);
      PropertyValue[] hostNameValues = QueryUtil.getProperties(notConfiguredHosts, new String[]{"name"}).getPropertyValues();
      PropertyValue[] var8 = hostNameValues;
      int var7 = hostNameValues.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         PropertyValue nameValue = var8[var6];
         result.add(HostInCluster.create((ManagedObjectReference)nameValue.resourceObject, this.refService.getUid(nameValue.resourceObject), (String)nameValue.value));
      }

      Collections.sort(result, new Comparator<HostInCluster>() {
         public int compare(HostInCluster host1, HostInCluster host2) {
            if (host1 != null && !StringUtils.isEmpty(host1.name) || host2 != null && !StringUtils.isEmpty(host2.name)) {
               if (host1 != null && !StringUtils.isEmpty(host1.name)) {
                  return host2 != null && !StringUtils.isEmpty(host2.name) ? host1.name.compareTo(host2.name) : -1;
               } else {
                  return 1;
               }
            } else {
               return 0;
            }
         }
      });
      return result;
   }

   @TsService
   public ConfigCardData validateNotConfiguredHosts(ManagedObjectReference clusterRef) throws Exception {
      boolean hasEditClusterPermission = this.permissionService.hasPermissions(clusterRef, new String[]{"Host.Inventory.EditCluster"});
      BasicClusterConfigData basicClusterConfigData = this.getBasicClusterConfigData(clusterRef);
      ConfigCardData result = this.getBasicAddHostsCard();
      result.contentHeader = this.getHostsNumLabel(basicClusterConfigData);
      result.validateEnabled = true;
      result.enabled = this.hasAddHostsPermissions(clusterRef, basicClusterConfigData.vsanEnabled, hasEditClusterPermission);
      this.populateHealthChecksResult(result, clusterRef, VsanHealthPerspective.beforeConfigureHost.toString(), false);
      return result;
   }

   private ConfigCardData getBasicAddHostsCard() {
      return new ConfigCardData(Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.title"), "vsphere.core.hci.addHosts", true, false, Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.launchButton.text"));
   }

   private ConfigCardData getBasicConfigureCard() {
      return new ConfigCardData(Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.title"), "com.vmware.vsan.client.h5vsanui.cluster.configureHciCluster", true, false, Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.launchButton.text"));
   }

   @TsService
   public ConfigCardData validateCluster(ManagedObjectReference clusterRef) throws Exception {
      BasicClusterConfigData basicClusterConfigData = this.getBasicClusterConfigData(clusterRef);
      ConfigCardData result = this.getBasicConfigureCard();
      result.enabled = basicClusterConfigData.notConfiguredHosts > 0;
      if (basicClusterConfigData.hciWorkflowState.equals(HciWorkflowState.DONE)) {
         result.title = Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.titleInExtend");
         result.validateEnabled = basicClusterConfigData.hosts > 0 && basicClusterConfigData.notConfiguredHosts == 0;
      }

      this.populateHealthChecksResult(result, clusterRef, VsanHealthPerspective.defaultView.toString(), true);
      return result;
   }

   private void populateHealthChecksResult(ConfigCardData card, ManagedObjectReference clusterRef, String perspective, Boolean showGroupsOnly) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      String[] requiredFields = new String[]{"groups"};
      VsanClusterHealthSummary healthSummary = null;
      Throwable var8 = null;
      VsanClusterHealthGroup group = null;

      try {
         VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterHealthSummary");

         try {
            healthSummary = healthSystem.queryClusterHealthSummary(clusterRef, (Integer)null, (String[])null, false, requiredFields, false, perspective, (ManagedObjectReference[])null, (Boolean)null);
         } finally {
            if (point != null) {
               point.close();
            }

         }
      } catch (Throwable var23) {
         if (var8 == null) {
            var8 = var23;
         } else if (var8 != var23) {
            var8.addSuppressed(var23);
         }

         throw var8;
      }

      List<VsanHealthCheck> healthChecks = new ArrayList();
      if (healthSummary != null && healthSummary.groups != null) {
         VsanClusterHealthGroup[] var12;
         int var11 = (var12 = healthSummary.groups).length;

         for(int var26 = 0; var26 < var11; ++var26) {
            group = var12[var26];
            if (showGroupsOnly) {
               VsanHealthCheck healthCheck = new VsanHealthCheck(perspective, group.groupName, (String)null, group.groupName, group.groupHealth);
               healthChecks.add(healthCheck);
            } else if (group.groupTests != null) {
               VsanClusterHealthTest[] var16;
               int var15 = (var16 = group.groupTests).length;

               for(int var14 = 0; var14 < var15; ++var14) {
                  VsanClusterHealthTest test = var16[var14];
                  VsanHealthCheck healthCheck = new VsanHealthCheck(perspective, group.groupName, test.testName, test.testName, test.testHealth);
                  healthChecks.add(healthCheck);
               }
            }
         }
      }

      boolean vsanEnabled = (Boolean)QueryUtil.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled", (Object)null);
      card.validationData = new ValidationData(healthChecks, vsanEnabled);
      VsanHealthStatus overallStatus = VsanHealthStatus.valueOf(healthSummary.overallHealth);
      card.status = overallStatus == VsanHealthStatus.red ? ConfigCardData.Status.ERROR : ConfigCardData.Status.PASSED;
   }

   private ConfigCardData getBasicConfigCard(BasicClusterConfigData basicClusterConfigData, boolean hasPermissions) throws Exception {
      ConfigCardData result = new ConfigCardData(Utils.getLocalizedString("vsan.hci.gettingStarted.basicConfigCard.title"), "vsphere.core.cluster.actions.edit", false, false, Utils.getLocalizedString("vsan.hci.gettingStarted.basicConfigCard.launchButton.text"));
      result.enabled = basicClusterConfigData.hciWorkflowState == HciWorkflowState.IN_PROGRESS && hasPermissions;
      result.status = ConfigCardData.Status.PASSED;
      result.listItems = this.getEnabledServices(basicClusterConfigData);
      result.contentHeader = Utils.getLocalizedString(result.listItems.size() > 0 ? "vsan.hci.gettingStarted.basicConfigCard.contentHeader" : "vsan.hci.gettingStarted.basicConfigCard.contentHeader.noServices");
      return result;
   }

   private List<String> getEnabledServices(BasicClusterConfigData basicClusterConfigData) throws Exception {
      List<String> result = new ArrayList();
      if (basicClusterConfigData.drsEnabled) {
         result.add(Utils.getLocalizedString("vsan.hci.gettingStarted.basicConfigCard.services.drs"));
      }

      if (basicClusterConfigData.haEnabled) {
         result.add(Utils.getLocalizedString("vsan.hci.gettingStarted.basicConfigCard.services.ha"));
      }

      if (basicClusterConfigData.vsanEnabled) {
         result.add(Utils.getLocalizedString("vsan.hci.gettingStarted.basicConfigCard.services.vsan"));
      }

      return result;
   }

   private ConfigCardData getAddHostsCard(BasicClusterConfigData basicClusterConfigData, boolean hasPermissions) throws Exception {
      ConfigCardData result = this.getBasicAddHostsCard();
      result.enabled = hasPermissions;
      result.validateEnabled = basicClusterConfigData.hosts > 0 && basicClusterConfigData.notConfiguredHosts > 0 && basicClusterConfigData.hciWorkflowState != HciWorkflowState.INVALID;
      result.nextStep = !result.validateEnabled;
      if (basicClusterConfigData.hosts == 0) {
         result.contentText = Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.contentText");
         result.status = ConfigCardData.Status.NOT_AVAILABLE;
      } else {
         result.contentHeader = this.getHostsNumLabel(basicClusterConfigData);
         result.status = ConfigCardData.Status.PASSED;
      }

      return result;
   }

   private String getHostsNumLabel(BasicClusterConfigData basicClusterConfigData) {
      if (basicClusterConfigData.notConfiguredHosts != 0) {
         return basicClusterConfigData.notConfiguredHosts == basicClusterConfigData.hosts ? Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.notConfiguredHostsInTheCluster", String.valueOf(basicClusterConfigData.notConfiguredHosts)) : Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.hostsInTheClusterByType", String.valueOf(basicClusterConfigData.hosts), String.valueOf(basicClusterConfigData.notConfiguredHosts));
      } else {
         return Utils.getLocalizedString("vsan.hci.gettingStarted.addHostsCard.hostsInTheCluster", String.valueOf(basicClusterConfigData.hosts));
      }
   }

   private ConfigCardData getConfigureCard(BasicClusterConfigData basicClusterConfigData, boolean hasEditClusterPermission, ManagedObjectReference clusterRef) throws Exception {
      ConfigCardData result = this.getBasicConfigureCard();
      result.contentText = this.getConfigureCardContentText(basicClusterConfigData);
      result.operationInProgress = this.isConfigureOperationInProgress(clusterRef);
      result.enabled = !result.operationInProgress && this.isConfigureCardEnabled(basicClusterConfigData, hasEditClusterPermission);
      result.nextStep = result.enabled;
      result.contentHeader = this.getNotConfiguredHostsLabel(basicClusterConfigData);
      result.status = ConfigCardData.Status.NOT_AVAILABLE;
      result.validateEnabled = basicClusterConfigData.hosts > 0 && basicClusterConfigData.notConfiguredHosts == 0 && basicClusterConfigData.hciWorkflowState != HciWorkflowState.NOT_IN_HCI_WORKFLOW;
      if (basicClusterConfigData.hciWorkflowState.equals(HciWorkflowState.DONE)) {
         result.title = Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.titleInExtend");
      }

      return result;
   }

   private boolean isConfigureCardEnabled(BasicClusterConfigData basicClusterConfigData, boolean hasPermission) {
      return basicClusterConfigData.notConfiguredHosts > 0 && !basicClusterConfigData.hciWorkflowState.equals(HciWorkflowState.INVALID) && hasPermission;
   }

   @TsService
   public boolean isConfigureOperationInProgress(ManagedObjectReference clusterRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

         try {
            AuthorizationManager authorizationManager = (AuthorizationManager)vcConnection.createStub(AuthorizationManager.class, vcConnection.getContent().getAuthorizationManager());
            DisabledMethodInfo[] disabledMethods = authorizationManager.queryDisabledMethods(clusterRef);
            if (ArrayUtils.isNotEmpty(disabledMethods)) {
               DisabledMethodInfo[] var10 = disabledMethods;
               int var9 = disabledMethods.length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  DisabledMethodInfo info = var10[var8];
                  if (info.method != null && StringUtils.isNotEmpty(info.method.getName()) && ("configureHCI".equals(info.method.getName()) || "extendHCI".equals(info.method.getName()))) {
                     return true;
                  }
               }
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }

         return false;
      } catch (Throwable var16) {
         if (var2 == null) {
            var2 = var16;
         } else if (var2 != var16) {
            var2.addSuppressed(var16);
         }

         throw var2;
      }
   }

   private String getNotConfiguredHostsLabel(BasicClusterConfigData configData) {
      if (configData.hciWorkflowState == HciWorkflowState.DONE && configData.notConfiguredHosts > 0) {
         return configData.notConfiguredHosts == 1 ? Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.notConfiguredHostText") : Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.notConfiguredHostsText", String.valueOf(configData.notConfiguredHosts));
      } else {
         return null;
      }
   }

   @TsService
   public EvcModeConfigData getEvcModeConfigData(ManagedObjectReference clusterRef) throws Exception {
      EvcModeConfigData data = new EvcModeConfigData();
      PropertyValue[] hostProps = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", HostSystem.class.getSimpleName(), new String[]{"summary.maxEVCModeKey"}).getPropertyValues();
      EVCMode[] evcModes = (EVCMode[])QueryUtil.getProperty(clusterRef, "supportedEvcMode", (Object)null);
      if (!ArrayUtils.isEmpty(evcModes) && !ArrayUtils.isEmpty(hostProps)) {
         List<EvcModeData> supportedAmdEvcMode = new ArrayList();
         List<EvcModeData> supportedIntelEvcMode = new ArrayList();
         EVCMode[] var10 = evcModes;
         int var9 = evcModes.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            EVCMode evcMode = var10[var8];
            EvcModeData modeData = new EvcModeData();
            modeData.id = evcMode.key;
            modeData.label = evcMode.label;
            if (Vendor.amd.name().equals(evcMode.vendor)) {
               supportedAmdEvcMode.add(modeData);
            } else if (Vendor.intel.name().equals(evcMode.vendor)) {
               supportedIntelEvcMode.add(modeData);
            } else {
               logger.warn("Unsupported vendor: " + evcMode.vendor);
            }
         }

         List<Integer> intelSupportedIndex = new ArrayList();
         List<Integer> amdSupportedIndex = new ArrayList();
         PropertyValue[] var12 = hostProps;
         int var20 = hostProps.length;

         for(int var19 = 0; var19 < var20; ++var19) {
            PropertyValue propValue = var12[var19];
            String hostEvcMode = (String)propValue.value;
            if (StringUtils.isEmpty(hostEvcMode)) {
               break;
            }

            int i;
            if (hostEvcMode.contains(Vendor.amd.name())) {
               for(i = 0; i < supportedAmdEvcMode.size(); ++i) {
                  if (hostEvcMode.equals(((EvcModeData)supportedAmdEvcMode.get(i)).id)) {
                     amdSupportedIndex.add(i);
                     break;
                  }
               }
            }

            if (hostEvcMode.contains(Vendor.intel.name())) {
               for(i = 0; i < supportedIntelEvcMode.size(); ++i) {
                  if (hostEvcMode.equals(((EvcModeData)supportedIntelEvcMode.get(i)).id)) {
                     intelSupportedIndex.add(i);
                     break;
                  }
               }
            }
         }

         if ((intelSupportedIndex.size() == 0 || amdSupportedIndex.size() == 0) && (intelSupportedIndex.size() != 0 || amdSupportedIndex.size() != 0)) {
            Integer intelSupportedLength;
            if (intelSupportedIndex.size() != 0) {
               intelSupportedLength = (Integer)Collections.min(intelSupportedIndex) + 1;
               data.supportedIntelEvcMode = supportedIntelEvcMode.subList(0, intelSupportedLength);
            } else {
               intelSupportedLength = (Integer)Collections.min(amdSupportedIndex) + 1;
               data.supportedAmdEvcMode = supportedAmdEvcMode.subList(0, intelSupportedLength);
            }

            return data;
         } else {
            data.unsupportedEvcStatus = true;
            return data;
         }
      } else {
         return data;
      }
   }

   @TsService
   public ClusterConfigData getClusterConfigData(ManagedObjectReference clusterRef) throws Exception {
      String[] properties = (String[])ArrayUtils.addAll(BASIC_CLUSTER_CONFIG_PROPERTIES, CLUSTER_CONFIG_PROPERTIES);
      DataServiceResponse response = QueryUtil.getProperties(clusterRef, properties);
      ClusterConfigData configData = new ClusterConfigData();
      configData.basicConfig = new BasicClusterConfigData();
      this.populateBasicClusterConfigData(clusterRef, configData.basicConfig, response);
      configData.enableAdmissionControl = (Boolean)response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.admissionControlEnabled");
      configData.hostFTT = (Integer)response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.failoverLevel");
      String vmMonitorStr = (String)response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.vmMonitoring");
      configData.enableVmMonitoring = !"vmMonitoringDisabled".equals(vmMonitorStr);
      String hostMonitorStr = (String)response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].dasConfig.hostMonitoring");
      configData.enableHostMonitoring = "enabled".equals(hostMonitorStr);
      configData.automationLevel = DrsAutoLevel.fromString(response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.defaultVmBehavior").toString());
      configData.migrationThreshold = (Integer)response.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].drsConfig.vmotionRate");
      configData.vsanConfigSpec = this.getVsanConfigSpec(clusterRef);
      if (configData.basicConfig.vsanEnabled && VsanCapabilityUtils.isVumBaselineRecommendationSupportedOnVc(clusterRef)) {
         configData.vumBaselineRecommendationType = this.baselineRecommendationService.getClusterVumBaselineRecommendation(clusterRef);
      }

      return configData;
   }

   @TsService
   public Object getEvcModeValidationResult(ManagedObjectReference clusterRef, String evcModeKey) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

         Throwable var10000;
         label173: {
            boolean var10001;
            Object var21;
            try {
               ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
               EVCManager evcManager = (EVCManager)vcConnection.createStub(EVCManager.class, cluster.evcManager());
               ManagedObjectReference task = evcManager.checkConfigureEvc(evcModeKey);
               task.setServerGuid(clusterRef.getServerGuid());
               var21 = this.taskService.getResult(task);
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var21;
            } catch (Throwable var18) {
               var10000 = var18;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var3;
      } catch (Throwable var20) {
         if (var3 == null) {
            var3 = var20;
         } else if (var3 != var20) {
            var3.addSuppressed(var20);
         }

         throw var3;
      }
   }

   @TsService("configureHciClusterTask")
   public ManagedObjectReference configureCluster(ManagedObjectReference param1, ClusterConfigData param2) throws Exception {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference simpleClusterExtend(ManagedObjectReference clusterRef) throws Exception {
      ClusterConfigData clusterConfigData = this.getClusterConfigData(clusterRef);
      VcConnection vcConnection = this.getVcConnection(clusterRef, clusterConfigData.basicConfig.vsanEnabled);
      return this.extendWorkflow(vcConnection, clusterRef, clusterConfigData, this.getNotConfiguredClusterHosts(clusterRef));
   }

   @TsService
   public void abandonHciWorkflowCluster(ManagedObjectReference clusterRef) throws Exception {
      VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());
      ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
      if (cluster.getHciConfig() != null && HciWorkflowState.IN_PROGRESS == HciWorkflowState.fromString(cluster.getHciConfig().workflowState)) {
         cluster.AbandonHciWorkflow();
      }

   }

   private VcConnection getVcConnection(ManagedObjectReference clusterRef, boolean vsan) {
      return vsan ? this.vcClient.getVsanVmodlVersionConnection(clusterRef.getServerGuid()) : this.vcClient.getConnection(clusterRef.getServerGuid());
   }

   private ManagedObjectReference createWorkflow(VcConnection vcConnection, ManagedObjectReference clusterRef, ClusterConfigData clusterConfigData, List<HostInCluster> hosts) throws Exception {
      ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
      boolean hasEncryptionPermissions = this.encryptionPropertyProvider.getEncryptionPermissions(clusterRef);
      HCIConfigSpec hciConfigSpec = clusterConfigData.getHciConfigSpec(clusterRef, hasEncryptionPermissions);
      HostConfigurationInput[] hostConfigurationInputs = clusterConfigData.getHostConfigurationInputs(hosts);
      ManagedObjectReference taskRef = cluster.configureHCI(hciConfigSpec, hostConfigurationInputs);
      taskRef.setServerGuid(clusterRef.getServerGuid());
      return taskRef;
   }

   private ManagedObjectReference extendWorkflow(VcConnection vcConnection, ManagedObjectReference clusterRef, ClusterConfigData clusterConfigData, List<HostInCluster> hosts) throws Exception {
      ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
      HostConfigurationInput[] hostConfigurationInputs = clusterConfigData.getHostConfigurationInputs(hosts);
      SDDCBase reconfigSpec = null;
      if (clusterConfigData.basicConfig.vsanEnabled) {
         if (clusterConfigData.vsanConfigSpec.stretchedClusterConfig != null && clusterConfigData.vsanConfigSpec.stretchedClusterConfig.witnessHost == null) {
            clusterConfigData.vsanConfigSpec.stretchedClusterConfig.witnessHost = this.getStretchedClusterWitnessHost(clusterRef);
         }

         reconfigSpec = clusterConfigData.vsanConfigSpec.getBasicReconfigSpec();
      }

      ManagedObjectReference taskRef = cluster.extendHCI(hostConfigurationInputs, reconfigSpec);
      taskRef.setServerGuid(clusterRef.getServerGuid());
      return taskRef;
   }

   private ManagedObjectReference getStretchedClusterWitnessHost(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse propertyResponse = QueryUtil.getPropertyForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), "isWitnessHost");
      PropertyValue[] propertyValues = propertyResponse.getPropertyValues();
      PropertyValue[] var7 = propertyValues;
      int var6 = propertyValues.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue value = var7[var5];
         if (value.propertyName.equals("isWitnessHost")) {
            boolean isWitnessHost = (Boolean)value.value;
            if (isWitnessHost) {
               return (ManagedObjectReference)value.resourceObject;
            }
         }
      }

      return null;
   }

   @TsService
   public List<HostAdapter> getPhysicalAdapters(ManagedObjectReference clusterRef) throws Exception {
      DataServiceResponse response = QueryUtil.getPropertiesForRelatedObjects(clusterRef, "host", ClusterComputeResource.class.getSimpleName(), new String[]{"config.network.pnic"});
      PropertyValue[] propertyValues = response.getPropertyValues();
      int nicCount = this.getMaxCommonNicCount(propertyValues);
      List<String> names = this.getFirstNDeviceNames(propertyValues, nicCount);
      Collections.sort(names);
      return this.generateNHostAdapters(names, nicCount);
   }

   private int getMaxCommonNicCount(PropertyValue[] propertyValues) {
      int nicCount = Integer.MAX_VALUE;
      if (propertyValues.length != 0) {
         PropertyValue[] var6 = propertyValues;
         int var5 = propertyValues.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            PropertyValue propertyValue = var6[var4];
            PhysicalNic[] nics = (PhysicalNic[])propertyValue.value;
            nicCount = Math.min(nicCount, nics.length);
         }
      } else {
         nicCount = 0;
      }

      return nicCount;
   }

   private List<String> getFirstNDeviceNames(PropertyValue[] hostPropertyValues, int number) {
      List<String> result = new ArrayList();
      Map<String, Integer> pnicNamesToHostCount = new HashMap();
      PropertyValue[] var8 = hostPropertyValues;
      int var7 = hostPropertyValues.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         PropertyValue propertyValue = var8[var6];
         PhysicalNic[] physicalNics = (PhysicalNic[])propertyValue.value;
         PhysicalNic[] var13 = physicalNics;
         int var12 = physicalNics.length;

         for(int var11 = 0; var11 < var12; ++var11) {
            PhysicalNic physicalNic = var13[var11];
            String pnicName = physicalNic.device;
            Integer hostCount = 1;
            if (pnicNamesToHostCount.containsKey(pnicName)) {
               hostCount = (Integer)pnicNamesToHostCount.get(pnicName) + 1;
            }

            pnicNamesToHostCount.put(pnicName, hostCount);
         }
      }

      Iterator var17 = pnicNamesToHostCount.entrySet().iterator();

      while(var17.hasNext()) {
         Entry<String, Integer> pnicNameToHostCount = (Entry)var17.next();
         if ((Integer)pnicNameToHostCount.getValue() == hostPropertyValues.length) {
            result.add((String)pnicNameToHostCount.getKey());
         }

         if (result.size() == number) {
            break;
         }
      }

      return result;
   }

   private List<HostAdapter> generateNHostAdapters(List<String> names, int number) {
      List<HostAdapter> result = new ArrayList(number);
      if (names.size() != number) {
         logger.warn("Inconsistent physical adapter naming across the hosts is found. Only suitable physical adapters are shown.");
      }

      for(int i = 0; i < names.size(); ++i) {
         result.add(HostAdapter.create(Utils.getLocalizedString("vsan.hci.configureCluster.longAdapterNamePattern", String.valueOf(i), (String)names.get(i)), (String)names.get(i)));
      }

      return result;
   }

   @TsService
   public List<String> getUniqueNewDvsNames(ManagedObjectReference clusterRef) throws Exception {
      List<String> dvsNames = this.getExistingDvsNames(clusterRef);
      List<String> result = new ArrayList();

      for(int i = 0; i < 3; ++i) {
         String newName = StringUtil.getIndexedString(dvsNames, Utils.getLocalizedString("vsan.hci.configureCluster.dvs.defaultName"), " ");
         dvsNames.add(newName);
         result.add(newName);
      }

      return result;
   }

   @TsService
   public List<String> getExistingDvsNames(ManagedObjectReference clusterRef) throws Exception {
      PropertyConstraint id = QueryUtil.createPropertyConstraint(VmwareDistributedVirtualSwitch.class.getSimpleName(), "serverGuid", com.vmware.vise.data.query.Comparator.EQUALS, clusterRef.getServerGuid());
      String[] properties = new String[]{"name"};
      ResultSet resultSet = QueryUtil.getData(QueryUtil.buildQuerySpec((Constraint)id, properties));
      DataServiceResponse response = QueryUtil.getDataServiceResponse(resultSet, properties);
      ArrayList<String> dvsNames = new ArrayList();
      PropertyValue[] var10;
      int var9 = (var10 = response.getPropertyValues()).length;

      for(int var8 = 0; var8 < var9; ++var8) {
         PropertyValue propertyValue = var10[var8];
         dvsNames.add((String)propertyValue.value);
      }

      return dvsNames;
   }

   private VsanConfigSpec getVsanConfigSpec(ManagedObjectReference clusterRef) throws Exception {
      VsanConfigSpec vsanConfigSpec = new VsanConfigSpec();
      List<String> propertiesToRequest = new ArrayList();
      vsanConfigSpec.largeScaleClusterSupport = this.getLargeScaleClusterSupport(clusterRef);
      if (VsanCapabilityUtils.isDeduplicationAndCompressionSupportedOnVc(clusterRef) && VsanCapabilityUtils.isDeduplicationAndCompressionSupported(clusterRef)) {
         propertiesToRequest.add("dataEfficiencyStatus");
      }

      if (VsanCapabilityUtils.isEncryptionSupportedOnVc(clusterRef) && VsanCapabilityUtils.isEncryptionSupported(clusterRef)) {
         propertiesToRequest.add("vsanEncryptionStatus");
      }

      if (CollectionUtils.isEmpty(propertiesToRequest)) {
         return vsanConfigSpec;
      } else {
         PropertyValue[] propertyValues = QueryUtil.getProperties(clusterRef, (String[])propertiesToRequest.toArray(new String[0])).getPropertyValues();
         PropertyValue[] var8 = propertyValues;
         int var7 = propertyValues.length;

         for(int var6 = 0; var6 < var7; ++var6) {
            PropertyValue propertyValue = var8[var6];
            if ("dataEfficiencyStatus".equals(propertyValue.propertyName)) {
               vsanConfigSpec.enabledDedup = (Boolean)propertyValue.value;
            } else if ("vsanEncryptionStatus".equals(propertyValue.propertyName)) {
               EncryptionStatus config = (EncryptionStatus)propertyValue.value;
               if (config != null && config.state != null) {
                  switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState()[config.state.ordinal()]) {
                  case 1:
                     vsanConfigSpec.kmipClusterId = config.kmipClusterId;
                  case 3:
                     vsanConfigSpec.enableEncryption = true;
                     break;
                  case 2:
                  default:
                     vsanConfigSpec.enableEncryption = false;
                  }
               } else {
                  vsanConfigSpec.enableEncryption = false;
               }
            }
         }

         return vsanConfigSpec;
      }
   }

   @TsService
   public ConfigureWizardData getConfigureWizardData(ManagedObjectReference clusterRef) throws Exception {
      ConfigureWizardData result = new ConfigureWizardData();
      result.isStandalone = false;
      BasicClusterConfigData basicClusterConfigData = this.getBasicClusterConfigData(clusterRef);
      if (basicClusterConfigData.hciWorkflowState == HciWorkflowState.IN_PROGRESS) {
         result.isExtend = false;
         result.openYesNoDialog = false;
         result.openWarningDialog = false;
         boolean hasNetPermissions = this.hasHostConfigurePermissions(clusterRef);
         result.optOutOfNetworking = !hasNetPermissions;
         result.optOutOfNetworkingDisabled = !hasNetPermissions;
         result.enableFaultDomainForSingleSiteCluster = false;
         result.showDvsPage = true;
         result.showVmotionTrafficPage = basicClusterConfigData.drsEnabled;
         result.showVsanTrafficPage = basicClusterConfigData.vsanEnabled;
         result.showAdvancedOptionsPage = true;
         result.showClaimDisksPage = basicClusterConfigData.vsanEnabled;
         result.selectedVsanClusterType = basicClusterConfigData.vsanEnabled ? VsanClusterType.SINGLE_SITE_CLUSTER : VsanClusterType.NO_VSAN;
         result.showFaultDomainsPageComponent = false;
         result.showSingleSiteFaultDomainsPage = false;
         result.showWitnessHostPageComponent = false;
         result.showClaimDisksWitnessHostPage = false;
         result.isSupportInsightStepHidden = this.isHideSupportInsightStepConfigured(clusterRef);
         result.ceipEnabled = this.ceipService.getCeipServiceEnabled(clusterRef);
         if (basicClusterConfigData.hosts > 32) {
            result.largeScaleClusterSupport = true;
         } else {
            result.largeScaleClusterSupport = this.getLargeScaleClusterSupport(clusterRef);
         }
      } else {
         result.isExtend = true;
         result.optOutOfNetworking = true;
         HCIConfigInfo hciConfig = (HCIConfigInfo)QueryUtil.getProperty(clusterRef, "hciConfig", (Object)null);
         boolean drsEnabledInCreate = false;
         boolean vsanEnabledInCreate = false;
         if (hciConfig != null) {
            DVSSetting[] dvsSettings = hciConfig.dvsSetting;
            if (dvsSettings != null) {
               result.optOutOfNetworking = false;
               DVSSetting[] var11 = dvsSettings;
               int var10 = dvsSettings.length;

               for(int var9 = 0; var9 < var10; ++var9) {
                  DVSSetting dvsSetting = var11[var9];
                  DVPortgroupToServiceMapping[] mappings = dvsSetting.dvPortgroupSetting;
                  if (mappings != null) {
                     DVPortgroupToServiceMapping[] var16 = mappings;
                     int var15 = mappings.length;

                     for(int var14 = 0; var14 < var15; ++var14) {
                        DVPortgroupToServiceMapping mapping = var16[var14];
                        Service service = Service.fromString(mapping.service);
                        switch($SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$Service()[service.ordinal()]) {
                        case 2:
                           drsEnabledInCreate = true;
                           break;
                        case 3:
                           vsanEnabledInCreate = true;
                        }
                     }
                  }
               }
            }
         }

         String[] networkValidationMessages = null;
         if (!result.optOutOfNetworking) {
            networkValidationMessages = this.getNetworkValidationMessages(clusterRef, (DvsProfile[])null, this.getNotConfiguredHosts(clusterRef), basicClusterConfigData.vsanEnabled);
         }

         if (!result.optOutOfNetworking && !this.hasExtendNetworkingPermissions(clusterRef, hciConfig)) {
            result.openWarningDialog = true;
            result.dialogText = Utils.getLocalizedString("vsan.hci.dialog.configureHostsWarning.title");
            return result;
         }

         if (ArrayUtils.isNotEmpty(networkValidationMessages)) {
            result.openWarningDialog = true;
            result.dialogText = Utils.getLocalizedString("vsan.hci.dialog.configureHostsWarning.networkConfigurationError.title");
            result.warningDialogContent = networkValidationMessages;
            return result;
         }

         result.showDvsPage = false;
         result.showVmotionTrafficPage = drsEnabledInCreate;
         result.showVsanTrafficPage = vsanEnabledInCreate;
         result.showAdvancedOptionsPage = false;
         result.showClaimDisksPage = basicClusterConfigData.vsanEnabled;
         if (basicClusterConfigData.vsanEnabled) {
            result.selectedVsanClusterType = this.getVsanClusterType(clusterRef);
            result.showFaultDomainsPageComponent = result.selectedVsanClusterType == VsanClusterType.STRETCHED_CLUSTER;
            result.showSingleSiteFaultDomainsPage = result.selectedVsanClusterType == VsanClusterType.SINGLE_SITE_CLUSTER;
         } else {
            result.selectedVsanClusterType = VsanClusterType.NO_VSAN;
            result.showFaultDomainsPageComponent = false;
            result.showSingleSiteFaultDomainsPage = false;
            if (result.optOutOfNetworking) {
               result.openYesNoDialog = true;
               result.dialogText = Utils.getLocalizedString("vsan.hci.dialog.configureHostsConfirmation.title");
            }
         }

         result.showWitnessHostPageComponent = false;
         result.showClaimDisksWitnessHostPage = false;
      }

      return result;
   }

   @TsService
   public boolean hasNetworkingModifyPermissions(ManagedObjectReference[] dvSwitches, ManagedObjectReference[] dvPortgroups) throws Exception {
      boolean hasDvsCreatePermission = true;
      boolean hasDvpgCreatePermission = true;
      if (ArrayUtils.isNotEmpty(dvSwitches)) {
         hasDvsCreatePermission = this.permissionService.havePermissions(dvSwitches, new String[]{"DVSwitch.HostOp"});
      }

      if (ArrayUtils.isNotEmpty(dvPortgroups)) {
         hasDvpgCreatePermission = this.permissionService.havePermissions(dvPortgroups, new String[]{"Network.Assign"});
      }

      return hasDvsCreatePermission && hasDvpgCreatePermission;
   }

   @TsService
   public boolean hasNetworkingCreatePermissions(ManagedObjectReference clusterRef, boolean checkDvsCreatePermission, boolean checkDvpgCreatePermission) throws Exception {
      ManagedObjectReference datacenter = (ManagedObjectReference)QueryUtil.getProperty(clusterRef, "dc", (Object)null);
      List<String> permissionsToCheck = new ArrayList();
      if (checkDvsCreatePermission) {
         permissionsToCheck.add("DVSwitch.Create");
      }

      if (checkDvpgCreatePermission) {
         permissionsToCheck.add("DVPortgroup.Create");
      }

      return this.permissionService.hasPermissions(datacenter, (String[])permissionsToCheck.toArray(new String[0]));
   }

   private boolean getLargeScaleClusterSupport(ManagedObjectReference clusterRef) throws Exception {
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx configInfoEx = vsanConfigSystem.getConfigInfoEx(clusterRef);
      VsanExtendedConfig originalExtendedConfig = configInfoEx.getExtendedConfig();
      return originalExtendedConfig.largeScaleClusterSupport;
   }

   private boolean hasHostConfigurePermissions(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference[] hosts = (ManagedObjectReference[])QueryUtil.getProperty(clusterRef, "host", (Object)null);
      boolean hasHostNetworkConfig = true;
      ManagedObjectReference[] var7 = hosts;
      int var6 = hosts.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference host = var7[var5];
         if (!this.permissionService.hasPermissions(host, new String[]{"Host.Config.Network"})) {
            hasHostNetworkConfig = false;
            break;
         }
      }

      return hasHostNetworkConfig;
   }

   private boolean hasExtendNetworkingPermissions(ManagedObjectReference clusterRef, HCIConfigInfo hciConfig) throws Exception {
      boolean hasDvsModify = true;
      if (hciConfig.dvsSetting != null) {
         DVSSetting[] var7;
         int var6 = (var7 = hciConfig.dvsSetting).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            DVSSetting dvsSetting = var7[var5];
            if (!this.permissionService.hasPermissions(dvsSetting.dvSwitch, new String[]{"DVSwitch.Modify"})) {
               hasDvsModify = false;
               break;
            }
         }
      }

      List<ManagedObjectReference> dvPortgroups = this.getDvPortgroups(hciConfig);
      boolean hasNetworkAssign = true;
      Iterator var16 = dvPortgroups.iterator();

      while(var16.hasNext()) {
         ManagedObjectReference dvPortgroup = (ManagedObjectReference)var16.next();
         if (!this.permissionService.hasPermissions(dvPortgroup, new String[]{"Network.Assign"})) {
            hasNetworkAssign = false;
            break;
         }
      }

      ManagedObjectReference[] notConfiguredHosts = this.getNotConfiguredHosts(clusterRef);
      boolean hasHostNetworkConfig = true;
      ManagedObjectReference[] var11 = notConfiguredHosts;
      int var10 = notConfiguredHosts.length;

      for(int var9 = 0; var9 < var10; ++var9) {
         ManagedObjectReference host = var11[var9];
         if (!this.permissionService.hasPermissions(host, new String[]{"Host.Config.Network"})) {
            hasHostNetworkConfig = false;
            break;
         }
      }

      return hasDvsModify && hasNetworkAssign && hasHostNetworkConfig;
   }

   private VsanClusterType getVsanClusterType(ManagedObjectReference clusterRef) throws Exception {
      PropertyValue[] witnessHostValues = QueryUtil.getPropertyForRelatedObjects(clusterRef, "allVsanHosts", ClusterComputeResource.class.getSimpleName(), "isWitnessHost").getPropertyValues();
      PropertyValue[] var6 = witnessHostValues;
      int var5 = witnessHostValues.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         PropertyValue witnessHostValue = var6[var4];
         if ((Boolean)witnessHostValue.value) {
            return VsanClusterType.STRETCHED_CLUSTER;
         }
      }

      return VsanClusterType.SINGLE_SITE_CLUSTER;
   }

   private Map<Service, DvsData> getDvsInfoData(HCIConfigInfo hciConfigInfo) throws Exception {
      Map<Service, DvsData> dvsDataByService = new HashMap();
      Map<Service, ManagedObjectReference> dvpgMorByService = new HashMap();
      Map<Service, ManagedObjectReference> dvsMorByService = new HashMap();
      if (hciConfigInfo == null) {
         return dvsDataByService;
      } else {
         DVSSetting[] dvsSettings = hciConfigInfo.getDvsSetting();
         if (ArrayUtils.isEmpty(dvsSettings)) {
            return dvsDataByService;
         } else {
            DVSSetting[] var9 = dvsSettings;
            int var8 = dvsSettings.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               DVSSetting dvsSetting = var9[var7];
               DVPortgroupToServiceMapping[] dvPortgroupSetting = dvsSetting.dvPortgroupSetting;
               if (ArrayUtils.isNotEmpty(dvPortgroupSetting)) {
                  DVPortgroupToServiceMapping[] var14 = dvPortgroupSetting;
                  int var13 = dvPortgroupSetting.length;

                  for(int var12 = 0; var12 < var13; ++var12) {
                     DVPortgroupToServiceMapping dvpgSetting = var14[var12];
                     if (dvpgSetting != null && (Service.fromString(dvpgSetting.service) == Service.VSAN || Service.fromString(dvpgSetting.service) == Service.VMOTION)) {
                        dvsMorByService.put(Service.fromString(dvpgSetting.service), dvsSetting.dvSwitch);
                        dvpgMorByService.put(Service.fromString(dvpgSetting.service), dvpgSetting.dvPortgroup);
                     }
                  }
               }
            }

            DataServiceResponse responseForDvpg;
            if (CollectionUtils.isNotEmpty(dvsMorByService.values())) {
               responseForDvpg = QueryUtil.getProperties((ManagedObjectReference[])dvsMorByService.values().toArray(new ManagedObjectReference[0]), new String[]{"name"});
               this.setDvsDataByService(dvsDataByService, dvsMorByService, responseForDvpg);
            }

            if (CollectionUtils.isNotEmpty(dvpgMorByService.values())) {
               responseForDvpg = QueryUtil.getProperties((ManagedObjectReference[])dvpgMorByService.values().toArray(new ManagedObjectReference[0]), new String[]{"config.defaultPortConfig.vlan"});
               this.setDvsDataByService(dvsDataByService, dvpgMorByService, responseForDvpg);
            }

            return dvsDataByService;
         }
      }
   }

   @TsService
   public String[] validateNetworkSpecification(ManagedObjectReference clusterRef, ClusterConfigData clusterConfigData) throws Exception {
      return this.getNetworkValidationMessages(clusterRef, clusterConfigData.getDvsProfiles(), (ManagedObjectReference[])null, clusterConfigData.basicConfig.vsanEnabled);
   }

   @TsService
   public List<String> getExistingPgNames(ManagedObjectReference clusterRef) throws Exception {
      PropertyConstraint id = QueryUtil.createPropertyConstraint(DistributedVirtualPortgroup.class.getSimpleName(), "serverGuid", com.vmware.vise.data.query.Comparator.EQUALS, clusterRef.getServerGuid());
      String[] properties = new String[]{"name"};
      ResultSet resultSet = QueryUtil.getData(QueryUtil.buildQuerySpec((Constraint)id, properties));
      DataServiceResponse response = QueryUtil.getDataServiceResponse(resultSet, properties);
      ArrayList<String> pgNames = new ArrayList();
      PropertyValue[] var10;
      int var9 = (var10 = response.getPropertyValues()).length;

      for(int var8 = 0; var8 < var9; ++var8) {
         PropertyValue propertyValue = var10[var8];
         pgNames.add((String)propertyValue.value);
      }

      return pgNames;
   }

   @TsService
   public List<ExistingDvsData> getExistingDvs(ManagedObjectReference clusterRef, String selectedDvsName) throws Exception {
      List<ExistingDvsData> result = new ArrayList();
      List<ManagedObjectReference> hostsInCluster = new ArrayList();
      PropertyValue[] hostValues = QueryUtil.getPropertyForRelatedObjects(clusterRef, "host", ClusterComputeResource.class.getSimpleName(), "config.product.version").getPropertyValues();
      if (hostValues == null) {
         return result;
      } else {
         int lowestHostVersion = Integer.MAX_VALUE;
         PropertyValue[] var10 = hostValues;
         int var9 = hostValues.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            PropertyValue hostValue = var10[var8];
            hostsInCluster.add((ManagedObjectReference)hostValue.resourceObject);
            int currentHostVersion = Integer.parseInt(((String)hostValue.value).replaceAll("\\.", ""));
            if (currentHostVersion < lowestHostVersion) {
               lowestHostVersion = currentHostVersion;
            }
         }

         Map<Object, Map<String, Object>> responseForDvs = this.queryDvsProperties(clusterRef);
         if (responseForDvs == null) {
            return result;
         } else {
            Iterator var14 = responseForDvs.entrySet().iterator();

            while(var14.hasNext()) {
               Entry<Object, Map<String, Object>> dvsEntry = (Entry)var14.next();
               Map<String, Object> dvsProperties = (Map)dvsEntry.getValue();
               if (!this.isDvsVersionIncompatible((String)dvsProperties.get("config.productInfo.version"), lowestHostVersion) && !this.isDvsConnectedToHostInCluster(hostsInCluster, (HostMember[])dvsProperties.get("config.host"))) {
                  ExistingDvsData existingDvsData = new ExistingDvsData();
                  existingDvsData.dvsRef = (ManagedObjectReference)dvsEntry.getKey();
                  existingDvsData.name = (String)dvsProperties.get("name");
                  existingDvsData.version = (String)dvsProperties.get("config.productInfo.version");
                  existingDvsData.niocVersion = (String)dvsProperties.get("lacpVersionColumnLabelDerived");
                  existingDvsData.lacpVersion = (String)dvsProperties.get("niocVersionColumnLabel");
                  if (existingDvsData.name.equals(selectedDvsName)) {
                     existingDvsData.isSelected = true;
                     result.add(0, existingDvsData);
                  } else {
                     result.add(existingDvsData);
                  }
               }
            }

            return result;
         }
      }
   }

   @TsService
   public List<ExistingDvpgData> getExistingDvpg(ManagedObjectReference dvsRef, String selectedDvpgName) throws Exception {
      List<ExistingDvpgData> result = new ArrayList();
      Map<Object, Map<String, Object>> dvpgResponse = QueryUtil.getPropertiesForRelatedObjects(dvsRef, "portgroup", DistributedVirtualPortgroup.class.getSimpleName(), new String[]{"name", "config.uplink"}).getMap();
      if (MapUtils.isEmpty(dvpgResponse)) {
         return result;
      } else {
         Iterator var6 = dvpgResponse.entrySet().iterator();

         while(var6.hasNext()) {
            Entry<Object, Map<String, Object>> dvpgEntry = (Entry)var6.next();
            Map<String, Object> dvpgProperties = (Map)dvpgEntry.getValue();
            if (!(Boolean)dvpgProperties.get("config.uplink")) {
               ExistingDvpgData existingDvpgData = new ExistingDvpgData();
               existingDvpgData.dvpgRef = (ManagedObjectReference)dvpgEntry.getKey();
               existingDvpgData.name = (String)dvpgProperties.get("name");
               if (existingDvpgData.name.equals(selectedDvpgName)) {
                  existingDvpgData.isSelected = true;
                  result.add(0, existingDvpgData);
               } else {
                  result.add(existingDvpgData);
               }
            }
         }

         return result;
      }
   }

   @TsService
   public VlanData getDvpgVlan(ManagedObjectReference dvpgRef) throws Exception {
      VlanSpec dvpgVlanSpec = (VlanSpec)QueryUtil.getProperty(dvpgRef, "config.defaultPortConfig.vlan", (Object)null);
      return dvpgVlanSpec == null ? null : this.getVlanData(dvpgVlanSpec);
   }

   private String[] getNetworkValidationMessages(ManagedObjectReference clusterRef, DvsProfile[] dvsProfiles, ManagedObjectReference[] notConfiguredHosts, boolean vsanEnabled) throws Exception {
      VcConnection vcConnection = this.getVcConnection(clusterRef, vsanEnabled);
      ArrayList messages = new ArrayList();

      try {
         Throwable var7 = null;
         Object var8 = null;

         try {
            VsanProfiler.Point point = _profiler.point("ClusterComputeResource.validateHCIConfiguration");

            try {
               ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
               HCIConfigSpec hciConfigSpec = new HCIConfigSpec();
               hciConfigSpec.dvsProf = dvsProfiles;
               ValidationResultBase[] validationResultBase = cluster.validateHCIConfiguration(hciConfigSpec, notConfiguredHosts);
               if (!ArrayUtils.isEmpty(validationResultBase)) {
                  ValidationResultBase[] var16 = validationResultBase;
                  int var15 = validationResultBase.length;

                  for(int var14 = 0; var14 < var15; ++var14) {
                     ValidationResultBase validationResult = var16[var14];
                     if (ArrayUtils.isEmpty(validationResult.info)) {
                        logger.warn("Unexpected ValidationResultBase value retrieved: validationResult.info array is empty.");
                     } else {
                        String message = "";
                        LocalizableMessage[] var21;
                        int var20 = (var21 = validationResult.info).length;

                        for(int var19 = 0; var19 < var20; ++var19) {
                           LocalizableMessage infoMessage = var21[var19];
                           message = message.concat(infoMessage.getMessage());
                        }

                        messages.add(message);
                     }
                  }

                  return CollectionUtils.isNotEmpty(messages) ? (String[])messages.toArray(new String[0]) : null;
               }
            } finally {
               if (point != null) {
                  point.close();
               }

            }

            return null;
         } catch (Throwable var29) {
            if (var7 == null) {
               var7 = var29;
            } else if (var7 != var29) {
               var7.addSuppressed(var29);
            }

            throw var7;
         }
      } catch (InvalidState var30) {
         return new String[]{Utils.getLocalizedString("vsan.hci.configureCluster.dvsVerification.clusterNotInHCI")};
      }
   }

   private void setDvsDataByService(Map<Service, DvsData> dvsDataByService, Map<Service, ManagedObjectReference> morByService, DataServiceResponse response) {
      PropertyValue[] var7;
      int var6 = (var7 = response.getPropertyValues()).length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertyValue propertyValue = var7[var5];
         if ("name".equals(propertyValue.propertyName)) {
            String switchName = (String)propertyValue.value;
            Iterator var12 = morByService.entrySet().iterator();

            while(var12.hasNext()) {
               Entry<Service, ManagedObjectReference> entry = (Entry)var12.next();
               if (((ManagedObjectReference)entry.getValue()).equals(propertyValue.resourceObject)) {
                  if (dvsDataByService.get(entry.getKey()) == null) {
                     dvsDataByService.put((Service)entry.getKey(), new DvsData());
                  }

                  ((DvsData)dvsDataByService.get(entry.getKey())).dvsName = switchName;
               }
            }
         } else if ("config.defaultPortConfig.vlan".equals(propertyValue.propertyName)) {
            VlanSpec dvpgVlanSpec = (VlanSpec)propertyValue.value;
            VlanData vlanData = this.getVlanData(dvpgVlanSpec);
            Iterator var11 = morByService.entrySet().iterator();

            while(var11.hasNext()) {
               Entry<Service, ManagedObjectReference> entry = (Entry)var11.next();
               if (((ManagedObjectReference)entry.getValue()).equals(propertyValue.resourceObject)) {
                  if (dvsDataByService.get(entry.getKey()) == null) {
                     dvsDataByService.put((Service)entry.getKey(), new DvsData());
                  }

                  ((DvsData)dvsDataByService.get(entry.getKey())).vlan = vlanData.vlan;
                  ((DvsData)dvsDataByService.get(entry.getKey())).vlanType = vlanData.vlanType;
               }
            }
         }
      }

   }

   private VlanData getVlanData(VlanSpec dvpgVlanSpec) {
      VlanData vlanData = new VlanData();
      vlanData.vlan = "0";
      vlanData.vlanType = VlanType.NONE;
      if (dvpgVlanSpec != null) {
         if (dvpgVlanSpec instanceof VlanIdSpec) {
            vlanData.vlan = String.valueOf(((VlanIdSpec)dvpgVlanSpec).vlanId);
            vlanData.vlanType = VlanType.VLAN_ID;
         } else if (dvpgVlanSpec instanceof TrunkVlanSpec) {
            NumericRange[] trunkRanges = ((TrunkVlanSpec)dvpgVlanSpec).vlanId;
            vlanData.vlan = StringUtil.parseNumericRange(trunkRanges);
            vlanData.vlanType = VlanType.VLAN_TRUNK;
         } else if (dvpgVlanSpec instanceof PvlanSpec) {
            vlanData.vlan = String.valueOf(((PvlanSpec)dvpgVlanSpec).pvlanId);
            vlanData.vlanType = VlanType.PVLAN;
         }
      }

      return vlanData;
   }

   private boolean hasAddHostsPermissions(ManagedObjectReference clusterRef, boolean vsanEnabled, boolean hasEditClusterPermission) throws Exception {
      ManagedObjectReference hostFolder = (ManagedObjectReference)QueryUtil.getPropertyForRelatedObjects(clusterRef, "dc", ClusterComputeResource.class.getSimpleName(), "hostFolder").getPropertyValues()[0].value;
      boolean hasHostPermissions = this.permissionService.hasPermissions(hostFolder, new String[]{"Host.Inventory.AddStandaloneHost", "Host.Inventory.MoveHost"});
      if (vsanEnabled) {
         return hasHostPermissions && hasEditClusterPermission;
      } else {
         return hasHostPermissions;
      }
   }

   private List<ManagedObjectReference> getDvPortgroups(HCIConfigInfo hciConfig) {
      List<ManagedObjectReference> result = new ArrayList();
      if (hciConfig.dvsSetting != null) {
         DVSSetting[] var6;
         int var5 = (var6 = hciConfig.dvsSetting).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            DVSSetting dvsSetting = var6[var4];
            if (dvsSetting.dvPortgroupSetting != null) {
               DVPortgroupToServiceMapping[] var10;
               int var9 = (var10 = dvsSetting.dvPortgroupSetting).length;

               for(int var8 = 0; var8 < var9; ++var8) {
                  DVPortgroupToServiceMapping mapping = var10[var8];
                  result.add(mapping.dvPortgroup);
               }
            }
         }
      }

      return result;
   }

   @TsService
   public VsanTestData getHealthGroupData(ManagedObjectReference clusterRef, String perspective, String group) throws Exception {
      VsanClusterHealthSummary healthSummary = this.getHealthSummary(clusterRef, perspective);
      Set<ManagedObjectReference> allMoRefs = new HashSet();
      VsanClusterHealthGroup currentGroup;
      int var7;
      int var8;
      VsanClusterHealthGroup[] var9;
      if (healthSummary != null && healthSummary.groups != null) {
         var8 = (var9 = healthSummary.groups).length;

         for(var7 = 0; var7 < var8; ++var7) {
            currentGroup = var9[var7];
            VsanHealthUtil.addToTestMoRefs(currentGroup, allMoRefs, clusterRef.getServerGuid());
         }
      }

      var8 = (var9 = healthSummary.groups).length;

      for(var7 = 0; var7 < var8; ++var7) {
         currentGroup = var9[var7];
         if (currentGroup.groupName.equals(group)) {
            return new VsanTestData(currentGroup, VsanHealthUtil.getNamesForMoRefs(allMoRefs));
         }
      }

      return null;
   }

   private VsanClusterHealthSummary getHealthSummary(ManagedObjectReference clusterRef, String perspective) throws Exception {
      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VsanProfiler.Point point = _profiler.point("healthSystem.queryClusterHealthSummary");

         Throwable var10000;
         label173: {
            boolean var10001;
            VsanClusterHealthSummary var19;
            try {
               var19 = healthSystem.queryClusterHealthSummary(clusterRef, (Integer)null, (String[])null, true, new String[]{"groups", "timestamp"}, false, perspective, (ManagedObjectReference[])null, (Boolean)null);
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (point != null) {
               point.close();
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

         var4 = var10000;
         if (point != null) {
            point.close();
         }

         throw var4;
      } catch (Throwable var18) {
         if (var4 == null) {
            var4 = var18;
         } else if (var4 != var18) {
            var4.addSuppressed(var18);
         }

         throw var4;
      }
   }

   private Map<Object, Map<String, Object>> queryDvsProperties(ManagedObjectReference clusterRef) throws Exception {
      ManagedObjectReference datacenter = (ManagedObjectReference)QueryUtil.getProperty(clusterRef, "dc", (Object)null);
      PropertyConstraint id = QueryUtil.createPropertyConstraint(VmwareDistributedVirtualSwitch.class.getSimpleName(), "dc", com.vmware.vise.data.query.Comparator.EQUALS, datacenter);
      ResultSet resultSet = QueryUtil.getData(QueryUtil.buildQuerySpec((Constraint)id, EXISTING_DVS_PROPERTIES));
      DataServiceResponse dvsRefResponse = QueryUtil.getDataServiceResponse(resultSet, EXISTING_DVS_PROPERTIES);
      return dvsRefResponse.getMap();
   }

   private boolean isDvsVersionIncompatible(String dvsVersion, int lowestHostVersion) {
      int parsedDvsVersion = Integer.parseInt(dvsVersion.replaceAll("\\.", ""));
      return lowestHostVersion < parsedDvsVersion;
   }

   private boolean isDvsConnectedToHostInCluster(List<ManagedObjectReference> hostsInCluster, HostMember[] hostMembers) {
      if (CollectionUtils.isNotEmpty(hostsInCluster) && ArrayUtils.isNotEmpty(hostMembers)) {
         HostMember[] var6 = hostMembers;
         int var5 = hostMembers.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            HostMember hostMember = var6[var4];
            if (hostMember != null && hostMember.config != null && hostMember.config.host != null && hostsInCluster.contains(hostMember.config.host)) {
               return true;
            }
         }
      }

      return false;
   }

   private String getConfigureCardContentText(BasicClusterConfigData basicClusterConfigData) {
      if (basicClusterConfigData.drsEnabled && basicClusterConfigData.vsanEnabled) {
         return Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.contentText.vMotionVsanTraffic");
      } else if (basicClusterConfigData.drsEnabled) {
         return Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.contentText.vMotionTraffic");
      } else {
         return basicClusterConfigData.vsanEnabled ? Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.contentText.vSanTraffic") : Utils.getLocalizedString("vsan.hci.gettingStarted.configureServicesCard.contentText.default");
      }
   }

   private boolean isHideSupportInsightStepConfigured(ManagedObjectReference clusterRef) throws Exception {
      try {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

            try {
               OptionManager optionManager = (OptionManager)vcConnection.createStub(OptionManager.class, vcConnection.getContent().setting);
               OptionValue[] optionValues = optionManager.getSetting();
               if (!ArrayUtils.isEmpty(optionValues)) {
                  OptionValue[] var10 = optionValues;
                  int var9 = optionValues.length;

                  for(int var8 = 0; var8 < var9; ++var8) {
                     OptionValue optionValue = var10[var8];
                     if ("config.vsan.hide_ceip_page_in_hci_wofkflow".equals(optionValue.key) && optionValue.value != null && BooleanUtils.isTrue(Boolean.parseBoolean(optionValue.value.toString()))) {
                        return true;
                     }
                  }

                  return false;
               }
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }

            return false;
         } catch (Throwable var18) {
            if (var2 == null) {
               var2 = var18;
            } else if (var2 != var18) {
               var2.addSuppressed(var18);
            }

            throw var2;
         }
      } catch (Exception var19) {
         logger.error("Failed to read config.vsan.hide_ceip_page_in_hci_wofkflow", var19);
         return false;
      }
   }

   @TsService
   public void hideSupportInsightStep(ManagedObjectReference clusterRef) throws Exception {
      try {
         Throwable var2 = null;
         Object var3 = null;

         try {
            VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

            try {
               OptionManager optionManager = (OptionManager)vcConnection.createStub(OptionManager.class, vcConnection.getContent().setting);
               OptionValue newOption = new OptionValue("config.vsan.hide_ceip_page_in_hci_wofkflow", Boolean.TRUE.toString());
               optionManager.updateValues(new OptionValue[]{newOption});
            } finally {
               if (vcConnection != null) {
                  vcConnection.close();
               }

            }

         } catch (Throwable var14) {
            if (var2 == null) {
               var2 = var14;
            } else if (var2 != var14) {
               var2.addSuppressed(var14);
            }

            throw var2;
         }
      } catch (Exception var15) {
         logger.error("Failed to save config.vsan.hide_ceip_page_in_hci_wofkflow", var15);
         throw new Exception(Utils.getLocalizedString("vsan.hci.dialog.hideSupportInsight.error"), var15);
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$HciWorkflowState() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$HciWorkflowState;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[HciWorkflowState.values().length];

         try {
            var0[HciWorkflowState.DONE.ordinal()] = 2;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[HciWorkflowState.INVALID.ordinal()] = 3;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[HciWorkflowState.IN_PROGRESS.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[HciWorkflowState.NOT_IN_HCI_WORKFLOW.ordinal()] = 4;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$HciWorkflowState = var0;
         return var0;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[EncryptionState.values().length];

         try {
            var0[EncryptionState.Disabled.ordinal()] = 2;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[EncryptionState.Enabled.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[EncryptionState.EnabledNoKmip.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$EncryptionState = var0;
         return var0;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$Service() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$Service;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[Service.values().length];

         try {
            var0[Service.MANAGEMENT.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[Service.VMOTION.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[Service.VSAN.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$hci$model$Service = var0;
         return var0;
      }
   }
}
