package com.vmware.vsan.client.services.iscsi;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.host.NetworkInfo;
import com.vmware.vim.binding.vim.host.VirtualNic;
import com.vmware.vim.binding.vim.host.IpConfig.IpV6Address;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsphere.client.vsan.iscsi.models.target.NetworkIpSetting;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class NetworkIpConfigProvider {
   private static final String NETWORK_CONFIG_PROPERTY = "config.network";
   private static final String DHCP_ORIGIN = "dhcp";
   private static final String SLAAC_ORIGIN = "linklayer";

   @TsService
   public List<NetworkIpSetting> getIpSetting(ManagedObjectReference[] hostRefs, String[] vnicNames) throws Exception {
      List<NetworkIpSetting> ipSettingList = new ArrayList();
      DataServiceResponse response = QueryUtil.getProperties(hostRefs, new String[]{"config.network"});
      if (response == null) {
         return ipSettingList;
      } else {
         for(int i = 0; i < hostRefs.length; ++i) {
            NetworkIpSetting setting = new NetworkIpSetting();
            NetworkInfo networkConfig = (NetworkInfo)((Map)response.getMap().get(hostRefs[i])).get("config.network");
            String vnicName = vnicNames[i];
            VirtualNic[] var12;
            int var11 = (var12 = networkConfig.vnic).length;

            for(int var10 = 0; var10 < var11; ++var10) {
               VirtualNic vnic = var12[var10];
               if (vnicName.equals(vnic.device)) {
                  if (!StringUtils.isBlank(vnic.spec.ip.ipAddress)) {
                     setting.ipV4Address = vnic.spec.ip.ipAddress;
                  }

                  if (networkConfig.ipV6Enabled) {
                     setting.ipV6Address = this.getFormattedIpV6Address(vnic.spec.ip.ipV6Config.ipV6Address);
                  }
                  break;
               }
            }

            ipSettingList.add(setting);
         }

         return ipSettingList;
      }
   }

   private String getFormattedIpV6Address(IpV6Address[] ipV6Addresses) {
      if (ArrayUtils.isEmpty(ipV6Addresses)) {
         return null;
      } else {
         IpV6Address[] var5 = ipV6Addresses;
         int var4 = ipV6Addresses.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            IpV6Address addr = var5[var3];
            if ("dhcp".equalsIgnoreCase(addr.origin)) {
               return addr.ipAddress + "/" + addr.prefixLength;
            }

            if ("linklayer".equalsIgnoreCase(addr.origin)) {
               return addr.ipAddress + "/" + addr.prefixLength;
            }
         }

         return null;
      }
   }
}
