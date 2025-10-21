package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class ServiceEndpoint {
   protected final URI uri;
   protected final X509Certificate[] certs;
   protected final String thumbprint;

   public ServiceEndpoint(URI uri, X509Certificate[] certs) {
      this.uri = uri;
      this.certs = certs;
      this.thumbprint = this.calcThumbprint();
   }

   public X509Certificate[] getCerts() {
      return this.certs;
   }

   public URI getUri() {
      return this.uri;
   }

   public URL getUrl() throws MalformedURLException {
      return this.uri.toURL();
   }

   public String getThumbprint() {
      return this.thumbprint;
   }

   protected String calcThumbprint() {
      if (this.certs != null && this.certs.length >= 1) {
         try {
            return CertificateHelper.calcThumbprint(this.certs[0].getEncoded());
         } catch (CertificateEncodingException var2) {
            throw new SsoException("Could not decode certificate", var2);
         } catch (NoSuchAlgorithmException var3) {
            throw new SsoException("Invalid certificate algorithm", var3);
         }
      } else {
         return null;
      }
   }
}
