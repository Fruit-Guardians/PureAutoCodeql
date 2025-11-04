package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vise.usersession.UserSession;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenStoreException;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.TokenStoreVcAuth;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class VcClient {
   @Autowired
   @Qualifier("vcFactory")
   private ResourceFactory<VcConnection, VlsiExploratorySettings> vcFactory;
   @Autowired
   @Qualifier("vsanVcFactory")
   private ResourceFactory<VcConnection, VlsiExploratorySettings> vsanVcFactory;
   @Autowired
   @Qualifier("vlsiSettingsTemplate")
   private VlsiSettings vlsiSettingsTemplate;
   @Autowired
   private LookupSvcClient lsClient;
   @Autowired
   private SsoClient ssoClient;
   @Autowired
   private UserSessionService sessionService;

   public VcConnection getConnection(String uuid) {
      return this.getConnection(uuid, (LookupSvcInfo)null);
   }

   public VcConnection getConnection(String uuid, LookupSvcInfo lsInfo) {
      return this.getConnection(uuid, lsInfo, this.vcFactory);
   }

   public VcConnection getVsanVmodlVersionConnection(String uuid) {
      return this.getConnection(uuid, (LookupSvcInfo)null, this.vsanVcFactory);
   }

   private VcConnection getConnection(String uuid, LookupSvcInfo lsInfo, ResourceFactory<VcConnection, VlsiExploratorySettings> vcFactory) {
      UserSession userSession = this.sessionService.getUserSession();
      String sessionLocale = userSession != null ? userSession.locale : null;
      VlsiExploratorySettings exploratorySettings = new VlsiExploratorySettings(this.vlsiSettingsTemplate.setAuthenticator(new TokenStoreVcAuth(sessionLocale, this.ssoClient.getTokenStore(), uuid)), this.lsClient.getProducerFactory(), this.lsClient.getSettings(lsInfo), UUID.fromString(uuid));

      try {
         VcConnection connection = (VcConnection)vcFactory.acquire(exploratorySettings);
         return connection;
      } catch (TokenStoreException var8) {
         throw new NotAuthenticatedException(Utils.getLocalizedString("vsan.sessionmanager.siteNotAuthenticated"), var8);
      } catch (Exception var9) {
         throw new NotAccessibleException(Utils.getLocalizedString("vsan.sessionmanager.siteNotAccessible"), var9);
      }
   }
}
