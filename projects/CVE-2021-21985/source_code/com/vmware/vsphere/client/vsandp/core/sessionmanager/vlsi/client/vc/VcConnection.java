package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.binding.vim.ExtensionManager;
import com.vmware.vim.binding.vim.ServiceInstanceContent;
import com.vmware.vim.binding.vim.SessionManager;
import com.vmware.vim.binding.vim.UserSession;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.vslm.vcenter.VStorageObjectManager;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.query.PropertyCollector;
import com.vmware.vim.vmomi.client.common.ProtocolBinding;
import com.vmware.vim.vmomi.client.common.Session;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;

public class VcConnection extends VlsiConnection {
   protected UserSession session;
   protected ServiceInstanceContent content;

   public UserSession getSession() {
      return this.session;
   }

   public void setSession(UserSession session) {
      this.session = session;
   }

   public SessionManager getSessionManager() {
      return (SessionManager)this.createStub(SessionManager.class, this.content.getSessionManager());
   }

   public ExtensionManager getExtensionManager() {
      return (ExtensionManager)this.createStub(ExtensionManager.class, this.content.getExtensionManager());
   }

   public PropertyCollector getPropertyCollector() {
      return (PropertyCollector)this.createStub(PropertyCollector.class, this.content.getPropertyCollector());
   }

   public VStorageObjectManager getVStorageObjectManager() {
      return (VStorageObjectManager)this.createStub(VStorageObjectManager.class, this.content.getVStorageObjectManager());
   }

   public VsanSystem getHostVsanSystem(ManagedObjectReference hostRef) {
      ManagedObjectReference hostVsanSystemRef = new ManagedObjectReference("HostVsanSystem", hostRef.getValue().replace("host", "vsanSystem"), hostRef.getServerGuid());
      return (VsanSystem)this.createStub(VsanSystem.class, hostVsanSystemRef);
   }

   public ServiceInstanceContent getContent() {
      return this.content;
   }

   public String getSessionCookie() {
      if (this.client == null) {
         return null;
      } else {
         ProtocolBinding binding = this.client.getBinding();
         if (binding == null) {
            return null;
         } else {
            Session session = binding.getSession();
            if (session == null) {
               return null;
            } else {
               String sessionCookie = session.getId();
               return sessionCookie;
            }
         }
      }
   }

   public String toString() {
      return this.settings != null && this.content != null ? String.format("VcConnection(host=%s, uuid=%s)", this.settings.getHttpSettings().getHost(), this.content.getAbout().getInstanceUuid()) : "VcConnection(initializing)";
   }
}
