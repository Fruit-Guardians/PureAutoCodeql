package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vim.binding.vim.host.IpConfig.IpV6Address;
import com.vmware.vim.binding.vim.host.IpConfig.IpV6AddressConfiguration;
import com.vmware.vim.vsan.binding.vim.vsan.FileServiceIpConfig;
import com.vmware.vise.core.model.data;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;

@data
public class VsanFileServiceHostIpSettings {
   public boolean isDefault;
   public String address;
   public String dnsName;
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$fileservice$model$VsanFileServiceIpType;

   public static VsanFileServiceHostIpSettings fromVmodl(FileServiceIpConfig vmodl) {
      VsanFileServiceHostIpSettings ipSettings = new VsanFileServiceHostIpSettings();
      ipSettings.isDefault = BooleanUtils.isTrue(vmodl.isPrimary);
      ipSettings.dnsName = vmodl.fqdn;
      if (vmodl.ipV6Config != null && !ArrayUtils.isEmpty(vmodl.ipV6Config.ipV6Address)) {
         ipSettings.address = vmodl.ipV6Config.ipV6Address[0].ipAddress;
      } else {
         ipSettings.address = vmodl.ipAddress;
      }

      return ipSettings;
   }

   public FileServiceIpConfig toVmodl(VsanFileServiceIpType type, String gateway, String mask) {
      FileServiceIpConfig vmodl = new FileServiceIpConfig();
      vmodl.dhcp = false;
      vmodl.isPrimary = this.isDefault;
      vmodl.fqdn = this.dnsName;
      vmodl.gateway = gateway;
      vmodl.subnetMask = mask;
      switch($SWITCH_TABLE$com$vmware$vsan$client$services$fileservice$model$VsanFileServiceIpType()[type.ordinal()]) {
      case 1:
         vmodl.ipAddress = this.address;
         vmodl.ipV6Config = null;
         break;
      case 2:
         vmodl.ipAddress = null;
         vmodl.ipV6Config = new IpV6AddressConfiguration();
         vmodl.ipV6Config.dhcpV6Enabled = false;
         vmodl.ipV6Config.autoConfigurationEnabled = false;
         IpV6Address address = new IpV6Address();
         address.ipAddress = this.address;
         vmodl.ipV6Config.ipV6Address = new IpV6Address[]{address};
         break;
      default:
         throw new IllegalArgumentException("Unknonw IP type found which cannot handled: " + type);
      }

      return vmodl;
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsan$client$services$fileservice$model$VsanFileServiceIpType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsan$client$services$fileservice$model$VsanFileServiceIpType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[VsanFileServiceIpType.values().length];

         try {
            var0[VsanFileServiceIpType.V4.ordinal()] = 1;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[VsanFileServiceIpType.V6.ordinal()] = 2;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsan$client$services$fileservice$model$VsanFileServiceIpType = var0;
         return var0;
      }
   }
}
