package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.CachedResourceFactory;

public class CachedHttpFactory extends CachedResourceFactory<ClientCfg, HttpSettings> {
   public CachedHttpFactory() {
      super(new HttpFactory());
   }
}
