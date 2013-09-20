package com.jaeckel.emquette;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import org.eclipse.paho.client.mqttv3.*;


public class PahoService extends Service implements MqttCallback {
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {

            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            MqttClient mqttClient = new MqttClient("tcp://test.mosquitto.org:1883", "CLIENT_ID", null);
//            MqttClient mqttClient = new MqttClient("ssl://test.mosquitto.org:8883", "CLIENT_ID", null);

            mqttClient.setCallback(this);
            mqttClient.connect(mqttConnectOptions);
            mqttClient.subscribe("testFooBar");

        } catch (MqttException e) {
            App.log.error("Creating client", e);
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
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        App.log.debug("deliveryComplete: iMqttDeliveryToken: " + iMqttDeliveryToken);

    }

}
