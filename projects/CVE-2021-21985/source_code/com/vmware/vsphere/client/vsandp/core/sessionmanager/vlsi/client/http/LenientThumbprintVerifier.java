package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.http.impl.AllowAllThumbprintVerifier;

public class LenientThumbprintVerifier extends AllowAllThumbprintVerifier {
   public int hashCode() {
      return -863795821;
   }

   public boolean equals(Object obj) {
      return obj != null && obj instanceof LenientThumbprintVerifier;
   }
}
