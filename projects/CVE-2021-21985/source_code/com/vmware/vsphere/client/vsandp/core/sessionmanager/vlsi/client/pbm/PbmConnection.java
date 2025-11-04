package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm;

import com.vmware.vim.binding.pbm.ServiceInstanceContent;
import com.vmware.vim.binding.pbm.auth.SessionManager;
import com.vmware.vim.binding.pbm.compliance.ComplianceManager;
import com.vmware.vim.binding.pbm.placement.PlacementSolver;
import com.vmware.vim.binding.pbm.profile.ProfileManager;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;

public class PbmConnection extends VlsiConnection {
   protected ServiceInstanceContent content;

   public ServiceInstanceContent getContent() {
      return this.content;
   }

   public SessionManager getSessionManager() {
      return (SessionManager)this.createStub(SessionManager.class, this.content.getSessionManager());
   }

   public ProfileManager getProfileManager() {
      return (ProfileManager)this.createStub(ProfileManager.class, this.content.getProfileManager());
   }

   public ComplianceManager getComplianceManager() {
      return (ComplianceManager)this.createStub(ComplianceManager.class, this.content.getComplianceManager());
   }

   public PlacementSolver getPlacementSolver() {
      return (PlacementSolver)this.createStub(PlacementSolver.class, this.content.getPlacementSolver());
   }

   public String toString() {
      return this.settings != null && this.content != null ? String.format("PbmConnection(host=%s, uuid=%s)", this.settings.getHttpSettings().getHost(), this.content.getAboutInfo().getInstanceUuid()) : "PbmConnection(initializing)";
   }
}
