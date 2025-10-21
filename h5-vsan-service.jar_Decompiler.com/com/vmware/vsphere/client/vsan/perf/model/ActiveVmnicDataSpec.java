package com.vmware.vsphere.client.vsan.perf.model;

import com.google.common.collect.Multimap;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

public class ActiveVmnicDataSpec {
   public List<ManagedObjectReference> switches;
   public Map<String, ManagedObjectReference> uuidSwitchMap;
   public Multimap<ManagedObjectReference, ManagedObjectReference> switchNetworkMap;
   public Map<ManagedObjectReference, String[]> networkUplinksMap;

   public Set<String> getUplinksBySwitchUuid(String uuid, String portgroupKey) {
      Set<String> uplinks = new HashSet();
      ManagedObjectReference switchRef = (ManagedObjectReference)this.uuidSwitchMap.get(uuid);
      if (switchRef != null) {
         Iterator var6 = this.switchNetworkMap.get(switchRef).iterator();

         while(var6.hasNext()) {
            ManagedObjectReference networkRef = (ManagedObjectReference)var6.next();
            if (networkRef.getValue() != null && StringUtils.equals(portgroupKey, networkRef.getValue())) {
               String[] activeUplink = (String[])this.networkUplinksMap.get(networkRef);
               if (!ArrayUtils.isEmpty(activeUplink)) {
                  uplinks.addAll(Arrays.asList(activeUplink));
               }
            }
         }
      }

      return uplinks;
   }
}
