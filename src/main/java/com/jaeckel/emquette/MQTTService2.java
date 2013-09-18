package com.jaeckel.emquette;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.fusesource.mqtt.client.Future;
import org.fusesource.mqtt.client.FutureConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

import java.net.URISyntaxException;

public class MQTTService2 extends Service {
    // trying to do local binding while minimizing leaks - code thanks to
    //   Geoff Bruckner - which I found at
    //   http://groups.google.com/group/cw-android/browse_thread/thread/d026cfa71e48039b/c3b41c728fedd0e7?show_docid=c3b41c728fedd0e7

//    private LocalBinder<MQTTService> mBinder;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

        App.log.info("onCreate()");

        MQTT mqtt = new MQTT();
        try {
//            mqtt.setHost("ssl://test.mosquitto.org:8883");
//            mqtt.setHost("tcp://test.mosquitto.org:1883");
            mqtt.setHost("test.mosquitto.org", 1883);

            App.log.info("mqtt.blockingConnection()");
//            BlockingConnection connection = mqtt.blockingConnection();
            FutureConnection connection = mqtt.futureConnection();

            App.log.info("connection.connect()");
            Future<Void> f1 = connection.connect();

            App.log.info("f1.await()");

            f1.await();

            connection.
            App.log.info("connection.connected");

            Topic[] topics = {new Topic("testFooBar", QoS.AT_LEAST_ONCE)};

            App.log.info("connection.subscribe()");

            Future<byte[]> qoses = connection.subscribe(topics);

            App.log.info("connection.receive()");
            Future<Message> message = connection.receive();

            App.log.debug("message.topic: " + message.await().getTopic());
            byte[] payload = message.await().getPayload();
            // process the message then:
            message.await().ack();

        } catch (URISyntaxException e) {

            App.log.error("Exception in host setting", e);
        } catch (Exception e) {

            App.log.error("Exception while connecting", e);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        App.log.debug("onStartCommand()");

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                handleStart(intent, startId);
//            }
//        }, "MQTTservice").start();

        // return START_NOT_STICKY - we want this Service to be left running
        //  unless explicitly stopped, and it's process is killed, we want it to
        //  be restarted
        return START_STICKY;
    }

}
