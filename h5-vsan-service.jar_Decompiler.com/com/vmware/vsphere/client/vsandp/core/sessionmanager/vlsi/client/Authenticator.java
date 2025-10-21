package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

public class Authenticator {
   protected final int id;

   public Authenticator() {
      this(0);
   }

   public Authenticator(int id) {
      this.id = id;
   }

   public void login(VlsiConnection connection) {
   }

   public void logout(VlsiConnection connection) {
   }

   public int hashCode() {
      return this.id;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         Authenticator other = (Authenticator)obj;
         return this.id == other.id;
      }
   }
}
