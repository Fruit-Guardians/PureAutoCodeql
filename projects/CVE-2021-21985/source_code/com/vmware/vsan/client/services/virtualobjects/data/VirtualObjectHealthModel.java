package com.vmware.vsan.client.services.virtualobjects.data;

public class VirtualObjectHealthModel {
   public String health;
   public String dataProtectionHealth;

   public VirtualObjectHealthModel(String health, String dataProtectionHealth) {
      this.health = health;
      this.dataProtectionHealth = dataProtectionHealth;
   }
}
