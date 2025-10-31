package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.client.http.HttpClientConfiguration;
import com.vmware.vim.vmomi.client.http.HttpConfiguration;
import com.vmware.vim.vmomi.client.http.HttpConfiguration.Factory;
import com.vmware.vim.vmomi.core.types.VmodlVersion;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import java.io.Closeable;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpFactory implements ResourceFactory<ClientCfg, HttpSettings> {
   private static Logger logger = LoggerFactory.getLogger(AbstractConnectionFactory.class);

   public ClientCfg acquire(HttpSettings id) {
      HttpConfiguration result = Factory.newInstance();
      if (id.isViaProxy()) {
         result.setDefaultProxy(id.getProxyHost(), id.getProxyPort(), id.getProxyProto());
      }

      if (id.getMaxConn() > 0) {
         result.setMaxConnections(id.getMaxConn());
         result.setDefaultMaxConnectionsPerRoute(id.getMaxConn());
      }

      if (id.getTimeout() > 0) {
         result.setTimeoutMs(id.getTimeout());
         result.setConnectTimeoutMs(id.getTimeout());
      }

      if (id.getTrustStore() != null) {
         result.getKeyStoreConfig().setTrustStorePassword(id.getTrustStore().getKeystorePass());
         result.getKeyStoreConfig().setKeyStorePath(id.getTrustStore().getKeystorePath());
         result.setTrustStore(id.getTrustStore().getKeystore());
      }

      if (id.getClientCert() != null) {
         ClientCertificate cert = id.getClientCert();
         result.getKeyStoreConfig().setKeyAlias(cert.getKeystoreAlias());
         result.getKeyStoreConfig().setKeyPassword(cert.getKeyPass());
         result.setKeyStore(cert.getKeystore());
      }

      if (id.getThumbprintVerifier() != null) {
         result.setThumbprintVerifier(id.getThumbprintVerifier());
      }

      HttpClientConfiguration red = com.vmware.vim.vmomi.client.http.HttpClientConfiguration.Factory.newInstance();
      red.setHttpConfiguration(result);
      red.setExecutor((Executor)id.getExecutorFactory().acquire(id.getExecutorSettings()));
      if (id.getRequestProperties() != null) {
         red.setRequestContextProvider(new HttpRequestContextProvider(id.getRequestProperties()));
      }

      VmodlVersion vmodlVersion = id.getVmodlContext().getVmodlVersionMap().getVersion(id.getVersion());
      Client cl = com.vmware.vim.vmomi.client.Client.Factory.createClient(id.makeUri(), vmodlVersion.getVersionClass(), id.getVmodlContext(), red);
      final ClientCfg res = new ClientCfg(red, cl);
      res.setCloseHandler(new Runnable() {
         public void run() {
            HttpFactory.this.release(res);
         }
      });
      return res;
   }

   private void release(ClientCfg resource) {
      resource.getExtraClient().shutdown();
      Closeable pool = (Closeable)resource.getClientConfig().getExecutor();
      if (pool != null) {
         try {
            pool.close();
         } catch (Exception var4) {
            logger.warn("Ignoring problem when releasing thread pool", var4);
         }
      }

   }
}
