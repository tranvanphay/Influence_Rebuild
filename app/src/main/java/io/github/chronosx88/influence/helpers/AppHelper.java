/*
 *    Copyright 2019 ChronosX88
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.github.chronosx88.influence.helpers;

import android.app.Application;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import androidx.multidex.MultiDexApplication;
import androidx.room.Room;
import com.instacart.library.truetime.TrueTime;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.github.chronosx88.influence.LoginCredentials;
import io.github.chronosx88.influence.SharedPrefs;
import io.github.chronosx88.influence.XMPPConnection;

import static io.github.chronosx88.influence.Constants.PASSWORD;
import static io.github.chronosx88.influence.Constants.USER_NAME;

/**
 * Extended Application class which designed for centralized getting various objects from anywhere in the application.
 */
public class AppHelper extends MultiDexApplication {
    private static Application instance;
    public final static String APP_NAME = "Influence";
    public final static String DEFAULT_NTP_SERVER = "time.apple.com";
    private static AppHelper mSelf;
    private static String jid;
    private static RoomHelper chatDB;
    private static SharedPreferences preferences;
    private static XMPPConnection xmppConnection;
    private static LoginCredentials currentLoginCredentials;
    private static Handler mainUIThreadHandler;
    private static ServiceConnection serviceConnection;
    private static boolean isMainActivityDestroyed = true;
    private static String currentChatActivity = "";
    public final static Map<String, byte[]> avatarsCache = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mSelf = this;
        mainUIThreadHandler = new Handler(Looper.getMainLooper());
        initChatDB();
        preferences = PreferenceManager.getDefaultSharedPreferences(instance);
        initTrueTime();
        loadLoginCredentials();
    }

    public static AppHelper self() {
        return mSelf;
    }

    public static Context getContext() {
        return instance.getApplicationContext();
    }

    public static String getJid() { return jid; }

    public static void setJid(String jid1) { jid = jid1; }

    public static RoomHelper getChatDB() { return chatDB; }

    public static SharedPreferences getPreferences() {
        return preferences;
    }

    public static XMPPConnection getXmppConnection() {
        return xmppConnection;
    }

    public static void setXmppConnection(XMPPConnection xmppConnection) {
        AppHelper.xmppConnection = xmppConnection;
    }

    public static void loadLoginCredentials() {
        currentLoginCredentials = new LoginCredentials();
        String jid = SharedPrefs.getInstance().get(USER_NAME,String.class);
        String password = SharedPrefs.getInstance().get(PASSWORD,String.class);
        if(jid != null && password != null) {
            String username = jid.split("@")[0];
            String jabberHost = jid.split("@")[1];
            currentLoginCredentials.username = username;
            currentLoginCredentials.jabberHost = jabberHost;
            currentLoginCredentials.password = password;
        }
        AppHelper.setJid(currentLoginCredentials.username + "@" + currentLoginCredentials.jabberHost);
    }

    public static void resetLoginCredentials() {
        currentLoginCredentials = null;
        SharedPrefs.getInstance().clear();
    }

    private static void initTrueTime() {
        new Thread(() -> {
            boolean isTrueTimeIsOn = false;
            int count = 0;
            while(!isTrueTimeIsOn && count <= 10) {
                try {
                    TrueTime.build().withNtpHost(DEFAULT_NTP_SERVER).initialize();
                    isTrueTimeIsOn = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    count++;
                }
            }
        }).start();
    }

    private void initChatDB() {
        chatDB = Room.databaseBuilder(getApplicationContext(), RoomHelper.class, "chatDB")
                .fallbackToDestructiveMigration() // FIXME   ONLY FOR TEST ENVIRONMENT! DON'T USE THIS IN PRODUCTION!
                .allowMainThreadQueries()
                .build();
    }

    public static Handler getMainUIThread() {
        return mainUIThreadHandler;
    }

    public static void setServiceConnection(ServiceConnection serviceConnection) {
        AppHelper.serviceConnection = serviceConnection;
    }

    public static ServiceConnection getServiceConnection() {
        return serviceConnection;
    }

    public static boolean isIsMainActivityDestroyed() {
        return isMainActivityDestroyed;
    }

    public static void setIsMainActivityDestroyed(boolean isMainActivityDestroyed) {
        AppHelper.isMainActivityDestroyed = isMainActivityDestroyed;
    }

    public static String getCurrentChatActivity() {
        return currentChatActivity;
    }

    public static void setCurrentChatActivity(String currentChatActivity) {
        AppHelper.currentChatActivity = currentChatActivity;
    }

    public static LoginCredentials getCurrentLoginCredentials() {
        return currentLoginCredentials;
    }
}