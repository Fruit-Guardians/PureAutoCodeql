package com.vmware.vsan.client.services.common;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.option.OptionManager;
import com.vmware.vim.binding.vim.option.OptionValue;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.Iterator;
import org.apache.commons.lang.ArrayUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CeipService {
   private Logger logger = LoggerFactory.getLogger(CeipService.class);
   @Autowired
   private VcClient vcClient;

   @TsService
   public boolean getCeipServiceEnabled(ManagedObjectReference clusterRef) throws Exception {
      Throwable var3 = null;
      ObjectMapper mapper = null;

      OptionValue[] optionValues;
      try {
         VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid());

         try {
            OptionManager optionManager = (OptionManager)vcConnection.createStub(OptionManager.class, vcConnection.getContent().setting);
            optionValues = optionManager.queryView("VirtualCenter.DataCollector.ConsentData");
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }
      } catch (Throwable var18) {
         if (var3 == null) {
            var3 = var18;
         } else if (var3 != var18) {
            var3.addSuppressed(var18);
         }

         throw var3;
      }

      if (ArrayUtils.isEmpty(optionValues)) {
         return false;
      } else {
         OptionValue optionValue = optionValues[0];
         mapper = new ObjectMapper();

         try {
            JsonNode rootNode = mapper.readTree((String)optionValue.value);
            JsonNode consentConfigNodes = rootNode.get("consentConfigurations");
            if (consentConfigNodes == null) {
               return false;
            } else {
               Iterator consentNodesIterator = consentConfigNodes.getElements();

               while(consentNodesIterator.hasNext()) {
                  JsonNode consentNode = (JsonNode)consentNodesIterator.next();
                  JsonNode consentIdNode = consentNode.get("consentId");
                  if (consentIdNode != null && consentIdNode.getIntValue() == 2) {
                     JsonNode consentEnabledNode = consentNode.get("consentAccepted");
                     if (consentEnabledNode != null) {
                        return consentEnabledNode.getBooleanValue();
                     }
                     break;
                  }
               }

               return false;
            }
         } catch (Exception var19) {
            this.logger.error("Error parsing the information for CEIP service enabled", var19);
            return true;
         }
      }
   }
}
