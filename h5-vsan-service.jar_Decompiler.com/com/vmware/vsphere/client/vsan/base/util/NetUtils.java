package com.vmware.vsphere.client.vsan.base.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;

public class NetUtils {
   public static final String HTTP_GET = "GET";
   public static final String CONTEXT_SSL = "SSL";

   public static TrustManager createTrustAllManager() {
      return new X509TrustManager() {
         public X509Certificate[] getAcceptedIssuers() {
            return null;
         }

         public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
         }

         public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
         }
      };
   }

   public static HttpClient createTrustAllHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
      TrustManager trustAllManager = createTrustAllManager();
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init((KeyManager[])null, new TrustManager[]{trustAllManager}, new SecureRandom());
      return HttpClients.custom().setSSLContext(sc).build();
   }

   public static SSLSocketFactory getDisableSSLCertificateCheckingSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
      TrustManager[] trustAllCerts = new TrustManager[]{createTrustAllManager()};
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init((KeyManager[])null, trustAllCerts, new SecureRandom());
      return sc.getSocketFactory();
   }

   public static HostnameVerifier createAllTrustingHostnameVerifier() {
      return new HostnameVerifier() {
         public boolean verify(String hostname, SSLSession session) {
            return true;
         }
      };
   }

   public static HttpsURLConnection createUntrustedConnection(String address) throws KeyManagementException, NoSuchAlgorithmException, MalformedURLException, IOException {
      return createUntrustedConnection(new URL(address));
   }

   public static HttpsURLConnection createUntrustedConnection(URL url) throws KeyManagementException, NoSuchAlgorithmException, IOException {
      HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
      conn.setSSLSocketFactory(getDisableSSLCertificateCheckingSocketFactory());
      conn.setHostnameVerifier(createAllTrustingHostnameVerifier());
      return conn;
   }

   public static boolean isSuccess(int responseCode) {
      return responseCode >= 200 && responseCode < 300;
   }
}
