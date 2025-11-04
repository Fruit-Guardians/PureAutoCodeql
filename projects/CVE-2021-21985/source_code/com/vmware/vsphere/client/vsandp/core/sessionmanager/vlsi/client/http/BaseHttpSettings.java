package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.CloseableExecutorService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor.ExecutorSettings;

public class BaseHttpSettings {
   protected final ResourceFactory<CloseableExecutorService, ExecutorSettings> executorFactory;
   protected final ExecutorSettings executorSettings;
   protected final String proto;
   protected final String host;
   protected final int port;
   protected final String path;
   protected final int maxConn;
   protected final int timeout;
   protected final ClientCertificate trustStore;

   public BaseHttpSettings(ResourceFactory<CloseableExecutorService, ExecutorSettings> executorFactory, ExecutorSettings executorSettings, String proto, String host, int port, String path, int maxConn, int timeout, ClientCertificate trustStore) {
      this.executorFactory = executorFactory;
      this.executorSettings = executorSettings;
      this.proto = proto;
      this.host = host;
      this.port = port;
      this.path = path;
      this.maxConn = maxConn;
      this.timeout = timeout;
      this.trustStore = trustStore;
   }

   public ResourceFactory<CloseableExecutorService, ExecutorSettings> getExecutorFactory() {
      return this.executorFactory;
   }

   public ExecutorSettings getExecutorSettings() {
      return this.executorSettings;
   }

   public String getProto() {
      return this.proto;
   }

   public String getHost() {
      return this.host;
   }

   public int getPort() {
      return this.port;
   }

   public String getPath() {
      return this.path;
   }

   public int getMaxConn() {
      return this.maxConn;
   }

   public int getTimeout() {
      return this.timeout;
   }

   public ClientCertificate getTrustStore() {
      return this.trustStore;
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.executorSettings == null ? 0 : this.executorSettings.hashCode());
      result = 31 * result + (this.host == null ? 0 : this.host.hashCode());
      result = 31 * result + this.maxConn;
      result = 31 * result + (this.path == null ? 0 : this.path.hashCode());
      result = 31 * result + this.port;
      result = 31 * result + (this.proto == null ? 0 : this.proto.hashCode());
      result = 31 * result + this.timeout;
      result = 31 * result + (this.trustStore == null ? 0 : this.trustStore.hashCode());
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
         BaseHttpSettings other = (BaseHttpSettings)obj;
         if (this.executorSettings == null) {
            if (other.executorSettings != null) {
               return false;
            }
         } else if (!this.executorSettings.equals(other.executorSettings)) {
            return false;
         }

         if (this.host == null) {
            if (other.host != null) {
               return false;
            }
         } else if (!this.host.equals(other.host)) {
            return false;
         }

         if (this.maxConn != other.maxConn) {
            return false;
         } else {
            if (this.path == null) {
               if (other.path != null) {
                  return false;
               }
            } else if (!this.path.equals(other.path)) {
               return false;
            }

            if (this.port != other.port) {
               return false;
            } else {
               if (this.proto == null) {
                  if (other.proto != null) {
                     return false;
                  }
               } else if (!this.proto.equals(other.proto)) {
                  return false;
               }

               if (this.timeout != other.timeout) {
                  return false;
               } else {
                  if (this.trustStore == null) {
                     if (other.trustStore != null) {
                        return false;
                     }
                  } else if (!this.trustStore.equals(other.trustStore)) {
                     return false;
                  }

                  return true;
               }
            }
         }
      }
   }

   public String toString() {
      return "BaseHttpSettings [executorFactory=" + this.executorFactory + ", executorSettings=" + this.executorSettings + ", proto=" + this.proto + ", host=" + this.host + ", port=" + this.port + ", path=" + this.path + ", maxConn=" + this.maxConn + ", timeout=" + this.timeout + ", trustStore=" + this.trustStore + "]";
   }
}
