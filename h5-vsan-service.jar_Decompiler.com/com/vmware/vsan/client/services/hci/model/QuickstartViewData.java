package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;

@data
public class QuickstartViewData {
   public String header;
   public String text;
   public boolean showSendFeedbackLink;
   public boolean showCloseQuickstartButton;
   public ConfigCardData[] configurationCards;
   public boolean extendCard;
}
