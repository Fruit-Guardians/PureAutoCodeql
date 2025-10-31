package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import java.net.URI;
import java.security.KeyStore;

public class LookupSvcInfo {
   private final URI address;
   private final String thumbprint;
   private final ThumbprintVerifier thumbprintVerifier;
   private final KeyStore keyStore;

   public static LookupSvcInfo from(PscConnectionDetails lsParams) {
      return lsParams == null ? null : lsParams.toLsInfo();
   }

   public LookupSvcInfo(URI address, String thumbprint) {
      this(address, thumbprint, (ThumbprintVerifier)null, (KeyStore)null);
   }

   public LookupSvcInfo(URI address, KeyStore keyStore) {
      this(address, (String)null, (ThumbprintVerifier)null, keyStore);
   }

   public LookupSvcInfo(URI address, ThumbprintVerifier thumbprintVerifier) {
      this(address, (String)null, thumbprintVerifier, (KeyStore)null);
   }

   private LookupSvcInfo(URI address, String thumbprint, ThumbprintVerifier thumbprintVerifier, KeyStore keyStore) {
      this.address = address;
      this.thumbprint = thumbprint;
      this.thumbprintVerifier = thumbprintVerifier;
      this.keyStore = keyStore;
   }

   public URI getAddress() {
      return this.address;
   }

   public LookupSvcInfo copyWithAddress(URI address) {
      return new LookupSvcInfo(address, this.thumbprint, this.thumbprintVerifier, this.keyStore);
   }

   public String getThumbprint() {
      return this.thumbprint;
   }

   public LookupSvcInfo copyWithThumbprint(String thumbprint) {
      return new LookupSvcInfo(this.address, thumbprint, this.thumbprintVerifier, this.keyStore);
   }

   public KeyStore getKeyStore() {
      return this.keyStore;
   }

   public LookupSvcInfo copyWithKeyStore(KeyStore keyStore) {
      return new LookupSvcInfo(this.address, this.thumbprint, this.thumbprintVerifier, keyStore);
   }

   public ThumbprintVerifier getThumbprintVerifier() {
      if (this.thumbprintVerifier != null) {
         return this.thumbprintVerifier;
      } else {
         return this.thumbprint != null ? new SingleThumbprintVerifier(this.thumbprint) : null;
      }
   }

   public LookupSvcInfo copyWithThumbprintVerifier(ThumbprintVerifier thumbprintVerifier) {
      return new LookupSvcInfo(this.address, this.thumbprint, thumbprintVerifier, this.keyStore);
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = 1;
      int result = 31 * result + (this.address == null ? 0 : this.address.hashCode());
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
         LookupSvcInfo other = (LookupSvcInfo)obj;
         if (this.address == null) {
            if (other.address != null) {
               return false;
            }
         } else if (!this.address.equals(other.address)) {
            return false;
         }

         return true;
      }
   }

   public String toString() {
      return String.format("LookupSvcInfo [address=%s]", this.address);
   }
}
