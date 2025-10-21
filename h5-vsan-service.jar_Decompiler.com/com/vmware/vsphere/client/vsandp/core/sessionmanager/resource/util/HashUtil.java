package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class HashUtil {
   public final String defaultAlgo;
   public final List<String> algos;
   public static final String SHA1_ALGO = "SHA-1";
   public static final String SHA256_ALGO = "SHA-256";
   public static final String SHA384_ALGO = "SHA-384";
   public static final String SHA512_ALGO = "SHA-512";

   public HashUtil() {
      this("SHA-256", Arrays.asList("SHA-1", "SHA-256", "SHA-384", "SHA-512"));
   }

   public HashUtil(String defaultAlgo, List<String> algos) {
      this.defaultAlgo = defaultAlgo;
      this.algos = Collections.unmodifiableList(algos);
   }

   public String sha(byte[] data, String algo) {
      MessageDigest md;
      try {
         md = MessageDigest.getInstance(algo);
      } catch (NoSuchAlgorithmException var8) {
         throw new RuntimeException(var8);
      }

      md.update(data);
      byte[] hash = md.digest();
      StringBuilder sb = new StringBuilder(algo + ":");

      for(int i = 0; i < hash.length; ++i) {
         String hex = Integer.toHexString(255 & hash[i]);
         if (hex.length() == 1) {
            sb.append('0');
         }

         sb.append(hex);
         if (i < hash.length - 1) {
            sb.append(':');
         }
      }

      return this.parseSha(sb.toString());
   }

   public String sha256(byte[] data) {
      return this.sha(data, "SHA-256");
   }

   public String parseSha(String sha) {
      sha = sha.toUpperCase();
      boolean hasAlgo = false;
      Iterator var4 = this.algos.iterator();

      while(var4.hasNext()) {
         String algo = (String)var4.next();
         if (sha.startsWith(algo)) {
            hasAlgo = true;
            break;
         }
      }

      if (!hasAlgo) {
         sha = this.defaultAlgo + ":" + sha;
      }

      this.validateSha(sha);
      return sha;
   }

   public void validateSha(String sha) {
   }

   public String extractAlgo(String sha) {
      String[] terms = sha.split(":");
      if (terms.length < 1) {
         throw new IllegalArgumentException("Algorithm prefix missing from checksum: " + sha);
      } else if (!this.algos.contains(terms[0])) {
         throw new IllegalArgumentException("Unknown checksum algorithm: " + terms[0]);
      } else {
         return terms[0];
      }
   }

   public boolean verify(String sha, X509Certificate cert) {
      sha = this.parseSha(sha);
      String algo = this.extractAlgo(sha);

      String computedSha;
      try {
         computedSha = this.sha(cert.getEncoded(), algo);
      } catch (CertificateEncodingException var5) {
         throw new IllegalArgumentException("Invalid certificate");
      }

      return sha.equals(computedSha);
   }

   public String withoutAlgo(String sha) {
      this.validateSha(sha);
      String algo = this.extractAlgo(sha);
      return sha.substring(algo.length() + 1);
   }
}
