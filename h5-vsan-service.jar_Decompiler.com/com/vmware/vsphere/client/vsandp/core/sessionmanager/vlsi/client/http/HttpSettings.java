package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vim.vmomi.core.types.VmodlContext;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.CloseableExecutorService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorSettings;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class HttpSettings extends BaseHttpSettings {
   protected final Class<?> version;
   protected final String proxyProto;
   protected final String proxyHost;
   protected final int proxyPort;
   protected final ClientCertificate clientCert;
   protected final ThumbprintVerifier thumbprintVerifier;
   protected final VmodlContext vmodlContext;
   protected final Map<String, Object> requestProperties;

   public static HttpSettings createTemplate(ResourceFactory<CloseableExecutorService, ExecutorSettings> executorFactory, ExecutorSettings executorSettings, VmodlContext vmodlContext, int maxConnections, int timeoutInMillis) {
      return new HttpSettings("https", (String)null, -1, (String)null, (String)null, (String)null, -1, maxConnections, timeoutInMillis, (ClientCertificate)null, (ClientCertificate)null, new LenientThumbprintVerifier(), executorFactory, executorSettings, Void.class, vmodlContext, (Map)null);
   }

   public HttpSettings(String proto, String host, int port, String path, String proxyProto, String proxyHost, int proxyPort, int maxConn, int timeout, ClientCertificate clientCert, ClientCertificate trustStore, ThumbprintVerifier thumbprintVerifier, ResourceFactory<CloseableExecutorService, ExecutorSettings> executorMgr, ExecutorSettings executorSettings, Class<?> version, VmodlContext vmodlContext, Map<String, Object> requestProperties) {
      super(executorMgr, executorSettings, proto, host, port, path, maxConn, timeout, trustStore);
      this.proxyProto = proxyProto;
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
      this.clientCert = clientCert;
      this.thumbprintVerifier = thumbprintVerifier;
      this.version = version;
      this.vmodlContext = vmodlContext;
      this.requestProperties = requestProperties;
   }

   public HttpSettings setExecutorFactory(ResourceFactory<CloseableExecutorService, ExecutorSettings> executorMgr) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, executorMgr, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setExecutorSettings(ExecutorSettings executorSettings) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setServiceUri(URI serviceUri) {
      return new HttpSettings(serviceUri.getScheme(), serviceUri.getHost(), serviceUri.getPort(), serviceUri.getPath(), this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public URI getServiceUri() {
      try {
         return new URI(this.getProto(), (String)null, this.getHost(), this.getPort(), this.getPath(), (String)null, (String)null);
      } catch (URISyntaxException var2) {
         throw new RuntimeException(var2);
      }
   }

   public Class<?> getVersion() {
      return this.version;
   }

   public HttpSettings setVersion(Class<?> version) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setProto(String proto) {
      return new HttpSettings(proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setHost(String host) {
      return new HttpSettings(this.proto, host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setPort(int port) {
      return new HttpSettings(this.proto, this.host, port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setPath(String path) {
      return new HttpSettings(this.proto, this.host, this.port, path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setProxyUri(URI proxyUri) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, proxyUri == null ? null : proxyUri.getScheme(), proxyUri == null ? null : proxyUri.getHost(), proxyUri == null ? -1 : proxyUri.getPort(), this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public String getProxyProto() {
      return this.proxyProto;
   }

   public HttpSettings setProxyProto(String proxyProto) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public String getProxyHost() {
      return this.proxyHost;
   }

   public HttpSettings setProxyHost(String proxyHost) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public int getProxyPort() {
      return this.proxyPort;
   }

   public HttpSettings setProxyPort(int proxyPort) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setMaxConn(int maxConn) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setTimeout(int timeout) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public ClientCertificate getClientCert() {
      return this.clientCert;
   }

   public HttpSettings setClientCert(ClientCertificate clientCert) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public HttpSettings setTrustStore(ClientCertificate trustStore) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public ThumbprintVerifier getThumbprintVerifier() {
      return this.thumbprintVerifier;
   }

   public HttpSettings setThumbprintVerifier(ThumbprintVerifier thumbprintVerifier) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, this.requestProperties);
   }

   public Map<String, Object> getRequestProperties() {
      return this.requestProperties;
   }

   public HttpSettings setRequestProperties(Map<String, Object> requestProperties) {
      return new HttpSettings(this.proto, this.host, this.port, this.path, this.proxyProto, this.proxyHost, this.proxyPort, this.maxConn, this.timeout, this.clientCert, this.trustStore, this.thumbprintVerifier, this.executorFactory, this.executorSettings, this.version, this.vmodlContext, requestProperties);
   }

   public VmodlContext getVmodlContext() {
      return this.vmodlContext;
   }

   public boolean isViaProxy() {
      return this.proxyHost != null;
   }

   public URI makeUri() {
      try {
         return new URI(this.getProto(), (String)null, this.getHost(), this.getPort(), this.getPath(), (String)null, (String)null);
      } catch (URISyntaxException var2) {
         throw new RuntimeException(var2);
      }
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = super.hashCode();
      result = 31 * result + (this.clientCert == null ? 0 : this.clientCert.hashCode());
      result = 31 * result + (this.proxyHost == null ? 0 : this.proxyHost.hashCode());
      result = 31 * result + this.proxyPort;
      result = 31 * result + (this.proxyProto == null ? 0 : this.proxyProto.hashCode());
      result = 31 * result + (this.thumbprintVerifier == null ? 0 : this.thumbprintVerifier.hashCode());
      result = 31 * result + (this.version == null ? 0 : this.version.hashCode());
      result = 31 * result + (this.vmodlContext == null ? 0 : this.vmodlContext.hashCode());
      result = 31 * result + (this.requestProperties == null ? 0 : this.requestProperties.hashCode());
      return result;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!super.equals(obj)) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         HttpSettings other = (HttpSettings)obj;
         if (this.clientCert == null) {
            if (other.clientCert != null) {
               return false;
            }
         } else if (!this.clientCert.equals(other.clientCert)) {
            return false;
         }

         if (this.proxyHost == null) {
            if (other.proxyHost != null) {
               return false;
            }
         } else if (!this.proxyHost.equals(other.proxyHost)) {
            return false;
         }

         if (this.proxyPort != other.proxyPort) {
            return false;
         } else {
            if (this.proxyProto == null) {
               if (other.proxyProto != null) {
                  return false;
               }
            } else if (!this.proxyProto.equals(other.proxyProto)) {
               return false;
            }

            if (this.thumbprintVerifier == null) {
               if (other.thumbprintVerifier != null) {
                  return false;
               }
            } else if (!this.thumbprintVerifier.equals(other.thumbprintVerifier)) {
               return false;
            }

            if (this.version == null) {
               if (other.version != null) {
                  return false;
               }
            } else if (!this.version.equals(other.version)) {
               return false;
            }

            if (this.vmodlContext == null) {
               if (other.vmodlContext != null) {
                  return false;
               }
            } else if (!this.vmodlContext.equals(other.vmodlContext)) {
               return false;
            }

            if (this.requestProperties == null) {
               if (other.requestProperties != null) {
                  return false;
               }
            } else if (!this.requestProperties.equals(other.requestProperties)) {
               return false;
            }

            return true;
         }
      }
   }

   public String toString() {
      return "HttpSettings [version=" + this.version + ", proxyProto=" + this.proxyProto + ", proxyHost=" + this.proxyHost + ", proxyPort=" + this.proxyPort + ", clientCert=" + this.clientCert + ", thumbprintVerifier=" + this.thumbprintVerifier + ", vmodlContext=" + this.vmodlContext + ", executorFactory=" + this.executorFactory + ", executorSettings=" + this.executorSettings + ", proto=" + this.proto + ", host=" + this.host + ", port=" + this.port + ", path=" + this.path + ", maxConn=" + this.maxConn + ", timeout=" + this.timeout + ", trustStore=" + this.trustStore + ", requestProperties=" + this.requestProperties + "]";
   }
}
