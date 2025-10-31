package com.vmware.vsan.client.services.obfuscation;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.VsanPhoneHomeSystem;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import com.vmware.vsphere.client.vsan.base.util.VsanProviderUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(
   value = {"/support/obfuscation"},
   method = {RequestMethod.GET}
)
public class ObfuscationController {
   private Logger logger = LoggerFactory.getLogger(ObfuscationController.class);
   private static final VsanProfiler _profiler = new VsanProfiler(ObfuscationController.class);
   @Autowired
   private ObjectReferenceService objRefService;

   @RequestMapping(
      value = {"/{operationType}/{objectId}"},
      method = {RequestMethod.GET}
   )
   public void downloadObfuscationMap(@PathVariable("operationType") String operationType, @PathVariable("objectId") String objectId, HttpServletResponse response) throws Exception {
      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            VsanProfiler.Point point = _profiler.point("phoneHomeSystem.vsanGetPhoneHomeObfuscationMap");

            try {
               ManagedObjectReference clusterRef = (ManagedObjectReference)this.objRefService.getReference(objectId);
               VsanPhoneHomeSystem phoneHomeSystem = VsanProviderUtils.getVsanPhoneHomeSystem(clusterRef);
               String obfuscationMap = phoneHomeSystem.vsanGetPhoneHomeObfuscationMap(clusterRef);
               if ("view".equals(operationType)) {
                  response.setContentType("text/plain");
                  response.setHeader("Content-Disposition", "inline");
               } else {
                  response.setContentType("application/text");
                  response.setHeader("Content-Disposition", "attachment; filename=\"obfuscatedMap.txt\"");
               }

               response.setContentLength(obfuscationMap.getBytes().length);
               InputStream in = new ByteArrayInputStream(obfuscationMap.getBytes());
               IOUtils.copy(in, response.getOutputStream());
               in.close();
               response.flushBuffer();
            } finally {
               if (point != null) {
                  point.close();
               }

            }

         } catch (Throwable var18) {
            if (var4 == null) {
               var4 = var18;
            } else if (var4 != var18) {
               var4.addSuppressed(var18);
            }

            throw var4;
         }
      } catch (Exception var19) {
         this.logger.error("Failed to download the obfuscation map data.", var19);
         throw var19;
      }
   }
}
