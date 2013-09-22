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
import org.eclipse.paho.client.mqttv3.*;

import java.util.Calendar;


public class PahoService extends Service implements MqttCallback {


    // constant used internally to schedule the next ping event
    public static final String MQTT_PING_ACTION = "com.jaeckel.emquette.PING";
    public static final int KEEP_ALIVE_SECONDS = 20 * 60;
    private static final int MQTT_NOTIFICATION_UPDATE = 1;
//    public static final int KEEP_ALIVE_SECONDS = 2 * 60;

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
        App.log.debug("onStartCommand()");

        new Thread(new Runnable() {
            @Override
            public void run() {
                handleStart(intent, startId);
            }
        }, "MQTTservice").start();

        // return START_NOT_STICKY - we want this Service to be left running
        //  unless explicitly stopped, and it's process is killed, we want it to
        //  be restarted
        App.log.debug("Starting sticky");
        return START_STICKY;
    }

    private void handleStart(Intent intent, int startId) {

        if (!isAlreadyConnected()) {


            if (connectToBroker()) {
                subscribeToTopic(topicName);
            }

            if (networkStatusReceiver == null) {
                App.log.info("Registering CONNECTIVITY_ACTION receiver");
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

//    public class LocalBinder<S> extends Binder {
//        private WeakReference<S> mService;
//
//        public LocalBinder(S service) {
//            mService = new WeakReference<S>(service);
//        }
//
//        public S getService() {
//            return mService.get();
//        }
//
//        public void close() {
//            mService = null;
//        }
//    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.log.debug("onCreate()");

//        // reset status variable to initial state
////        connectionStatus = MQTTConnectionStatus.INITIAL;
//
//        // create a binder that will let the Activity UI send
//        //   commands to the Service
//        mBinder = new LocalBinder<PahoService>(this);
//        // define the connection to the broker
    }

    private boolean connectToBroker() {
        try {

            mqttClient = new MqttClient("tcp://test.mosquitto.org:1883", "CLIENT_ID", null);
//            MqttClient mqttClient = new MqttClient("ssl://test.mosquitto.org:8883", "CLIENT_ID", null);

            mqttClient.setCallback(this);
            mqttClient.connect();

        } catch (MqttException e) {
            App.log.error("Creating client", e);
        }

        return true;
    }

    private void subscribeToTopic(String topicName) {

        try {
            App.log.debug("Subscribing topic: " + topicName);

            mqttClient.subscribe(topicName);

        } catch (MqttException e) {
            App.log.error("Subscribing topic: " + topicName, e);
        }
    }

    // MqttCallback callbacks
    @Override
    public void connectionLost(Throwable throwable) {
        App.log.error("connectionLost()", throwable);

    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        App.log.debug("messageArrived: s: " + s + ", mqttMessage: " + mqttMessage);

        scheduleKeepAliveAlarm();

        notifyUser(topicName, topicName, mqttMessage.toString());

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        App.log.debug("deliveryComplete: iMqttDeliveryToken: " + iMqttDeliveryToken);

    }

    private class NetworkStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            App.log.debug("Received: intent: " + intent);

            if (isOnline()) {
                App.log.debug("Online now");
                notifyUser("Online", "Online", "Device is online now");

                // T O D O : register alarm for keep alive ping
                scheduleKeepAliveAlarm();


            } else {
                App.log.debug("Offline now");
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
        wakeUpTime.add(Calendar.SECOND, KEEP_ALIVE_SECONDS);

        // T O D O : Try setInexactRepeating(): See http://developer.android.com/training/efficient-downloads/regular_updates.html#OptimizedPolling
        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.set(AlarmManager.RTC_WAKEUP,
                wakeUpTime.getTimeInMillis(),
                pendingIntent);

        App.log.info("Scheduled ping for: " + wakeUpTime.getTime());

    }


    /*
   * Used to implement a keep-alive protocol at this Service level - it sends
   *  a PING message to the server, then schedules another ping after an
   *  interval defined by keepAliveSeconds
   */
    public class PingSender extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Note that we don't need a wake lock for this method (even though
            //  it's important that the phone doesn't switch off while we're
            //  doing this).
            // According to the docs, "Alarm Manager holds a CPU wake lock as
            //  long as the alarm receiver's onReceive() method is executing.
            //  This guarantees that the phone will not sleep until you have
            //  finished handling the broadcast."
            // This is good enough for our needs.

            try {
                App.log.info("Sending ping.");

                // T O D O : send something
//                mqttClient.ping();
                mqttClient.publish(topicName, pingMessage);


            } catch (MqttException e) {
                // if something goes wrong, it should result in connectionLost
                //  being called, so we will handle it there
                App.log.error("ping failed - MQTT exception", e);

                // assume the client connection is broken - trash it
                try {
                    mqttClient.disconnect();

                } catch (MqttException e2) {

                    App.log.error("disconnect failed - MqttException exception", e2);
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
        Notification notification = new Notification(R.drawable.ic_launcher, alert,
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
