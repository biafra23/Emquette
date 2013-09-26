package com.jaeckel.emquette;

import android.app.Application;
import android.os.Handler;
import org.apache.log4j.Logger;


public class App extends Application {

    //Global logging facility. Use App.log.level() anywhere
    public final static Logger log = Logger.getLogger("Emquette");

    //    private Bus bus;
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();

        new Log().init(new Handler());
        ConfigureLog4j.configure();

        instance = this;

//        bus = new Bus();
//        bus.register(this);

        App.log.info("onCreate()");

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable ex) {
                App.log.error("Exception in Thread: " + thread.getName() + ": ", ex);
            }
        });

    }

    public static App getApp() {

        return instance;
    }

//    public static Bus getBus() {
//        return instance.bus;
//    }


}
