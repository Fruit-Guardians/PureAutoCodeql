package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.client.common.Session;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanSessionCookieAuth extends Authenticator {
   private static final Log logger = LogFactory.getLog(VsanSessionCookieAuth.class);

   public void login(VlsiConnection connection) {
      Client client = connection.getClient();
      Session session = client.getBinding().createSession(connection.getSettings().getSessionCookie());
      client.getBinding().setSession(session);
   }

   public void logout(VlsiConnection connection) {
      try {
         Client client = connection.getClient();
         if (client != null) {
            client.shutdown();
         }
      } catch (Exception var3) {
         logger.error("Failed to shutdown vlsi client: " + var3.getMessage());
      }

   }
}
