package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;

public class CredentialsVcAuth extends VcAuthenticator {
   protected final String user;
   protected final String pass;

   public CredentialsVcAuth(String user, String pass, String locale, int id) {
      super(locale, id);
      this.user = user;
      this.pass = pass;
   }

   public CredentialsVcAuth(String user, String pass, String locale) {
      super(locale);
      this.user = user;
      this.pass = pass;
   }

   protected void loginVc(final VcConnection connection) {
      CheckedRunnable.withoutChecked(new CheckedRunnable() {
         public void run() throws Exception {
            connection.setSession(connection.getSessionManager().login(CredentialsVcAuth.this.user, CredentialsVcAuth.this.pass, CredentialsVcAuth.this.locale));
         }
      });
   }

   public String getUser() {
      return this.user;
   }

   public String getPass() {
      return this.pass;
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = super.hashCode();
      result = 31 * result + (this.pass == null ? 0 : this.pass.hashCode());
      result = 31 * result + (this.user == null ? 0 : this.user.hashCode());
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
         CredentialsVcAuth other = (CredentialsVcAuth)obj;
         if (this.pass == null) {
            if (other.pass != null) {
               return false;
            }
         } else if (!this.pass.equals(other.pass)) {
            return false;
         }

         if (this.user == null) {
            if (other.user != null) {
               return false;
            }
         } else if (!this.user.equals(other.user)) {
            return false;
         }

         return true;
      }
   }
}
