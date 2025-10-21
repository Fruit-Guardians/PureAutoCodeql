package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.binding.lookup.ServiceRegistration.Filter;
import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractLsExplorer<R> {
   private final ServiceRegistration lookupService;

   public AbstractLsExplorer(ServiceRegistration lookupService) {
      this.lookupService = lookupService;
   }

   public R get(UUID uuid) {
      R result = this.map().get(uuid);
      if (result != null) {
         return result;
      } else {
         throw new IllegalStateException("Service registration not found: " + uuid);
      }
   }

   public Set<R> list() {
      return new HashSet(this.map().values());
   }

   public Map<UUID, R> map() {
      Info[] list = this.lookupService.list(this.getFilter());
      if (list != null && list.length != 0) {
         Map<UUID, R> result = new HashMap();
         Info[] var6 = list;
         int var5 = list.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Info registration = var6[var4];
            this.mapRegistration(this.createRegistration(registration), result);
         }

         return result;
      } else {
         return Collections.emptyMap();
      }
   }

   protected abstract R createRegistration(Info var1);

   protected abstract void mapRegistration(R var1, Map<UUID, R> var2);

   protected abstract Filter getFilter();
}
