package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.google.common.collect.ImmutableMap;
import com.vmware.vim.binding.pbm.version.version11;
import com.vmware.vise.usersession.ServerInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PbmClient {
   private static final String PBM_ENDPOINT_PATH = "/pbm/sdk";
   @Autowired
   private VcService vcService;
   @Autowired
   @Qualifier("pbmFactory")
   private ResourceFactory<PbmConnection, VlsiSettings> producerFactory;
   @Autowired
   @Qualifier("vlsiSettingsTemplate")
   private VlsiSettings vlsiSettingsTemplate;

   public PbmConnection getConnection(String uuid) {
      try {
         ServerInfo serverInfo = this.vcService.findServerInfo(uuid);
         URI vcEndpoint = URI.create(serverInfo.serviceUrl);
         URI pbmEndpoint = new URI(vcEndpoint.getScheme(), (String)null, vcEndpoint.getHost(), vcEndpoint.getPort(), "/pbm/sdk", (String)null, (String)null);
         HttpSettings httpSettings = this.vlsiSettingsTemplate.getHttpSettings().setRequestProperties(ImmutableMap.of("vcSessionCookie", serverInfo.sessionCookie));
         VlsiSettings vlsiSettings = this.vlsiSettingsTemplate.setHttpSettings(httpSettings).setServiceInfo(pbmEndpoint, version11.class).setSslContext((ClientCertificate)null, new SingleThumbprintVerifier(serverInfo.thumbprint));
         return (PbmConnection)this.producerFactory.acquire(vlsiSettings);
      } catch (Exception var7) {
         throw new NotAccessibleException(var7);
      }
   }
}
