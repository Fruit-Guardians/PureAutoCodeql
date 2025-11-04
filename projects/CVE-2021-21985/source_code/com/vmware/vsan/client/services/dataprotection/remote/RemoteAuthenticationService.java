package com.vmware.vsan.client.services.dataprotection.remote;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.sso.client.TokenSpec;
import com.vmware.vim.sso.client.TokenSpec.Builder;
import com.vmware.vim.sso.client.TokenSpec.DelegationSpec;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.SsoClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SniffingThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.IllegalAuthInfoException;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore.TokenRetriever;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RemoteAuthenticationService {
   @Autowired
   private SsoClient ssoClient;
   @Autowired
   private LookupSvcClient lsClient;

   @TsService
   public String validateConnectionDetails(String host, Integer port, String username, String password) throws VsanUiLocalizableException {
      if (!StringUtils.isEmpty(host) && port != null && port >= 1) {
         if (StringUtils.isEmpty(username)) {
            throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.credentials.error");
         } else if (StringUtils.isEmpty(password)) {
            throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.credentials.error");
         } else {
            SniffingThumbprintVerifier sniffer = new SniffingThumbprintVerifier(true);
            this.getRemoteTokenRetriever(host, port, username, password, sniffer).shutdown();
            return sniffer.getSniffedThumbprint();
         }
      } else {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.address.error");
      }
   }

   @TsService
   public void authenticate(PscConnectionDetails pscDetails, String username, String password) throws VsanUiLocalizableException {
      SingleThumbprintVerifier thumbprintVerifier = new SingleThumbprintVerifier(pscDetails.pscThumbprint);
      TokenRetriever tokenRetriever = this.getRemoteTokenRetriever(pscDetails.pscHost, pscDetails.pscPort, username, password, thumbprintVerifier);
      Throwable var6 = null;
      Object var7 = null;

      try {
         LookupSvcConnection lsConn = this.lsClient.getConnection(LookupSvcInfo.from(pscDetails));

         try {
            Iterator var10 = (new VcLsExplorer(lsConn.getServiceRegistration())).list().iterator();

            while(var10.hasNext()) {
               VcRegistration vcRegistration = (VcRegistration)var10.next();
               this.ssoClient.authenticateSite(vcRegistration.getUuid().toString(), tokenRetriever);
            }
         } finally {
            if (lsConn != null) {
               lsConn.close();
            }

         }

      } catch (Throwable var16) {
         if (var6 == null) {
            var6 = var16;
         } else if (var6 != var16) {
            var6.addSuppressed(var16);
         }

         throw var6;
      }
   }

   private TokenRetriever getRemoteTokenRetriever(String host, Integer port, String username, String password, ThumbprintVerifier thumbprintVerifier) throws VsanUiLocalizableException {
      URI uri = null;

      try {
         uri = LookupSvcClient.createServiceUri(host, port);
      } catch (URISyntaxException var11) {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.address.error", var11);
      }

      try {
         LookupSvcInfo lsInfo = new LookupSvcInfo(uri, thumbprintVerifier);
         TokenSpec tokenSpec = (new Builder(100L)).renewable(false).delegationSpec(new DelegationSpec(false, (String)null)).createTokenSpec();
         return this.ssoClient.newRemoteTokenRetriever(lsInfo, username, password, tokenSpec);
      } catch (IllegalAuthInfoException var9) {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.credentials.error", var9);
      } catch (Exception var10) {
         throw new VsanUiLocalizableException("vsan.dataProtection.remote.psc.address.error", var10);
      }
   }
}
