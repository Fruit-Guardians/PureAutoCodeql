package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.client.Client.Factory;
import com.vmware.vim.vmomi.client.common.ProtocolBinding;
import com.vmware.vim.vmomi.client.common.Session;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.ClientCfg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConnectionFactory<R extends VlsiConnection, S extends VlsiSettings> implements ResourceFactory<R, S> {
   private static Logger logger = LoggerFactory.getLogger(AbstractConnectionFactory.class);

   public R acquire(S settings) {
      VlsiConnection result = this.buildConnection(settings);

      try {
         this.onPreConnect(settings, result);
         logger.trace("Opening HTTP connection.");
         result.setClientConfig((ClientCfg)settings.getHttpFactory().acquire(settings.getHttpSettings()));
         logger.trace("Initializing VLSI client.");
         result.setClient(this.makeClient(settings, result));
         this.onConnect(settings, result);
         logger.trace("Authenticating connection.");
         settings.getAuthenticator().login(result);
      } catch (Exception var4) {
         result.close();
         CheckedRunnable.handle(var4);
      }

      logger.debug("Created connection: {}", result);
      return result;
   }

   protected void release(S settings, R resource) {
      try {
         if (settings.getAuthenticator() != null) {
            settings.getAuthenticator().logout(resource);
         }
      } catch (Exception var6) {
         logger.warn("Ignoring unsuccessful logout", var6);
      }

      try {
         resource.getClient().shutdown();
      } catch (Exception var5) {
         logger.warn("Ignoring problem when releasing client", var5);
      }

      try {
         if (resource.getClientConfig() != null) {
            resource.getClientConfig().close();
         }
      } catch (Exception var4) {
         logger.warn("Ignoring problem when releasing HttpConfig", var4);
      }

      logger.debug("Closed connection: {}", resource);
   }

   protected abstract R buildConnection(S var1);

   protected void onPreConnect(final S settings, final R connection) {
      connection.setCloseHandler(new Runnable() {
         public void run() {
            AbstractConnectionFactory.this.release(settings, connection);
         }
      });
   }

   protected void onConnect(S settings, R connection) {
      connection.settings = settings;
   }

   protected Client makeClient(S settings, VlsiConnection connection) {
      Client client = Factory.createClient(settings.getHttpSettings().makeUri(), settings.getHttpSettings().getVersion(), settings.getHttpSettings().getVmodlContext(), connection.getClientConfig().getClientConfig());
      if (settings.getSessionCookie() != null) {
         ProtocolBinding binding = client.getBinding();
         Session session = binding.createSession(settings.getSessionCookie());
         binding.setSession(session);
      }

      return client;
   }
}
