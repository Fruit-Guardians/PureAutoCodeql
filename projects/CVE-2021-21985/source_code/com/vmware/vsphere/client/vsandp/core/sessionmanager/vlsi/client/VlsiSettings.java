package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vim.binding.lookup.ServiceRegistration.Endpoint;
import com.vmware.vim.binding.lookup.ServiceRegistration.EndpointType;
import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.ClientCfg;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorSettings;
import java.net.URI;

public class VlsiSettings {
   protected final ResourceFactory<ClientCfg, HttpSettings> httpMgr;
   protected final HttpSettings httpSettings;
   protected final Authenticator authenticator;
   protected final String sessionCookie;

   public VlsiSettings(ResourceFactory<ClientCfg, HttpSettings> httpFactory, HttpSettings httpSettings, Authenticator authenticator, String sessionCookie) {
      this.httpMgr = httpFactory;
      this.httpSettings = httpSettings;
      this.authenticator = authenticator != null ? authenticator : new Authenticator();
      this.sessionCookie = sessionCookie;
   }

   public ResourceFactory<ClientCfg, HttpSettings> getHttpFactory() {
      return this.httpMgr;
   }

   public VlsiSettings setHttpFactory(ResourceFactory<ClientCfg, HttpSettings> httpMgr) {
      return new VlsiSettings(httpMgr, this.httpSettings, this.authenticator, this.sessionCookie);
   }

   public HttpSettings getHttpSettings() {
      return this.httpSettings;
   }

   public VlsiSettings setHttpSettings(HttpSettings httpSettings) {
      return new VlsiSettings(this.httpMgr, httpSettings, this.authenticator, this.sessionCookie);
   }

   public Authenticator getAuthenticator() {
      return this.authenticator;
   }

   public VlsiSettings setAuthenticator(Authenticator authenticator) {
      return new VlsiSettings(this.httpMgr, this.httpSettings, authenticator, this.sessionCookie);
   }

   public VlsiSettings setServiceInfo(URI serviceUri, Class<?> version) {
      HttpSettings newSettings = this.httpSettings.setServiceUri(serviceUri).setVersion(version);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public VlsiSettings setProxyInfo(URI proxyUri) {
      HttpSettings newSettings = this.httpSettings.setProxyUri(proxyUri);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public VlsiSettings setSslContext(ClientCertificate trustStore, ThumbprintVerifier thumbprintVerifier) {
      HttpSettings newSettings = this.httpSettings.setTrustStore(trustStore).setThumbprintVerifier(thumbprintVerifier);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public VlsiSettings setExecutorSettings(ExecutorSettings executorSettings) {
      HttpSettings newSettings = this.httpSettings.setExecutorSettings(executorSettings);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public VlsiSettings setConnectionSettings(int maxConn, int timeout) {
      HttpSettings newSettings = this.httpSettings.setMaxConn(maxConn).setTimeout(timeout);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public VlsiSettings setClientCertificate(ClientCertificate clientCert) {
      HttpSettings newSettings = this.httpSettings.setClientCert(clientCert);
      return new VlsiSettings(this.httpMgr, newSettings, this.authenticator, this.sessionCookie);
   }

   public String getSessionCookie() {
      return this.sessionCookie;
   }

   public VlsiSettings setSessionCookie(String sessionCookie) {
      return new VlsiSettings(this.httpMgr, this.httpSettings, this.authenticator, sessionCookie);
   }

   public VlsiSettings updateFrom(Info info) {
      return this.updateFrom(info, (EndpointType)null, (Class)null);
   }

   public VlsiSettings updateFrom(Info info, EndpointType endpointType, Class<?> versionCls) {
      Endpoint point = null;
      if (endpointType == null) {
         if (info.getServiceEndpoints().length != 1) {
            throw new IllegalArgumentException("Exactly one endpoint expected, but found " + info.getServiceEndpoints().length + " endpoints in service " + info);
         }

         point = info.getServiceEndpoints()[0];
      } else {
         Endpoint[] endpoints = info.getServiceEndpoints();
         if (endpoints == null || endpoints.length < 1) {
            throw new RuntimeException("No endpoints found for service " + info);
         }

         Endpoint[] var9 = endpoints;
         int var8 = endpoints.length;

         for(int var7 = 0; var7 < var8; ++var7) {
            Endpoint endpoint = var9[var7];
            if ((endpointType.getProtocol() == null || endpointType.getProtocol().equals(endpoint.getEndpointType().getProtocol())) && (endpointType.getType() == null || endpointType.getType().equals(endpoint.getEndpointType().getType()))) {
               point = endpoint;
               break;
            }
         }

         if (point == null) {
            throw new RuntimeException("Cannot find endpoint for protocol " + endpointType.getProtocol() + " and/or type " + endpointType.getType() + " in service " + info);
         }
      }

      ClientCertificate truststore = new ClientCertificate(info.getServiceId(), point.getSslTrust(), "", "", info.getServiceId());
      Class<?> cls = versionCls != null ? versionCls : this.getHttpSettings().getVersion();
      return this.setSslContext(truststore, (ThumbprintVerifier)null).setProxyInfo((URI)null).setServiceInfo(point.getUrl(), cls);
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.authenticator == null ? 0 : this.authenticator.hashCode());
      result = 31 * result + (this.httpSettings == null ? 0 : this.httpSettings.hashCode());
      result = 31 * result + (this.sessionCookie == null ? 0 : this.sessionCookie.hashCode());
      return result;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         VlsiSettings other = (VlsiSettings)obj;
         if (this.authenticator == null) {
            if (other.authenticator != null) {
               return false;
            }
         } else if (!this.authenticator.equals(other.authenticator)) {
            return false;
         }

         if (this.httpSettings == null) {
            if (other.httpSettings != null) {
               return false;
            }
         } else if (!this.httpSettings.equals(other.httpSettings)) {
            return false;
         }

         if (this.sessionCookie == null) {
            if (other.sessionCookie != null) {
               return false;
            }
         } else if (!this.sessionCookie.equals(other.sessionCookie)) {
            return false;
         }

         return true;
      }
   }

   public String toString() {
      return String.format("VlsiSettings [authenticator=%s, httpSettings=%s, sessionCookie=%s]", this.authenticator, this.httpSettings, this.sessionCookie);
   }
}
