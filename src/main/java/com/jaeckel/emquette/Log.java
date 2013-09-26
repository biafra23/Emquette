package com.jaeckel.emquette;

import android.os.Handler;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private static TextView textView;
    private static Handler handler = new Handler();

    public void init(Handler handler) {
        this.handler = handler;
    }

    public static void setTextView(TextView textView) {
        Log.textView = textView;
    }

    public static void v(String msg) {
        App.log.debug(msg);
        logToTextView(msg);
    }


    public static void d(String msg) {
        v(msg);
    }

    public static void i(String msg) {
        App.log.info(msg);
        logToTextView(msg);
    }

    public static void e(String msg, Throwable e) {
        App.log.error(msg, e);
        logToTextView(msg);
    }


    private static void logToTextView(final String msg) {
        final String time = timeFormat.format(new Date());
        if (textView != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    textView.append(time + " | " + msg + "\n");
                }
            });
        }
    }


}
