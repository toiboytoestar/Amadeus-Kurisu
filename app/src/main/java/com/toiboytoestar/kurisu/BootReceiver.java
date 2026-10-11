package com.toiboytoestar.kurisu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms reminders and the wake-up call after a reboot or app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Reminders.rescheduleAll(ctx);
            if (new Prefs(ctx).showXpIcon()) Notifier.showXp(ctx);
        }
    }
}
