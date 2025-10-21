package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;

public class ImpersonatingVcAuth extends Authenticator {
   protected Authenticator parentAuth;
   protected String userName;
   protected String locale;

   public ImpersonatingVcAuth(Authenticator parentAuth, String userName, String locale) {
      this.parentAuth = parentAuth;
      this.userName = userName;
      this.locale = locale;
   }

   public void login(VlsiConnection connection) {
      this.parentAuth.login(connection);
      this.impersonate((VcConnection)connection);
   }

   protected void impersonate(final VcConnection connection) {
      CheckedRunnable.withoutChecked(new CheckedRunnable() {
         public void run() throws Exception {
            connection.setSession(connection.getSessionManager().impersonateUser(ImpersonatingVcAuth.this.userName, ImpersonatingVcAuth.this.locale));
         }
      });
   }

   public void logout(VlsiConnection connection) {
      this.parentAuth.logout(connection);
   }

   public Authenticator getParentAuth() {
      return this.parentAuth;
   }

   public String getUserName() {
      return this.userName;
   }

   public String getLocale() {
      return this.locale;
   }

   public int hashCode() {
      boolean var10000 = true;
      int result = super.hashCode();
      result = 31 * result + (this.locale == null ? 0 : this.locale.hashCode());
      result = 31 * result + (this.parentAuth == null ? 0 : this.parentAuth.hashCode());
      result = 31 * result + (this.userName == null ? 0 : this.userName.hashCode());
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
         ImpersonatingVcAuth other = (ImpersonatingVcAuth)obj;
         if (this.locale == null) {
            if (other.locale != null) {
               return false;
            }
         } else if (!this.locale.equals(other.locale)) {
            return false;
         }

         if (this.parentAuth == null) {
            if (other.parentAuth != null) {
               return false;
            }
         } else if (!this.parentAuth.equals(other.parentAuth)) {
            return false;
         }

         if (this.userName == null) {
            if (other.userName != null) {
               return false;
            }
         } else if (!this.userName.equals(other.userName)) {
            return false;
         }

         return true;
      }
   }
}
