package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vim.vmomi.core.impl.SslUtil;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class CertificateUtils {
   private CertificateUtils() {
   }

   public static String getServerThumbprint(String url) throws Exception {
      X509Certificate cert = getServerCert(url);
      return SslUtil.computeCertificateThumbprint(cert);
   }

   public static X509Certificate getServerCert(String url) throws Exception {
      URL urlAddr = new URL(url);
      TrustManager[] trustAllTrustManager = new TrustManager[]{new X509TrustManager() {
         public X509Certificate[] getAcceptedIssuers() {
            return null;
         }

         public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
         }

         public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
         }
      }};
      HostnameVerifier trustAllVerifier = new HostnameVerifier() {
         public boolean verify(String hostname, SSLSession session) {
            return true;
         }
      };
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init((KeyManager[])null, trustAllTrustManager, (SecureRandom)null);
      HttpsURLConnection con = null;
      con = (HttpsURLConnection)urlAddr.openConnection();
      con.setSSLSocketFactory(sslContext.getSocketFactory());
      con.setHostnameVerifier(trustAllVerifier);
      con.connect();

      X509Certificate var7;
      try {
         var7 = (X509Certificate)con.getServerCertificates()[0];
      } finally {
         con.disconnect();
      }

      return var7;
   }
}
