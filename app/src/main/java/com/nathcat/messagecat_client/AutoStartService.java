package com.nathcat.messagecat_client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutoStartService extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startForegroundService(new Intent(context, NetworkerService.class));
        //context.startService(new Intent(context, NetworkerService.class));
    }
}
