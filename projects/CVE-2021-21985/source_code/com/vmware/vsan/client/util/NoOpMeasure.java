package com.vmware.vsan.client.util;

public class NoOpMeasure extends Measure {
   public NoOpMeasure() {
      super("");
   }

   public Measure start(String task) {
      return new NoOpMeasure();
   }

   public void close() {
   }

   public String toString() {
      return this.getClass().getSimpleName();
   }
}
