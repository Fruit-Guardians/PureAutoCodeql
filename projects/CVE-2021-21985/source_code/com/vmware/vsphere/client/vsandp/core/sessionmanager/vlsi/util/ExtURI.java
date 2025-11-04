package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util;

import java.net.URI;
import java.net.URISyntaxException;

public class ExtURI {
   protected URI uri;
   protected URI proxy;

   public ExtURI(String uri) throws URISyntaxException {
      int pos = uri.indexOf(124);
      if (pos >= 0) {
         this.uri = new URI(uri.substring(pos + 1));
         String proxyUri = uri.substring(0, pos).trim();
         if (!proxyUri.isEmpty()) {
            this.proxy = new URI(proxyUri);
         }
      } else {
         this.uri = new URI(uri);
      }

   }

   public URI getUri() {
      return this.uri;
   }

   public void setUri(URI uri) {
      this.uri = uri;
   }

   public URI getProxy() {
      return this.proxy;
   }

   public void setProxy(URI proxy) {
      this.proxy = proxy;
   }

   public String toString() {
      String proxyUrl = "";
      if (this.proxy != null) {
         proxyUrl = this.proxy.toString();
         if (!proxyUrl.endsWith("/")) {
            proxyUrl = proxyUrl + "/";
         }

         proxyUrl = proxyUrl + "|";
      }

      return proxyUrl + this.uri.toString();
   }
}
