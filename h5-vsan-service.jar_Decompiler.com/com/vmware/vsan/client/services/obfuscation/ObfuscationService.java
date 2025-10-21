package com.vmware.vsan.client.services.obfuscation;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vsan.client.services.common.CeipService;
import com.vmware.vsan.client.services.obfuscation.model.ObfuscationData;
import com.vmware.vsan.client.util.NoOpMeasure;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ObfuscationService {
   private static final VsanProfiler _profiler = new VsanProfiler(ObfuscationService.class);
   @Autowired
   private CeipService ceipService;

   @TsService
   public ObfuscationData getObfuscationData(ManagedObjectReference clusterRef) throws Exception {
      ObfuscationData data = new ObfuscationData();

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point point = _profiler.point("phoneHomeSystem.vsanGetPhoneHomeObfuscationMap");

            try {
               Future<String> obfuscationFuture = (new NoOpMeasure()).newFuture("String");
               VsanProviderUtils.getVsanPhoneHomeSystem(clusterRef).vsanGetPhoneHomeObfuscationMap(clusterRef, obfuscationFuture);
               data.obfuscationMap = (String)obfuscationFuture.get();
               data.obfuscationSupported = true;
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
         data.obfuscationSupported = false;
      }

      data.ceipEnabled = this.ceipService.getCeipServiceEnabled(clusterRef);
      data.clusterVsanConfigUuid = (String)QueryUtil.getProperty(clusterRef, "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.uuid", (Object)null);
      return data;
   }
}
