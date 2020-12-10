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

package io.github.chronosx88.influence;

import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import java.io.IOException;
import java.util.Set;
import io.github.chronosx88.influence.helpers.AppHelper;
import io.github.chronosx88.influence.helpers.NetworkHandler;
import io.github.chronosx88.influence.models.appEvents.AuthenticationStatusEvent;

import static io.github.chronosx88.influence.Constants.LOGGED_IN;
import static io.github.chronosx88.influence.Constants.PASSWORD;
import static io.github.chronosx88.influence.Constants.USER_NAME;

public class XMPPConnection implements ConnectionListener {
    private final static String LOG_TAG = "XMPPConnection";
    private LoginCredentials credentials = new LoginCredentials();
    private XMPPTCPConnection connection = null;
    private NetworkHandler networkHandler;
    private Context context;
    private Roster roster;
    private MamManager mamManager;

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    public enum SessionState {
        LOGGED_IN,
        LOGGED_OUT
    }

    public XMPPConnection(Context context) {
        this.context = context;
        String jid = SharedPrefs.getInstance().get(USER_NAME,String.class);
        String password = SharedPrefs.getInstance().get(PASSWORD,String.class);
        if(jid != null && password != null) {
            String username = jid.split("@")[0];
            String jabberHost = jid.split("@")[1];
            credentials.username = username;
            credentials.jabberHost = jabberHost;
            credentials.password = password;
        }
        networkHandler = new NetworkHandler();
    }

    public void connect() throws XMPPException, IOException, SmackException, EmptyLoginCredentialsException {
        if(credentials.isEmpty()) {
            throw new EmptyLoginCredentialsException();
        }
        if(connection == null) {
            XMPPTCPConnectionConfiguration conf = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(credentials.jabberHost)
                    .setHost(credentials.jabberHost)
                    .setResource(AppHelper.APP_NAME)
                    .setKeystoreType(null)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                    .setCompressionEnabled(true)
                    .setConnectTimeout(7000)
                    .build();

            connection = new XMPPTCPConnection(conf);
            connection.addConnectionListener(this);
            if(credentials.jabberHost.equals("") && credentials.password.equals("") && credentials.username.equals("")){
                throw new IOException();
            }
            try {
                connection.connect();
                connection.login(credentials.username, credentials.password);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                throw new IOException();
            }

            ChatManager.getInstanceFor(connection).addIncomingListener(networkHandler);
            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
            ReconnectionManager.setEnabledPerDefault(true);
            reconnectionManager.enableAutomaticReconnection();
            roster = roster.getInstanceFor(connection);
            roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
            roster.addPresenceEventListener(networkHandler);
            AppHelper.setJid(credentials.username + "@" + credentials.jabberHost);
            mamManager = MamManager.getInstanceFor(connection);
            try {
                if(mamManager.isSupported()) {
                    MamManager.getInstanceFor(connection).enableMamForAllMessages();
                } else {
                    mamManager = null;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(AppHelper.isIsMainActivityDestroyed()) {
                sendUserPresence(new Presence(Presence.Type.unavailable));
            }
        }
    }

    public void disconnect() {
        SharedPrefs.getInstance().put(LOGGED_IN,false);
        if(connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    @Override
    public void connected(org.jivesoftware.smack.XMPPConnection connection) {
        XMPPConnectionService.CONNECTION_STATE = ConnectionState.CONNECTED;
    }

    @Override
    public void authenticated(org.jivesoftware.smack.XMPPConnection connection, boolean resumed) {
        XMPPConnectionService.SESSION_STATE = SessionState.LOGGED_IN;
        SharedPrefs.getInstance().put(LOGGED_IN,true);
        EventBus.getDefault().post(new AuthenticationStatusEvent(AuthenticationStatusEvent.CONNECT_AND_LOGIN_SUCCESSFUL));
    }

    @Override
    public void connectionClosed() {
        XMPPConnectionService.CONNECTION_STATE = ConnectionState.DISCONNECTED;
        XMPPConnectionService.SESSION_STATE = SessionState.LOGGED_OUT;
        SharedPrefs.getInstance().put(LOGGED_IN,false);
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        XMPPConnectionService.CONNECTION_STATE = ConnectionState.DISCONNECTED;
        XMPPConnectionService.SESSION_STATE = SessionState.LOGGED_OUT;
        SharedPrefs.getInstance().put(LOGGED_IN,false);
        Log.e(LOG_TAG, "Connection closed, exception occurred");
        e.printStackTrace();
    }

    public String sendMessage(EntityBareJid recipientJid, String messageText) {
        Chat chat = ChatManager.getInstanceFor(connection).chatWith(recipientJid);
        try {
            Message message = new Message(recipientJid, Message.Type.chat);
            message.setBody(messageText);
            chat.send(message);
            return message.getStanzaId();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public XMPPTCPConnection getConnection() {
        return connection;
    }

    public byte[] getAvatar(EntityBareJid jid) {
        if(isConnectionAlive()) {
            VCardManager manager = VCardManager.getInstanceFor(connection);
            byte[] avatar = null;
            try {
                avatar = manager.loadVCard(jid).getAvatar();
            } catch (SmackException.NoResponseException e) {
                e.printStackTrace();
            } catch (XMPPException.XMPPErrorException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return avatar;
        }
        return null;
    }

    public Set<RosterEntry> getContactList() {
        if(isConnectionAlive()) {
            while (roster == null);
            return roster.getEntries();
        }
        return null;
    }

    public boolean isConnectionAlive() {
        if(XMPPConnectionService.CONNECTION_STATE.equals(ConnectionState.CONNECTED) && XMPPConnectionService.SESSION_STATE.equals(SessionState.LOGGED_IN)) {
            return true;
        } else {
            return false;
        }
    }

    public Presence getUserPresence(BareJid jid) {
        return roster.getPresence(jid);
    }

    public void sendUserPresence(Presence presence) {
        if(connection != null) {
            if(isConnectionAlive()) {
                try {
                    connection.sendStanza(presence);
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public MamManager getMamManager() {
        if(isConnectionAlive()) {
            return mamManager;
        }
        return null;
    }
}
