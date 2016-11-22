package com.madpixels.tgadmintools.services;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.madpixels.apphelpers.MyLog;
import com.madpixels.apphelpers.Utils;
import com.madpixels.tgadmintools.activity.MainActivity;
import com.madpixels.tgadmintools.helper.TgH;
import com.madpixels.tgadmintools.helper.TgUtils;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Created by Snake on 16.11.2016.
 */

public class ServiceBackgroundStarter extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        MyLog.log("Starting ServiceBackgroundStarter");
        if (!ServiceAutoKicker.IS_RUNNING && !ServiceChatTask.IS_RUNNING) {
            TgInit();
        } else {
            MyLog.log("ServiceBackgroundStarter services already started");
            registerNextStartForBackgroundService();
        }

        return START_STICKY;
    }

    private void TgInit() {
        TgH.init(getBaseContext());
        TgH.send(new TdApi.GetAuthState(), new Client.ResultHandler() {
            @Override
            public void onResult(final TdApi.TLObject object) {
                if (!TgUtils.isAuthorized(object)) {
                    stopSelf();
                    return;
                }

                onAuthorized();

                registerNextStartForBackgroundService();
            }
        });
    }


    private void onAuthorized() {
        MainActivity.initializeLanguage(this);
        ServiceAutoKicker.registerTask(getBaseContext());
        ServiceChatTask.start(getBaseContext());

        MyLog.log("ServiceBackgroundStarter runned tasks");
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void registerNextStartForBackgroundService() {
        Intent myIntent = new Intent(getBaseContext(), ServiceBackgroundStarter.class);
        /*
        PendingIntent pi = PendingIntent.getService(getBaseContext(), 0, myIntent, PendingIntent.FLAG_NO_CREATE);
        boolean isAlreadySet = pi != null;
        if (isAlreadySet) {
            //pi.cancel();
            MyLog.log("Alarm ServiceBackgroundStarter already set. Skip register alarm");
            return;
        }
        */

        PendingIntent pi = PendingIntent.getService(getBaseContext(), 0, myIntent, 0);
        AlarmManager alarmManager = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);

        long delayMillis = 1000 * 60 * 5;// 5 min
        long timeAlarmTrigger = System.currentTimeMillis() + delayMillis;
        MyLog.log("ServiceBackgroundStarter next start at: "+Utils.TimestampToDate(timeAlarmTrigger/1000));

        boolean isPowerSafeMode_API23 = false;
        if (Build.VERSION.SDK_INT < 19) {
            //alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firstCall, frequency, pi);
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeAlarmTrigger, pi);
            // MyLog.log("alarmManager registered at " + System.currentTimeMillis() + " , firstCall at " + firstCall);
            return;
        }

        if (Build.VERSION.SDK_INT < 23 || isPowerSafeMode_API23) // Если киткат, или запрещена работа в лоу режиме
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeAlarmTrigger, pi);
        else // Только для апи 23+:
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeAlarmTrigger, pi);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        context.startService(new Intent(context, ServiceBackgroundStarter.class));
    }

    public static void stop(Context c) {
        Intent intent = new Intent(c, ServiceBackgroundStarter.class);
        c.stopService(intent);

        AlarmManager alarmManager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent myIntent = new Intent(c, ServiceBackgroundStarter.class);
        PendingIntent pi = PendingIntent.getService(c, 0, myIntent, 0);
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }
}