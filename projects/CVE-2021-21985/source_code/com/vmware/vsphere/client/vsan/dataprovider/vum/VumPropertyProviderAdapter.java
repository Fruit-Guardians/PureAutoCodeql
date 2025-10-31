package com.vmware.vsphere.client.vsan.dataprovider.vum;

import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.host.MaintenanceSpec;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHclInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.host.VsanHclFirmwareUpdateSpec;
import com.vmware.vim.vsan.binding.vim.host.VsanUpdateManager;
import com.vmware.vim.vsan.binding.vim.vsan.VsanDownloadItem;
import com.vmware.vim.vsan.binding.vim.vsan.VsanUpdateItem;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVibScanResult;
import com.vmware.vim.vsan.binding.vim.vsan.VsanVibSpec;
import com.vmware.vise.data.ParameterSpec;
import com.vmware.vise.data.PropertySpec;
import com.vmware.vise.data.query.DataServiceExtensionRegistry;
import com.vmware.vise.data.query.PropertyProviderAdapter;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import com.vmware.vise.data.query.TypeInfo;
import com.vmware.vsan.client.services.ProxygenSerializer;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.vum.VumBaselineRecommendationService;
import com.vmware.vsphere.client.vsan.base.impl.VsanComponentsProvider;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.data.VumBaselineRecommendationType;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VumPropertyProviderAdapter implements PropertyProviderAdapter {
   private static final Logger logger = LoggerFactory.getLogger(VumPropertyProviderAdapter.class);
   private static final String UPDATES = "updates";
   private static final String AVAILABLE = "vumVsanIntegrationAvailable";
   private static final String IMPORT = "vumVsanImportFirmware";
   private static final String VENDOR_INSTALL = "vumVsanInstallVendorTool";
   private static final String VENDOR_INSTALL_FROM_CHECKSUM = "vumVsanInstallVendorToolFromChecksum";
   private static final String VENDOR_DOWNLOAD_AND_INSTALL = "vumVsanDownloadInstallVendorTool";
   private static final String BASELINE_RECOMMENDATION = "baselineRecommendation";
   private static final String BASELINE_RECOMMENDATION_AVAILABLE = "vsanEnabledAndBaselineRecommendationAvailable";
   private static final String TOOL = "tool";
   @Autowired
   private VumBaselineRecommendationService baselineRecommendationService;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType;

   public VumPropertyProviderAdapter(DataServiceExtensionRegistry registry) {
      TypeInfo clusterType = new TypeInfo();
      clusterType.type = ClusterComputeResource.class.getSimpleName();
      clusterType.properties = new String[]{"updates", "vumVsanImportFirmware", "vumVsanIntegrationAvailable", "vumVsanInstallVendorTool", "vumVsanInstallVendorToolFromChecksum", "vumVsanDownloadInstallVendorTool", "baselineRecommendation", "vsanEnabledAndBaselineRecommendationAvailable"};
      registry.registerDataAdapter(this, new TypeInfo[]{clusterType});
   }

   public ResultSet getProperties(PropertyRequestSpec propertyRequest) {
      ResultSet result = new ResultSet();
      ManagedObjectReference[] targetObjects = (ManagedObjectReference[])Arrays.copyOf(propertyRequest.objects, propertyRequest.objects.length, ManagedObjectReference[].class);
      PropertySpec[] var7;
      int var6 = (var7 = propertyRequest.properties).length;

      for(int var5 = 0; var5 < var6; ++var5) {
         PropertySpec propertySpec = var7[var5];
         String[] var11;
         int var10 = (var11 = propertySpec.propertyNames).length;

         for(int var9 = 0; var9 < var10; ++var9) {
            String propertyName = var11[var9];

            try {
               switch(propertyName.hashCode()) {
               case -1526316595:
                  if (propertyName.equals("vumVsanIntegrationAvailable")) {
                     ArrayList<ResultItem> items = new ArrayList();
                     ManagedObjectReference[] var17 = targetObjects;
                     int var16 = targetObjects.length;

                     for(int var15 = 0; var15 < var16; ++var15) {
                        ManagedObjectReference clusterRef = var17[var15];
                        boolean available = VsanCapabilityUtils.isVsanVumIntegrationSupported(clusterRef);
                        items.add(QueryUtil.createResultItem("vumVsanIntegrationAvailable", available, clusterRef));
                     }

                     result.items = (ResultItem[])items.toArray(new ResultItem[targetObjects.length]);
                     continue;
                  }
                  break;
               case -1206112381:
                  if (propertyName.equals("vumVsanInstallVendorTool")) {
                     result.items = this.installVib(targetObjects, propertySpec.parameters);
                     continue;
                  }
                  break;
               case -234430262:
                  if (propertyName.equals("updates")) {
                     result.items = this.getUpdates(targetObjects);
                     continue;
                  }
                  break;
               case 79019995:
                  if (propertyName.equals("vumVsanDownloadInstallVendorTool")) {
                     result.items = this.downloadAndInstallTools(targetObjects);
                     continue;
                  }
                  break;
               case 115089611:
                  if (propertyName.equals("vsanEnabledAndBaselineRecommendationAvailable")) {
                     result.items = (ResultItem[])this.getBaselineRecommendationAvailable(targetObjects).toArray(new ResultItem[targetObjects.length]);
                     continue;
                  }
                  break;
               case 1413649656:
                  if (propertyName.equals("vumVsanImportFirmware")) {
                     result.items = this.importFirmware(targetObjects, propertySpec.parameters);
                     continue;
                  }
                  break;
               case 1580590384:
                  if (propertyName.equals("vumVsanInstallVendorToolFromChecksum")) {
                     result.items = this.installVibFromChecksum(targetObjects, propertySpec.parameters);
                     continue;
                  }
                  break;
               case 1635718494:
                  if (propertyName.equals("baselineRecommendation")) {
                     result.items = (ResultItem[])this.getVumBaselineRecommendation(targetObjects).toArray(new ResultItem[targetObjects.length]);
                     continue;
                  }
               }

               throw new UnsupportedOperationException();
            } catch (Exception var19) {
               result.error = var19;
            }
         }
      }

      return result;
   }

   private ArrayList<ResultItem> getVumBaselineRecommendation(ManagedObjectReference[] targetObjects) throws Exception {
      ArrayList<ResultItem> items = new ArrayList();
      ManagedObjectReference[] var6 = targetObjects;
      int var5 = targetObjects.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference clusterRef = var6[var4];
         VumBaselineRecommendationType recommendation = this.baselineRecommendationService.getClusterVumBaselineRecommendation(clusterRef);
         items.add(QueryUtil.createResultItem("baselineRecommendation", this.getBaselineRecommendationTxt(recommendation), clusterRef));
      }

      return items;
   }

   private String getBaselineRecommendationTxt(VumBaselineRecommendationType recommendation) {
      switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType()[recommendation.ordinal()]) {
      case 1:
         return Utils.getLocalizedString("vsan.vum.baseline.recommendation.latestPatch");
      case 2:
         return Utils.getLocalizedString("vsan.vum.baseline.recommendation.latestRelease");
      case 3:
         return Utils.getLocalizedString("vsan.vum.baseline.recommendation.noRecommendation");
      default:
         logger.error("Not supported vum baseline recommendation found: ", recommendation);
         return recommendation.toString();
      }
   }

   private ArrayList<ResultItem> getBaselineRecommendationAvailable(ManagedObjectReference[] targetObjects) throws Exception {
      ArrayList<ResultItem> items = new ArrayList();
      DataServiceResponse response = QueryUtil.getProperties(targetObjects, new String[]{"configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled"});
      ManagedObjectReference[] var7 = targetObjects;
      int var6 = targetObjects.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference clusterRef = var7[var5];
         Object propertyValue = ((Map)response.getMap().get(clusterRef)).get("configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled");
         boolean isVsanEnabled = propertyValue == null ? false : (Boolean)propertyValue;
         boolean available = isVsanEnabled && VsanCapabilityUtils.isVumBaselineRecommendationSupportedOnVc(clusterRef);
         items.add(QueryUtil.createResultItem("vsanEnabledAndBaselineRecommendationAvailable", available, clusterRef));
      }

      return items;
   }

   private ResultItem[] importFirmware(ManagedObjectReference[] refs, ParameterSpec[] parameterSpecs) throws Exception {
      if (parameterSpecs != null && parameterSpecs.length != 0) {
         List<String> checksums = null;
         ParameterSpec[] var7 = parameterSpecs;
         int var6 = parameterSpecs.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            ParameterSpec spec = var7[var5];
            if (spec.propertyName.equals("vumVsanImportFirmware")) {
               checksums = (List)spec.parameter;
               break;
            }
         }

         if (checksums == null) {
            logger.warn("Unable to find supplied checksums for the update importFirmware.");
            return new ResultItem[0];
         } else {
            ArrayList<ResultItem> result = new ArrayList();
            ManagedObjectReference[] var8 = refs;
            int var22 = refs.length;

            for(var6 = 0; var6 < var22; ++var6) {
               ManagedObjectReference clusterRef = var8[var6];
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               Throwable var11 = null;
               Object var12 = null;

               ManagedObjectReference taskRef;
               try {
                  VsanProfiler.Point p = VsanComponentsProvider._profiler.point("healthSystem.downloadHclFile");

                  try {
                     taskRef = healthSystem.downloadHclFile((String[])checksums.toArray(new String[checksums.size()]));
                  } finally {
                     if (p != null) {
                        p.close();
                     }

                  }
               } catch (Throwable var19) {
                  if (var11 == null) {
                     var11 = var19;
                  } else if (var11 != var19) {
                     var11.addSuppressed(var19);
                  }

                  throw var11;
               }

               if (taskRef != null) {
                  taskRef.setServerGuid(clusterRef.getServerGuid());
               }

               result.add(QueryUtil.createResultItem("vumVsanImportFirmware", taskRef, clusterRef));
            }

            return (ResultItem[])result.toArray(new ResultItem[refs.length]);
         }
      } else {
         logger.warn("Missing importFirmware parameter spec.");
         return new ResultItem[0];
      }
   }

   private ResultItem[] installVibFromChecksum(ManagedObjectReference[] refs, ParameterSpec[] parameterSpecs) throws Exception {
      if (parameterSpecs != null && parameterSpecs.length != 0) {
         List<VsanVibSpec> vibSpecs = new ArrayList();
         ParameterSpec[] var7 = parameterSpecs;
         int var6 = parameterSpecs.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            ParameterSpec spec = var7[var5];
            if (spec.propertyName.equals("vumVsanInstallVendorToolFromChecksum")) {
               String checksum = (String)spec.parameter;
               vibSpecs = this.getVibSpecs(refs, checksum);
               break;
            }
         }

         if (((List)vibSpecs).size() == 0) {
            return new ResultItem[0];
         } else {
            ArrayList<ResultItem> result = new ArrayList();
            Throwable var21 = null;
            Object var22 = null;

            try {
               VsanProfiler.Point p = VsanComponentsProvider._profiler.point("updateManager.vsanVibInstall");

               try {
                  ManagedObjectReference[] var11 = refs;
                  int var10 = refs.length;

                  for(int var9 = 0; var9 < var10; ++var9) {
                     ManagedObjectReference clusterRef = var11[var9];
                     VsanUpdateManager updateManager = VsanProviderUtils.getUpdateManager(clusterRef);
                     ManagedObjectReference updateTask = updateManager.vsanVibInstall(clusterRef, (VsanVibSpec[])((List)vibSpecs).toArray(new VsanVibSpec[0]), (VsanVibScanResult[])null, (VsanHclFirmwareUpdateSpec[])null, (MaintenanceSpec)null, false, false);
                     updateTask.setServerGuid(clusterRef.getServerGuid());
                     result.add(QueryUtil.createResultItem("vumVsanInstallVendorToolFromChecksum", updateTask, clusterRef));
                  }
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var19) {
               if (var21 == null) {
                  var21 = var19;
               } else if (var21 != var19) {
                  var21.addSuppressed(var19);
               }

               throw var21;
            }

            return (ResultItem[])result.toArray(new ResultItem[refs.length]);
         }
      } else {
         logger.warn("Missing checksum parameter spec.");
         return new ResultItem[0];
      }
   }

   private ResultItem[] installVib(ManagedObjectReference[] refs, ParameterSpec[] parameterSpecs) throws Exception {
      if (parameterSpecs != null && parameterSpecs.length != 0) {
         Map vendorVibSpec = null;
         ParameterSpec[] var7 = parameterSpecs;
         int var6 = parameterSpecs.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            ParameterSpec spec = var7[var5];
            if (spec.propertyName.equals("vumVsanInstallVendorTool")) {
               vendorVibSpec = (Map)spec.parameter;
               break;
            }
         }

         if (vendorVibSpec == null) {
            logger.warn("Unable to find supplied vendor vib specs.");
            return new ResultItem[0];
         } else {
            ArrayList<ResultItem> result = new ArrayList();
            Throwable var21 = null;
            Object var22 = null;

            try {
               VsanProfiler.Point p = VsanComponentsProvider._profiler.point("updateManager.vsanVibInstall");

               try {
                  ManagedObjectReference[] var11 = refs;
                  int var10 = refs.length;

                  for(int var9 = 0; var9 < var10; ++var9) {
                     ManagedObjectReference clusterRef = var11[var9];
                     VsanUpdateManager updateManager = VsanProviderUtils.getUpdateManager(clusterRef);
                     ManagedObjectReference updateTask = updateManager.vsanVibInstall(clusterRef, new VsanVibSpec[]{this.toVibSpec(vendorVibSpec)}, (VsanVibScanResult[])null, (VsanHclFirmwareUpdateSpec[])null, (MaintenanceSpec)null, false, false);
                     updateTask.setServerGuid(clusterRef.getServerGuid());
                     result.add(QueryUtil.createResultItem("vumVsanInstallVendorTool", updateTask, clusterRef));
                  }
               } finally {
                  if (p != null) {
                     p.close();
                  }

               }
            } catch (Throwable var19) {
               if (var21 == null) {
                  var21 = var19;
               } else if (var21 != var19) {
                  var21.addSuppressed(var19);
               }

               throw var21;
            }

            return (ResultItem[])result.toArray(new ResultItem[refs.length]);
         }
      } else {
         logger.warn("Missing vib spec parameter spec.");
         return new ResultItem[0];
      }
   }

   private List<VsanVibSpec> getVibSpecs(ManagedObjectReference[] refs, String checksum) throws Exception {
      List<VsanVibSpec> result = new ArrayList();
      ManagedObjectReference[] var7 = refs;
      int var6 = refs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference clusterRef = var7[var5];
         VsanClusterHclInfo hclInfo = this.getHclInfo(clusterRef);
         if (hclInfo != null && hclInfo.updateItems != null) {
            VsanUpdateItem[] var12;
            int var11 = (var12 = hclInfo.updateItems).length;

            label55:
            for(int var10 = 0; var10 < var11; ++var10) {
               VsanUpdateItem updateItem = var12[var10];
               if (updateItem.vibType != null && updateItem.vibType.equals("tool") && updateItem.downloadInfo != null) {
                  VsanDownloadItem[] var16;
                  int var15 = (var16 = updateItem.downloadInfo).length;

                  for(int var14 = 0; var14 < var15; ++var14) {
                     VsanDownloadItem downloadItem = var16[var14];
                     if (downloadItem.sha1sum.equals(checksum) && updateItem.vibSpec != null) {
                        VsanVibSpec[] var20;
                        int var19 = (var20 = updateItem.vibSpec).length;
                        int var18 = 0;

                        while(true) {
                           if (var18 >= var19) {
                              continue label55;
                           }

                           VsanVibSpec vibSpec = var20[var18];
                           result.add(vibSpec);
                           ++var18;
                        }
                     }
                  }
               }
            }
         }
      }

      return result;
   }

   private ResultItem[] downloadAndInstallTools(ManagedObjectReference[] refs) throws Exception {
      ArrayList<ResultItem> result = new ArrayList();
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = VsanComponentsProvider._profiler.point("updateManager.downloadAndInstall");

         try {
            ManagedObjectReference[] var9 = refs;
            int var8 = refs.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               ManagedObjectReference clusterRef = var9[var7];
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(clusterRef);
               ManagedObjectReference updateTask = healthSystem.downloadAndInstallVendorTool(clusterRef);
               updateTask.setServerGuid(clusterRef.getServerGuid());
               result.add(QueryUtil.createResultItem("vumVsanDownloadInstallVendorTool", updateTask, clusterRef));
            }
         } finally {
            if (p != null) {
               p.close();
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

      return (ResultItem[])result.toArray(new ResultItem[refs.length]);
   }

   private ResultItem[] getUpdates(ManagedObjectReference[] refs) throws Exception {
      ProxygenSerializer serializer = new ProxygenSerializer();
      ArrayList<ResultItem> result = new ArrayList();
      ManagedObjectReference[] var7 = refs;
      int var6 = refs.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         ManagedObjectReference clusterRef = var7[var5];
         VsanClusterHclInfo hclInfo = this.getHclInfo(clusterRef);
         Map data = (Map)serializer.serialize(hclInfo);
         result.add(QueryUtil.createResultItem("updates", data, clusterRef));
      }

      return (ResultItem[])result.toArray(new ResultItem[refs.length]);
   }

   private VsanClusterHclInfo getHclInfo(ManagedObjectReference param1) throws Exception {
      // $FF: Couldn't be decompiled
   }

   private VsanVibSpec toVibSpec(Map specMap) {
      VsanVibSpec spec = new VsanVibSpec();
      Map morefMap = (Map)specMap.get("host");
      spec.host = new ManagedObjectReference((String)morefMap.get("type"), (String)morefMap.get("value"), (String)morefMap.get("serverGuid"));
      spec.metaUrl = (String)specMap.get("metaUrl");
      spec.metaSha1Sum = (String)specMap.get("metaSha1Sum");
      spec.vibUrl = (String)specMap.get("vibUrl");
      spec.vibSha1Sum = (String)specMap.get("vibSha1Sum");
      return spec;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[VumBaselineRecommendationType.values().length];

         try {
            var0[VumBaselineRecommendationType.latestPatch.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[VumBaselineRecommendationType.latestRelease.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[VumBaselineRecommendationType.noRecommendation.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$data$VumBaselineRecommendationType = var0;
         return var0;
      }
   }
}
