package com.vmware.vsan.client.services.update;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanClusterHclInfo;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterHealthSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVumSystem;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVumSystemConfig;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import com.vmware.vsphere.client.vsan.health.HclUpdateOfflineSpec;
import com.vmware.vsphere.client.vsan.impl.VsanPropertyProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sun.misc.BASE64Encoder;

@Component
public class UpdateDbService {
   private static final VsanProfiler profiler = new VsanProfiler(UpdateDbService.class);
   private static final Log _logger = LogFactory.getLog(UpdateDbService.class);
   @Autowired
   private VsanPropertyProvider vsanPropertyProvider;

   @TsService
   public Date getHclLastUpdatedDate(ManagedObjectReference vcRef) throws Exception {
      ManagedObjectReference clusterRef = null;
      if (!VsanCapabilityUtils.isGetHclLastUpdateOnVcSupported(vcRef)) {
         clusterRef = this.vsanPropertyProvider.getAnyVsanCluster(vcRef);
         if (clusterRef == null) {
            _logger.warn("Cannot find a cluster on the VC: " + vcRef);
            return null;
         }
      }

      VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(vcRef);
      if (healthSystem == null) {
         return null;
      } else {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point point = profiler.point("UpdateDbService.getHclLastUpdate");

            Throwable var10000;
            label339: {
               label342: {
                  VsanClusterHclInfo hclInfo;
                  boolean var10001;
                  try {
                     hclInfo = healthSystem.getClusterHclInfo(clusterRef, false, false, (String)null);
                     if (hclInfo == null) {
                        break label342;
                     }
                  } catch (Throwable var26) {
                     var10000 = var26;
                     var10001 = false;
                     break label339;
                  }

                  Date var28;
                  try {
                     var28 = this.convertUtcDate(hclInfo.getHclDbLastUpdate());
                  } catch (Throwable var25) {
                     var10000 = var25;
                     var10001 = false;
                     break label339;
                  }

                  if (point != null) {
                     point.close();
                  }

                  try {
                     return var28;
                  } catch (Throwable var24) {
                     var10000 = var24;
                     var10001 = false;
                     break label339;
                  }
               }

               if (point != null) {
                  point.close();
               }

               return null;
            }

            var4 = var10000;
            if (point != null) {
               point.close();
            }

            throw var4;
         } catch (Throwable var27) {
            if (var4 == null) {
               var4 = var27;
            } else if (var4 != var27) {
               var4.addSuppressed(var27);
            }

            throw var4;
         }
      }
   }

   @TsService
   public Date getReleaseCatalogLastUpdatedDate(ManagedObjectReference moRef) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = profiler.point("UpdateDbService.getVsanVumConfig");

         Throwable var10000;
         label173: {
            boolean var10001;
            Date var19;
            try {
               VsanVumSystem vumSystem = VsanProviderUtils.getVsanVumSystem(moRef);
               VsanVumSystemConfig vumSystemConfig = vumSystem.getVsanVumConfig();
               var19 = this.convertUtcDate(vumSystemConfig.releaseDbLastUpdate);
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

         var2 = var10000;
         if (p != null) {
            p.close();
         }

         throw var2;
      } catch (Throwable var18) {
         if (var2 == null) {
            var2 = var18;
         } else if (var2 != var18) {
            var2.addSuppressed(var18);
         }

         throw var2;
      }
   }

   @TsService
   public void updateHclDbFromWeb(ManagedObjectReference entity) throws Exception {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VsanProfiler.Point p = profiler.point("UpdateDbService.updateHclDbFromWeb");

         try {
            VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(entity);
            healthSystem.updateHclDbFromWeb((String)null);
         } finally {
            if (p != null) {
               p.close();
            }

         }

      } catch (Throwable var11) {
         if (var2 == null) {
            var2 = var11;
         } else if (var2 != var11) {
            var2.addSuppressed(var11);
         }

         throw var2;
      }
   }

   @TsService
   public boolean uploadHclDb(ManagedObjectReference entity, HclUpdateOfflineSpec spec) throws Exception {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VsanProfiler.Point p = profiler.point("UpdateDbService.uploadHclDb");

         Throwable var10000;
         label173: {
            boolean var10001;
            boolean var19;
            try {
               VsanVcClusterHealthSystem healthSystem = VsanProviderUtils.getVsanVcClusterHealthSystem(entity);
               var19 = healthSystem.uploadHclDb(this.generateGzipBase64EncodedString(this.decompressZlibByteArrayString(spec.hclDatabaseFileZlibCompressedContent)));
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
   public void uploadReleaseDb(ManagedObjectReference entity, byte[] data) throws Exception {
      if (!VsanCapabilityUtils.isUpdateVumReleaseCatalogOfflineSupported(entity)) {
         throw new VsanUiLocalizableException("vsan.common.error.notSupported");
      } else {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VsanProfiler.Point p = profiler.point("UpdateDbService.uploadReleaseDb");

            try {
               VsanVumSystem vumSystem = VsanProviderUtils.getVsanVumSystem(entity);
               String releaseDb = new String(this.decompressZlibByteArrayString(data));
               vumSystem.uploadReleaseDb(releaseDb);
            } finally {
               if (p != null) {
                  p.close();
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
      }
   }

   private Date convertUtcDate(Calendar date) {
      if (date != null) {
         date.add(14, date.getTimeZone().getRawOffset());
      }

      return date == null ? null : date.getTime();
   }

   private String generateGzipBase64EncodedString(byte[] fileByteArray) throws Exception {
      Validate.notNull(fileByteArray);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      GZIPOutputStream gzipos = new GZIPOutputStream(baos);

      try {
         gzipos.write(fileByteArray);
      } catch (Exception var8) {
         throw var8;
      } finally {
         gzipos.close();
         baos.close();
      }

      BASE64Encoder encoder = new BASE64Encoder();
      return encoder.encode(baos.toByteArray());
   }

   private byte[] decompressZlibByteArrayString(byte[] zlibByteArray) throws VsanUiLocalizableException, DataFormatException, IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(zlibByteArray.length);
      Inflater decompressor = new Inflater();

      try {
         decompressor.setInput(zlibByteArray);
         byte[] buf = new byte[1024];

         while(!decompressor.finished()) {
            int count = decompressor.inflate(buf);
            if (count == 0 && decompressor.needsInput()) {
               throw new VsanUiLocalizableException("vsan.update.catalog.content.error");
            }

            baos.write(buf, 0, count);
         }
      } finally {
         decompressor.end();
         baos.close();
      }

      return baos.toByteArray();
   }
}
