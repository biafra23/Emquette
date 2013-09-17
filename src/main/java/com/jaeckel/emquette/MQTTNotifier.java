package com.jaeckel.emquette;

import android.app.Activity;
import android.os.Bundle;

public class MQTTNotifier extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        App.log.info("MQTTNotifier started...");
    }
}