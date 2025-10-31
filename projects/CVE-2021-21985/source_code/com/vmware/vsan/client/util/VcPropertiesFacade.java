package com.vmware.vsan.client.util;

import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import org.springframework.stereotype.Component;

@Component
public class VcPropertiesFacade {
   public boolean isNativeUnmanagedLinkedClone(VirtualDisk disk) {
      return disk.getNativeUnmanagedLinkedClone() != null && disk.getNativeUnmanagedLinkedClone();
   }
}
