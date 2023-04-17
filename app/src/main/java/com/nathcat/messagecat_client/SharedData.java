package com.nathcat.messagecat_client;

import android.os.Messenger;

import com.nathcat.messagecat_database_entities.User;

import java.io.Serializable;
import java.util.HashMap;

/**
 * This class contains data which is to be shared throughout the program.
 */
public class SharedData implements Serializable {
    public static User user;
    public static Messenger nsMessenger;
    public static Messenger nsReceiver;
    public static int activeChatID = -1;
    public static boolean nsWaitingForResponse = false;
    public static final HashMap<String, Object> misc = new HashMap<>();
}
