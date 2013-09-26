package com.jaeckel.emquette;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import org.eclipse.paho.client.mqttv3.*;

import java.util.Calendar;


public class PahoService extends Service implements MqttCallback {

    public static final String MQTT_PING_ACTION = "com.jaeckel.emquette.PING";
    public static final int KEEP_ALIVE_SECONDS = 20 * 60;
    private static final int MQTT_NOTIFICATION_UPDATE = 1;

    private NetworkStatusReceiver networkStatusReceiver;
    private PingSender pingSender;
    private MqttClient mqttClient;
    private String topicName = "testFooBar";
    private MqttMessage pingMessage = new MqttMessage("PING".getBytes());


    private Binder mBinder;

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        Log.d("onStartCommand()");

        new Thread(new Runnable() {
            @Override
            public void run() {
                handleStart(intent, startId);
            }
        }, "MQTTservice").start();

        Log.d("Starting sticky");
        return START_STICKY;
    }

    private void handleStart(Intent intent, int startId) {

        if (!isAlreadyConnected()) {


            if (connectToBroker()) {
                subscribeToTopic(topicName);
            }

            if (networkStatusReceiver == null) {
                Log.d("Registering CONNECTIVITY_ACTION receiver");
                networkStatusReceiver = new NetworkStatusReceiver();
                registerReceiver(networkStatusReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

                // ACTION_BACKGROUND_DATA_SETTING_CHANGED is deprecated and will result in network loss in newer Android versions
                registerReceiver(networkStatusReceiver,
                        new IntentFilter(ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED));

            }

            // creates the intents that are used to wake up the phone when it is
            //  time to ping the server
            if (pingSender == null) {
                pingSender = new PingSender();
                registerReceiver(pingSender, new IntentFilter(MQTT_PING_ACTION));
            }
        }
    }

    private boolean isAlreadyConnected() {
        return ((mqttClient != null) && (mqttClient.isConnected() == true));
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("onCreate()");

    }

    private boolean connectToBroker() {
        try {

            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setKeepAliveInterval(KEEP_ALIVE_SECONDS);

            mqttClient = new MqttClient("tcp://test.mosquitto.org:1883", "CLIENT_ID", null);
            mqttClient.setCallback(this);
            mqttClient.connect(mqttConnectOptions);

        } catch (MqttException e) {
            Log.e("Creating client", e);
        }

        return true;
    }

    private void subscribeToTopic(String topicName) {

        try {
            Log.d("Subscribing topic: " + topicName);

            mqttClient.subscribe(topicName);

        } catch (MqttException e) {
            Log.e("Subscribing topic: " + topicName, e);
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        Log.e("connectionLost()", throwable);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();

        if (isOnline() == false) {

            Log.d("We're offline()");

        } else {

            Log.d("We're online()");

            if (!isAlreadyConnected()) {
                Log.d("reconnectToBrokerr()");
                // try to reconnect
                if (connectToBroker()) {
                    subscribeToTopic(topicName);
                }
            }
        }

        wl.release();
    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        Log.d("messageArrived: s: " + s + ", mqttMessage: " + mqttMessage);

        scheduleKeepAliveAlarm();

        notifyUser(topicName, topicName, mqttMessage.toString());

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d("deliveryComplete: iMqttDeliveryToken: " + iMqttDeliveryToken);

    }

    private class NetworkStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.d("Received: intent: " + intent);

            if (isOnline()) {
                Log.d("Online now");
                notifyUser("Online", "Online", "Device is online now");

                // T O D O : register alarm for keep alive ping
                scheduleKeepAliveAlarm();

                // reconnect
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handleStart(intent, 0);
                    }
                }).start();

            } else {
                Log.d("Offline now");
                // T O D O : unregister Alarm for keep alive ping
                notifyUser("Offline", "Offline", "Device is offline now");
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm.getActiveNetworkInfo() != null &&
                cm.getActiveNetworkInfo().isAvailable() &&
                cm.getActiveNetworkInfo().isConnected()) {
            return true;
        }

        return false;
    }

    private void scheduleKeepAliveAlarm() {
        // When the phone is off, the CPU may be stopped. This means that our
        //   code may stop running.
        // When connecting to the message broker, we specify a 'keep alive'
        //   period - a period after which, if the client has not contacted
        //   the server, even if just with a ping, the connection is considered
        //   broken.
        // To make sure the CPU is woken at least once during each keep alive
        //   period, we schedule a wake up to manually ping the server
        //   thereby keeping the long-running connection open
        // Normally when using this Java MQTT client library, this ping would be
        //   handled for us.
        // Note that this may be called multiple times before the next scheduled
        //   ping has fired. This is good - the previously scheduled one will be
        //   cancelled in favour of this one.
        // This means if something else happens during the keep alive period,
        //   (e.g. we receive an MQTT message), then we start a new keep alive
        //   period, postponing the next ping.

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(MQTT_PING_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // in case it takes us a little while to do this, we try and do it
        //  shortly before the keep alive period expires
        // it means we're pinging slightly more frequently than necessary
        Calendar wakeUpTime = Calendar.getInstance();
        wakeUpTime.add(Calendar.SECOND, KEEP_ALIVE_SECONDS - 2); // Are two seconds enough to wake up and send the ping message?

        // T O D O : Try setInexactRepeating(): See http://developer.android.com/training/efficient-downloads/regular_updates.html#OptimizedPolling
        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.set(AlarmManager.RTC_WAKEUP,
                wakeUpTime.getTimeInMillis(),
                pendingIntent);

        Log.d("Scheduled ping for: " + wakeUpTime.getTime());

    }


    public class PingSender extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // AlarmManager is supposed to give us a Wakelock until onReceive() is done

            try {
                Log.d("Sending ping.");

//                mqttClient.ping();
                mqttClient.publish(topicName + "_ping", pingMessage);


            } catch (MqttException e) {
                // if something goes wrong, it should result in connectionLost
                //  being called, so we will handle it there
                Log.e("ping failed - MQTT exception", e);

                // assume the client connection is broken - trash it
                try {
                    mqttClient.disconnect();

                } catch (MqttException e2) {

                    Log.e("disconnect failed - MqttException exception", e2);
                }

                // reconnect
                if (connectToBroker()) {
                    subscribeToTopic(topicName);
                }
            }

            // start the next keep alive period
            scheduleKeepAliveAlarm();
        }
    }

    private void notifyUser(String alert, String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.ic_launcher, "A:" + alert,
                System.currentTimeMillis());
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.ledARGB = Color.MAGENTA;
        Intent notificationIntent = new Intent(this, MQTTNotifier.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setLatestEventInfo(this, title, body, contentIntent);
        nm.notify(MQTT_NOTIFICATION_UPDATE, notification);
    }

}
