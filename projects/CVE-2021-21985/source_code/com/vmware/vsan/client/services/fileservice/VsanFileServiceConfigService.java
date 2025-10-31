package com.vmware.vsan.client.services.fileservice;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.impl.vmodl.TypeNameImpl;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vmodl.DynamicProperty;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.query.PropertyCollector;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.FilterSpec;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.ObjectContent;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.ObjectSpec;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.PropertySpec;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.RetrieveOptions;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.RetrieveResult;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.SelectionSpec;
import com.vmware.vim.binding.vmodl.query.PropertyCollector.TraversalSpec;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx;
import com.vmware.vim.vsan.binding.vim.vsan.DirectoryServerConfig;
import com.vmware.vim.vsan.binding.vim.vsan.FileServiceConfig;
import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vim.vsan.binding.vim.vsan.FileShareQuerySpec;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceOvfSpec;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServicePreflightCheckResult;
import com.vmware.vim.vsan.binding.vim.vsan.VsanFileServiceSystem;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceCommonConfig;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceOvf;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceShare;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceShareConfig;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VsanFileServiceConfigService {
   private static final String PROP_CLUSTER_HOST = "host";
   private static final String PROP_HOST_BUILD = "config.product.build";
   private static final String PROP_HOST_VERSION = "config.product.version";
   private static final String PUBLIC_OVF_URL_TEMPLATE_OB = "http://build-squid.eng.vmware.com/build/mts/release/bora-%s/publish/vdfs-fsvm/VMware-vSAN-File-Services-Appliance-%s-%s_OVF10.ovf";
   private static final String PUBLIC_OVF_URL_TEMPLATE_SB = "http://build-squid.eng.vmware.com/build/mts/release/sb-%s/publish/vdfs-fsvm/VMware-vSAN-File-Services-Appliance-%s-%s_OVF10.ovf";
   private static final Log logger = LogFactory.getLog(VsanFileServiceConfigService.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanFileServiceConfigService.class);
   @Autowired
   private VcClient vcClient;

   @TsService
   public List<VsanFileServiceOvf> getRegisteredOvfs(ManagedObjectReference clusterRef) {
      Validate.notNull(clusterRef);
      List<VsanFileServiceOvf> result = new ArrayList();
      VsanFileServiceSystem fileServiceSystem = VsanProviderUtils.getVsanVcFileServiceSystem(clusterRef);
      Measure measure = new Measure("Retrieving VDFS OVFs");
      Future<VsanFileServiceOvfSpec[]> ovfsFuture = measure.newFuture("VsanFileServiceSystem.queryFileServiceOvfs");
      Future<VsanFileServicePreflightCheckResult> precheckFuture = measure.newFuture("VsanFileServiceSystem.performFileServicePreflightCheck");
      fileServiceSystem.queryFileServiceOvfs(ovfsFuture);
      fileServiceSystem.performFileServicePreflightCheck(clusterRef, (DirectoryServerConfig)null, precheckFuture);

      try {
         VsanFileServiceOvfSpec[] ovfSpecs = (VsanFileServiceOvfSpec[])ovfsFuture.get();
         VsanFileServicePreflightCheckResult precheckResult = (VsanFileServicePreflightCheckResult)precheckFuture.get();
         VsanFileServiceConfigService.BuildInfo hostBuildInfo = this.getHostBuildInfo(clusterRef);
         if (ArrayUtils.isNotEmpty(ovfSpecs)) {
            VsanFileServiceOvfSpec[] var13 = ovfSpecs;
            int var12 = ovfSpecs.length;

            for(int var11 = 0; var11 < var12; ++var11) {
               VsanFileServiceOvfSpec ovfSpec = var13[var11];
               VsanFileServiceOvf ovf = VsanFileServiceOvf.fromVmodl(ovfSpec, clusterRef);
               result.add(ovf);
               if (!StringUtils.isEmpty(precheckResult.ovfInstalled) && !StringUtils.isEmpty(ovf.version)) {
                  VsanFileServiceConfigService.BuildInfo ovfBuildInfo = null;
                  ovfBuildInfo = VsanFileServiceConfigService.BuildInfo.fromOvfVersionString(ovf.version);
                  ovf.isCompatible = ovfBuildInfo.version.equals(precheckResult.ovfInstalled) && ovfBuildInfo.equals(hostBuildInfo);
               }
            }
         }

         return result;
      } catch (Exception var16) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.loadOvfs", "Cannot load registered OVFS for cluster: " + clusterRef, var16, new Object[0]);
      }
   }

   @TsService
   public ManagedObjectReference downloadPublicOvf(ManagedObjectReference clusterRef) {
      Validate.notNull(clusterRef);
      VsanFileServiceSystem vsanVcFileServiceSystem = VsanProviderUtils.getVsanVcFileServiceSystem(clusterRef);
      ManagedObjectReference taskRef = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = _profiler.point("VsanFileServiceSystem.downloadFileServiceOvf");

            try {
               String ovfUrl = this.getPublicOvfUrl(clusterRef);
               logger.debug("Download OVF: " + ovfUrl);
               taskRef = vsanVcFileServiceSystem.downloadFileServiceOvf(ovfUrl);
               VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
            } finally {
               if (p != null) {
                  p.close();
               }

            }

            return taskRef;
         } catch (Throwable var15) {
            if (var4 == null) {
               var4 = var15;
            } else if (var4 != var15) {
               var4.addSuppressed(var15);
            }

            throw var4;
         }
      } catch (Exception var16) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.downloadOvf", "Cannot download OVF for " + clusterRef, var16, new Object[0]);
      }
   }

   @TsService
   public ManagedObjectReference configureFileService(ManagedObjectReference clusterRef, VsanFileServiceCommonConfig fileServiceConfig, boolean isEdit) {
      return null;
   }

   @TsService
   public ManagedObjectReference disableFileService(ManagedObjectReference clusterRef) {
      return null;
   }

   private static ManagedObjectReference reconfigureFileService(ManagedObjectReference clusterRef, FileServiceConfig fileServiceConfig, boolean isEdit) {
      ReconfigSpec reconfigSpec = new ReconfigSpec();
      reconfigSpec.modify = isEdit;
      reconfigSpec.fileServiceConfig = fileServiceConfig;
      VsanVcClusterConfigSystem vsanConfigSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ManagedObjectReference taskRef = null;

      try {
         taskRef = vsanConfigSystem.reconfigureEx(clusterRef, reconfigSpec);
         VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
         return taskRef;
      } catch (Exception var7) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.configure", "Cannot reconfigure cluster '" + clusterRef + "' with configuration: " + reconfigSpec, var7, new Object[0]);
      }
   }

   @TsService
   public List<VsanFileServiceShare> listSharesPerDomain(ManagedObjectReference clusterRef, String domainName) {
      Validate.notNull(clusterRef);
      Validate.notNull(domainName);
      return this.listShares(clusterRef, domainName);
   }

   @TsService
   public List<VsanFileServiceShare> listAllShares(ManagedObjectReference clusterRef) {
      Validate.notNull(clusterRef);
      return this.listShares(clusterRef, (String)null);
   }

   private List<VsanFileServiceShare> listShares(ManagedObjectReference clusterRef, String domainName) {
      List<VsanFileServiceShare> shares = new ArrayList();
      VsanFileServiceSystem fileServiceSystem = VsanProviderUtils.getVsanVcFileServiceSystem(clusterRef);
      FileShare[] fileShares = null;

      try {
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = new VsanProfiler.Point("VsanFileServiceSystem.queryFileShares");

            try {
               FileShareQuerySpec spec = new FileShareQuerySpec();
               if (StringUtils.isNotBlank(domainName)) {
                  spec.domainName = domainName;
               }

               fileShares = fileServiceSystem.queryFileShares(spec, clusterRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var18) {
            if (var6 == null) {
               var6 = var18;
            } else if (var6 != var18) {
               var6.addSuppressed(var18);
            }

            throw var6;
         }
      } catch (Exception var19) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.loadShares", "Cannot load file shares for cluster '" + clusterRef + "' and domain '" + domainName + "'", var19, new Object[0]);
      }

      if (!ArrayUtils.isEmpty(fileShares)) {
         FileShare[] var23 = fileShares;
         int var22 = fileShares.length;

         for(int var21 = 0; var21 < var22; ++var21) {
            FileShare fileShare = var23[var21];
            VsanFileServiceShare share = VsanFileServiceShare.fromVmodl(fileShare);
            shares.add(share);
         }
      }

      Collections.sort(shares, new Comparator<VsanFileServiceShare>() {
         public int compare(VsanFileServiceShare first, VsanFileServiceShare second) {
            if (first != null && first.config != null && !StringUtils.isEmpty(first.config.name)) {
               return second != null && second.config != null && !StringUtils.isEmpty(second.config.name) ? first.config.name.compareTo(second.config.name) : 1;
            } else {
               return -1;
            }
         }
      });
      return shares;
   }

   @TsService
   public ManagedObjectReference createShare(ManagedObjectReference clusterRef, VsanFileServiceShareConfig share) {
      return null;
   }

   @TsService
   public ManagedObjectReference updateShare(ManagedObjectReference clusterRef, String shareUuid, VsanFileServiceShareConfig share) {
      return null;
   }

   @TsService
   public List<VsanFileServiceShare> queryShare(ManagedObjectReference param1, String param2) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference deleteShare(ManagedObjectReference clusterRef, String shareUuid, VsanFileServiceShareConfig share) {
      Validate.notNull(clusterRef);
      Validate.notNull(share);
      ManagedObjectReference taskRef = null;
      VsanFileServiceSystem fileServiceSystem = VsanProviderUtils.getVsanVcFileServiceSystem(clusterRef);

      try {
         Throwable var6 = null;
         Object var7 = null;

         try {
            VsanProfiler.Point p = new VsanProfiler.Point("VsanFileServiceSystem.removeFileShare");

            try {
               taskRef = fileServiceSystem.removeFileShare(shareUuid, clusterRef);
               VmodlHelper.assignServerGuid(taskRef, clusterRef.getServerGuid());
            } finally {
               if (p != null) {
                  p.close();
               }

            }

            return taskRef;
         } catch (Throwable var16) {
            if (var6 == null) {
               var6 = var16;
            } else if (var6 != var16) {
               var6.addSuppressed(var16);
            }

            throw var6;
         }
      } catch (Exception var17) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.deleteShare", "Cannot delete share with UUID: " + shareUuid, var17, new Object[0]);
      }
   }

   @TsService
   public VsanFileServiceCommonConfig getConfig(ManagedObjectReference clusterRef) {
      return null;
   }

   private FileServiceConfig getFileServiceConfig(ManagedObjectReference clusterRef) {
      VsanVcClusterConfigSystem configSystem = VsanProviderUtils.getVsanConfigSystem(clusterRef);
      ConfigInfoEx configInfoEx = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point p = new VsanProfiler.Point("VsanVcClusterConfigSystem.getConfigInfoEx");

            try {
               configInfoEx = configSystem.getConfigInfoEx(clusterRef);
            } finally {
               if (p != null) {
                  p.close();
               }

            }
         } catch (Throwable var14) {
            if (var4 == null) {
               var4 = var14;
            } else if (var4 != var14) {
               var4.addSuppressed(var14);
            }

            throw var4;
         }
      } catch (Exception var15) {
         throw new VsanUiLocalizableException("vsan.fileservice.error.loadConfig", "Cannot load vSAN configuration for cluster: " + clusterRef, var15, new Object[0]);
      }

      return configInfoEx != null && configInfoEx.fileServiceConfig != null ? configInfoEx.fileServiceConfig : null;
   }

   private String getPublicOvfUrl(ManagedObjectReference clusterRef) throws Exception {
      VsanFileServiceConfigService.BuildInfo buildInfo = this.getHostBuildInfo(clusterRef);
      String urlPattern = buildInfo.isSandbox ? "http://build-squid.eng.vmware.com/build/mts/release/sb-%s/publish/vdfs-fsvm/VMware-vSAN-File-Services-Appliance-%s-%s_OVF10.ovf" : "http://build-squid.eng.vmware.com/build/mts/release/bora-%s/publish/vdfs-fsvm/VMware-vSAN-File-Services-Appliance-%s-%s_OVF10.ovf";
      return String.format(urlPattern, buildInfo.build, buildInfo.version, buildInfo.build);
   }

   private VsanFileServiceConfigService.BuildInfo getHostBuildInfo(ManagedObjectReference clusterRef) throws Exception {
      RetrieveResult pcResult = null;
      FilterSpec filterSpec = this.createFilterSpecForHostBuildInfo(clusterRef);
      Throwable var4 = null;
      Object var5 = null;

      try {
         VcConnection conn = this.vcClient.getConnection(clusterRef.getServerGuid());

         try {
            FilterSpec[] filterSpecs = new FilterSpec[]{filterSpec};
            RetrieveOptions options = new RetrieveOptions();
            PropertyCollector propertyCollector = conn.getPropertyCollector();
            pcResult = propertyCollector.retrievePropertiesEx(filterSpecs, options);
         } finally {
            if (conn != null) {
               conn.close();
            }

         }
      } catch (Throwable var15) {
         if (var4 == null) {
            var4 = var15;
         } else if (var4 != var15) {
            var4.addSuppressed(var15);
         }

         throw var4;
      }

      if (pcResult != null && !ArrayUtils.isEmpty(pcResult.objects)) {
         Set<VsanFileServiceConfigService.BuildInfo> buildInfos = this.processBuildInfos(pcResult);
         if (buildInfos.size() != 1) {
            logger.warn("The environment is not homogenous! Using a random build configuration! It is possible your setup to work flawlessly however there is a chance of VMODL mismatches!");
         }

         return (VsanFileServiceConfigService.BuildInfo)buildInfos.iterator().next();
      } else {
         throw new Exception("Cannot retrieve hosts' build info!");
      }
   }

   private FilterSpec createFilterSpecForHostBuildInfo(ManagedObjectReference clusterRef) {
      FilterSpec filterSpec = new FilterSpec();
      ObjectSpec objectSpec = new ObjectSpec();
      objectSpec.obj = clusterRef;
      TraversalSpec traversalSpec = new TraversalSpec();
      traversalSpec.path = "host";
      traversalSpec.type = new TypeNameImpl(ClusterComputeResource.class.getSimpleName());
      objectSpec.selectSet = new SelectionSpec[]{traversalSpec};
      PropertySpec propertySpec = new PropertySpec();
      propertySpec.type = new TypeNameImpl(HostSystem.class.getSimpleName());
      propertySpec.pathSet = new String[]{"config.product.build", "config.product.version"};
      filterSpec.objectSet = new ObjectSpec[]{objectSpec};
      filterSpec.propSet = new PropertySpec[]{propertySpec};
      return filterSpec;
   }

   private Set<VsanFileServiceConfigService.BuildInfo> processBuildInfos(RetrieveResult pcResult) {
      Set<VsanFileServiceConfigService.BuildInfo> buildInfos = new HashSet();
      ObjectContent[] var6;
      int var5 = (var6 = pcResult.objects).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ObjectContent hostResult = var6[var4];
         VsanFileServiceConfigService.BuildInfo buildInfo = VsanFileServiceConfigService.BuildInfo.fromDynamicProperties(hostResult.propSet);
         buildInfos.add(buildInfo);
      }

      return buildInfos;
   }

   private static class BuildInfo {
      public static final int BUILD_NUMBER_THRESHOLD = 20000000;
      private String build;
      private String version;
      private boolean isSandbox;

      public static VsanFileServiceConfigService.BuildInfo fromOvfVersionString(String ovfVersion) throws Exception {
         Validate.notEmpty(ovfVersion);
         String[] chunks = ovfVersion.split(" ");
         if (!ArrayUtils.isEmpty(chunks) && chunks.length == 3) {
            VsanFileServiceConfigService.BuildInfo buildInfo = new VsanFileServiceConfigService.BuildInfo();
            buildInfo.version = chunks[0];
            buildInfo.build = chunks[2];
            buildInfo.updateIsSandbox();
            return buildInfo;
         } else {
            throw new Exception("Invalid OVF version string format: " + ovfVersion);
         }
      }

      public static VsanFileServiceConfigService.BuildInfo fromDynamicProperties(DynamicProperty[] props) {
         VsanFileServiceConfigService.BuildInfo buildInfo = new VsanFileServiceConfigService.BuildInfo();
         DynamicProperty[] var5 = props;
         int var4 = props.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            DynamicProperty prop = var5[var3];
            String var6;
            switch((var6 = prop.getName()).hashCode()) {
            case -1696943357:
               if (var6.equals("config.product.build")) {
                  buildInfo.build = (String)prop.getVal();
               }
               break;
            case 1445673005:
               if (var6.equals("config.product.version")) {
                  buildInfo.version = prop.getVal() + ".1000";
               }
            }
         }

         buildInfo.updateIsSandbox();
         return buildInfo;
      }

      private void updateIsSandbox() {
         try {
            int buildNumber = Integer.parseInt(this.build);
            this.isSandbox = buildNumber > 20000000;
         } catch (Exception var2) {
            VsanFileServiceConfigService.logger.warn("Not able to parse server build version. Assuming that this is an official build.");
         }

      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            VsanFileServiceConfigService.BuildInfo buildInfo = (VsanFileServiceConfigService.BuildInfo)o;
            return this.isSandbox == buildInfo.isSandbox && Objects.equals(this.build, buildInfo.build) && Objects.equals(this.version, buildInfo.version);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return Objects.hash(new Object[]{this.build, this.version, this.isSandbox});
      }
   }
}
