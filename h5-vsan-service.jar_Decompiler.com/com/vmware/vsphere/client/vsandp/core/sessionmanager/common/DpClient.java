package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.usersession.UserSession;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp.DpConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp.DpTokenAuthenticator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DpClient {
   @Autowired
   @Qualifier("dpFactory")
   private ResourceFactory<DpConnection, VlsiExploratorySettings> dpFactory;
   @Autowired
   @Qualifier("vlsiSettingsTemplate")
   private VlsiSettings vlsiSettingsTemplate;
   @Autowired
   private SsoClient ssoClient;
   @Autowired
   private LookupSvcClient lsClient;
   @Autowired
   private UserSessionService sessionService;

   public DpConnection getConnection(ManagedObjectReference clusterRef) {
      return this.getConnection(clusterRef, (LookupSvcInfo)null);
   }

   public DpConnection getConnection(ManagedObjectReference clusterRef, LookupSvcInfo lsInfo) {
      UserSession userSession = this.sessionService.getUserSession();
      String sessionLocale = userSession != null ? userSession.locale : null;
      Authenticator dpAuthenticator = new DpTokenAuthenticator(sessionLocale, this.ssoClient.getTokenStore(), clusterRef.getServerGuid());
      VlsiExploratorySettings dpSettings = new VlsiExploratorySettings(this.vlsiSettingsTemplate.setAuthenticator(dpAuthenticator), this.lsClient.getProducerFactory(), this.lsClient.getSettings(lsInfo), UUID.fromString(clusterRef.getServerGuid()));
      return (DpConnection)this.dpFactory.acquire(dpSettings);
   }
}
