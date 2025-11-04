package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vim.binding.vim.version.internal.version9;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.core.types.VmodlContext;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.CachedResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.ClientCfg;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.ServiceEndpoint;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.Token;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.CredentialsVcAuth;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.SdkTunnelHttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.SsoVcAuth;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.CloseableExecutorService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorSettings;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleContext implements Closeable {
   public static String VIMBASE = "com.vmware.vim.binding.vim";
   public static String LOOKUP = "com.vmware.vim.binding.lookup";
   public static String SSO_ADMIN = "com.vmware.vim.binding.sso";
   protected final String[] vmodlPackages;
   protected final ExecutorSettings executorConfig;
   protected final ResourceFactory<CloseableExecutorService, ExecutorSettings> executorFactory;
   protected final ResourceFactory<ClientCfg, HttpSettings> configFactory;
   protected final VmodlContext vmodlContext;

   public SimpleContext() {
      this(VIMBASE);
   }

   public SimpleContext(String... vmodlPackages) {
      this(true, vmodlPackages);
   }

   public SimpleContext(boolean lazyInit, String... vmodlPackages) {
      this.vmodlPackages = vmodlPackages;
      this.executorConfig = this.mkExecutorSettings();
      this.executorFactory = this.mkExecutorFactory();
      this.configFactory = this.mkConfigFactory();
      this.vmodlContext = VmodlContext.createContext(vmodlPackages, lazyInit);
   }

   protected ExecutorSettings mkExecutorSettings() {
      return new ExecutorSettings(3, 30, 30L, TimeUnit.SECONDS);
   }

   protected ResourceFactory<CloseableExecutorService, ExecutorSettings> mkExecutorFactory() {
      return new CachedResourceFactory(new ExecutorFactory());
   }

   protected ResourceFactory<ClientCfg, HttpSettings> mkConfigFactory() {
      return new CachedResourceFactory(new HttpFactory());
   }

   public ExecutorSettings getExecutorConfig() {
      return this.executorConfig;
   }

   public ResourceFactory<CloseableExecutorService, ExecutorSettings> getExecutorFactory() {
      return this.executorFactory;
   }

   public ResourceFactory<ClientCfg, HttpSettings> getConfigFactory() {
      return this.configFactory;
   }

   protected Class<?> getVcVmodlVersion() {
      return version9.class;
   }

   public VlsiSettings makeSettings(URI uri, ThumbprintVerifier verifier, Class<?> version) {
      HttpSettings httpSettings = new HttpSettings(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath(), (String)null, (String)null, -1, 10, 30000, (ClientCertificate)null, (ClientCertificate)null, verifier, this.executorFactory, this.executorConfig, version, this.vmodlContext, Collections.emptyMap());
      return new VlsiSettings(this.configFactory, httpSettings, new Authenticator(), (String)null);
   }

   public VlsiSettings makeSettings(ServiceEndpoint endpoint, Class<?> version) {
      return this.makeSettings(endpoint.getUri(), new SingleThumbprintVerifier(endpoint.getThumbprint()), version);
   }

   public VlsiSettings makeVcSettings(String host, String user, String passwd, ThumbprintVerifier verifier) {
      SdkTunnelHttpSettings vcSettings = new SdkTunnelHttpSettings(host, 80, 100, 30000, verifier, this.executorFactory, this.executorConfig, this.getVcVmodlVersion(), this.vmodlContext);
      return new VlsiSettings(this.configFactory, vcSettings, new CredentialsVcAuth(user, passwd, (String)null), (String)null);
   }

   public VlsiSettings makeVcSettings(String host, String user, String passwd, String thumbprint) {
      return this.makeVcSettings(host, user, passwd, (ThumbprintVerifier)(new SingleThumbprintVerifier(thumbprint)));
   }

   public VlsiSettings makeVcSettings(String host, Token token, ThumbprintVerifier verifier) {
      SdkTunnelHttpSettings vcSettings = new SdkTunnelHttpSettings(host, 80, 100, 30000, verifier, this.executorFactory, this.executorConfig, this.getVcVmodlVersion(), this.vmodlContext);
      return new VlsiSettings(this.configFactory, vcSettings, new SsoVcAuth(token.getKey(), token.getSaml(), (String)null), (String)null);
   }

   public VmodlContext getVmodlContext() {
      return this.vmodlContext;
   }

   public void shutdown() {
      try {
         if (this.configFactory instanceof CachedResourceFactory) {
            ((CachedResourceFactory)this.configFactory).shutdown();
         }
      } catch (Exception var3) {
         var3.printStackTrace();
      }

      try {
         if (this.executorFactory instanceof CachedResourceFactory) {
            ((CachedResourceFactory)this.executorFactory).shutdown();
         }
      } catch (Exception var2) {
         var2.printStackTrace();
      }

   }

   public void close() throws IOException {
      this.shutdown();
   }
}
