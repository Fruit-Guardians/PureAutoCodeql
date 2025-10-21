package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.model.filter;

import com.vmware.vise.core.model.data;

@data
public class DataProtectionInstanceFilter {
   public double newerThanThreeDays;
   public double betweenThreeAndSevenDays;
   public double betweenOneAndTwoWeeks;
   public double betweenTwoAndFourWeeks;
   public double olderThanFourWeeks;
   public int newerThanThreeDaysCount;
   public int betweenThreeAndSevenDaysCount;
   public int betweenOneAndTwoWeeksCount;
   public int betweenTwoAndFourWeeksCount;
   public int olderThanFourWeeksCount;
}
