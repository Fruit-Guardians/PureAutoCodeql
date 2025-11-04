package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.vsan.binding.vsan.version.versions;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.VersionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;

public class VsanVcExploratoryFactory extends VcExploratoryFactory {
   @Autowired
   private VersionService versionService;

   public VsanVcExploratoryFactory(ResourceFactory<VcConnection, VlsiSettings> vcFactory) {
      super(vcFactory);
   }

   protected Class<?> getVmodlVerion(URI vcAddress) {
      Class result;
      try {
         result = this.versionService.getVmodlVersion(vcAddress.toString(), "/vsanServiceVersions.xml");
      } catch (Exception var3) {
         result = versions.VSAN_VERSION_STABLE;
      }

      return result;
   }
}
