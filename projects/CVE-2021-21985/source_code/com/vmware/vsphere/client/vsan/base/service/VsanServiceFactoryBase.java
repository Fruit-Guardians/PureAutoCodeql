package com.vmware.vsphere.client.vsan.base.service;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.client.common.Session;
import com.vmware.vim.vmomi.client.http.HttpClientConfiguration;
import com.vmware.vim.vmomi.client.http.HttpConfiguration;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.client.http.HttpClientConfiguration.Factory;
import com.vmware.vim.vmomi.client.http.impl.HttpConfigurationImpl;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
import com.vmware.vise.security.ClientSessionEndListener;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.VersionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcService;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class VsanServiceFactoryBase<T> implements ClientSessionEndListener {
   protected static final Log _logger = LogFactory.getLog(VsanServiceFactoryBase.class);
   protected static final String VC_SESSION_COOKIE = "Cookie";
   private ConcurrentHashMap<String, T> sessionContext = new ConcurrentHashMap();
   private ExecutorService _threadPoolExecutor;
   private UserSessionService _userSessionService;
   private ServiceBundleActivator _bundleActivator;
   @Autowired
   private VcService vcService;
   @Autowired
   private VersionService versionService;

   public void setThreadPoolExecutor(ExecutorService executor) {
      this._threadPoolExecutor = executor;
   }

   public void setUserSessionService(UserSessionService userSessionService) {
      this._userSessionService = userSessionService;
   }

   public void setBundleActivator(ServiceBundleActivator bundleActivator) {
      this._bundleActivator = bundleActivator;
   }

   protected ServiceBundleActivator getBundleActivator() {
      return this._bundleActivator;
   }

   public T getService(String vcGuid) {
      String key = this.getSessionKey(vcGuid);
      T result = this.sessionContext.get(key);
      if (result == null) {
         T newEntity = this.create(vcGuid);
         result = this.sessionContext.putIfAbsent(key, newEntity);
         if (result == null) {
            result = newEntity;
         } else {
            this.destroy(newEntity);
         }
      }

      return result;
   }

   public String getSessionKey(String vcGuid) {
      return vcGuid == null ? null : vcGuid + ":" + this._userSessionService.getUserSession().clientId;
   }

   protected Client createClient(String vcGuid, String serviceDir, Class<?> version) {
      HttpConfiguration httpConfiguration = new HttpConfigurationImpl();
      ThumbprintVerifier sslThumbprintVerifier = new SingleThumbprintVerifier(this.vcService.findServerInfo(vcGuid).thumbprint);
      httpConfiguration.setThumbprintVerifier(sslThumbprintVerifier);
      HttpClientConfiguration httpClientConfiguration = Factory.newInstance();
      httpClientConfiguration.setExecutor(this._threadPoolExecutor);
      httpClientConfiguration.setHttpConfiguration(httpConfiguration);
      URI serviceUri = null;

      try {
         serviceUri = this.getServiceLocation(vcGuid, serviceDir);
      } catch (Exception var10) {
         _logger.error(var10);
      }

      Client client = com.vmware.vim.vmomi.client.Client.Factory.createClient(serviceUri, version, this._bundleActivator.getVmodlContext(), httpClientConfiguration);
      return client;
   }

   protected Client createClient(String vcGuid, String serviceDir) {
      Class<?> version = this.versionService.getVsanVmodlVersion(this.vcService.findServerInfo(vcGuid).serviceUrl);
      _logger.info("Using VMODL version: " + version.getName());
      return this.createClient(vcGuid, serviceDir, version);
   }

   protected RequestContext prepareSessionContext(String vcGuid, Client client) {
      String sessionCookie = this.vcService.findServerInfo(vcGuid).sessionCookie;
      Session session = client.getBinding().createSession(sessionCookie);
      client.getBinding().setSession(session);
      RequestContext requestContext = new RequestContextImpl();
      requestContext.put("Cookie", sessionCookie);
      return requestContext;
   }

   private URI getServiceLocation(String vcGuid, String serviceDir) throws Exception {
      String vcServiceUrl = this.vcService.findServerInfo(vcGuid).serviceUrl;
      if (StringUtils.isEmpty(vcServiceUrl)) {
         _logger.error("getServiceLocation: Failed to retrieve the VC URL.");
         throw new Exception("Failed to retrieve VC service Url");
      } else {
         try {
            URL vcUrl = new URL(vcServiceUrl);
            URL vsanHealthUrl = new URL(vcUrl.getProtocol(), vcUrl.getHost(), vcUrl.getPort(), serviceDir);
            return vsanHealthUrl.toURI();
         } catch (MalformedURLException | URISyntaxException var6) {
            _logger.error(var6);
            throw new Exception(var6);
         }
      }
   }

   protected abstract T create(String var1);

   protected abstract void destroy(T var1);

   public void beanDestroyed() {
      Iterator iterator = this.sessionContext.entrySet().iterator();

      while(iterator.hasNext()) {
         Entry<String, T> entry = (Entry)iterator.next();
         iterator.remove();
         this.destroy(entry.getValue());
      }

   }

   public void sessionEnded(String clientId) {
      Iterator iterator = this.sessionContext.entrySet().iterator();

      while(iterator.hasNext()) {
         Entry<String, T> entry = (Entry)iterator.next();
         if (((String)entry.getKey()).endsWith(clientId)) {
            iterator.remove();
            this.destroy(entry.getValue());
         }
      }

   }
}
