package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.VersionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanExploratoryFactory implements ResourceFactory<VsanConnection, VlsiExploratorySettings> {
   private static final String VSAN_HEALTH_SERVICE_SUBDIR = "/vsanHealth";
   private final ResourceFactory<VsanConnection, VlsiSettings> vsanFactory;
   @Autowired
   private VersionService versionService;

   public VsanExploratoryFactory(ResourceFactory<VsanConnection, VlsiSettings> vsanFactory) {
      this.vsanFactory = vsanFactory;
   }

   public VsanConnection acquire(VlsiExploratorySettings settings) {
      HttpSettings vcHttpSettings = settings.getServiceSettingsTemplate().getHttpSettings();
      Class vsanVmodlVersion = this.versionService.getVsanVmodlVersion(vcHttpSettings.getServiceUri().toString());
      HttpSettings vsanHttpSettings = vcHttpSettings.setPath("/vsanHealth").setVersion(vsanVmodlVersion);
      VlsiSettings vsanSettings = settings.getServiceSettingsTemplate().setHttpSettings(vsanHttpSettings);
      return (VsanConnection)this.vsanFactory.acquire(vsanSettings);
   }
}
