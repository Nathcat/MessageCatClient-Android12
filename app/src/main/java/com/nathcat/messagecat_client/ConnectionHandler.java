package com.nathcat.messagecat_client;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import com.nathcat.RSA.*;
import com.nathcat.messagecat_database.Result;

/**
 * Manages a connection to the server
 */
public class ConnectionHandler extends Handler {
    public Socket s = null;                // The socket used to connect to the server
    public ObjectOutputStream oos = null;  // Object output stream
    public ObjectInputStream ois = null;   // Object input stream
    public KeyPair keyPair = null;         // The client's key pair
    public KeyPair serverKeyPair = null;   // The server's key pair
    public final Context context;          // The context this handler was created in
    public int connectionHandlerId;        // The identifier of the connection handler this handler has connected to

    public ConnectionHandler(Context context, Looper looper) {
        super(looper);  // Handler constructor

        this.context = context;
    }

    /**
     * Send an object to the server
     * @param obj The object to send
     * @throws IOException Thrown in case of I/O issues
     */
    public void Send(Object obj) throws IOException {
        oos.writeObject(obj);
        oos.flush();
    }

    /**
     * Receive an object from the server
     * @return The object received
     * @throws IOException Thrown by I/O issues
     * @throws ClassNotFoundException Thrown if the required class cannot be found
     */
    public Object Receive() throws IOException, ClassNotFoundException {
        return ois.readObject();
    }

    /**
     * Handles messages passed to the handler.
     *
     * @param msg The message passed to the handler
     */
    @Override
    public void handleMessage(Message msg) {
        // If the 'what' parameter of the message is 0, that indicates an initialisation request
        // This should only occur when the service is first started
        if (msg.what == 0) {
            try {
                // Try to connect to the server
                this.s = new Socket("192.168.181.226", 1234);
                this.s.setSoTimeout(20000);
                this.oos = new ObjectOutputStream(s.getOutputStream());
                this.ois = new ObjectInputStream(s.getInputStream());

                // Create a key pair and perform the handshake
                this.keyPair = RSA.GenerateRSAKeyPair();
                this.serverKeyPair = (KeyPair) this.Receive();

                this.Send(new KeyPair(this.keyPair.pub, null));

                this.connectionHandlerId = (int) this.serverKeyPair.decryptBigObject((EncryptedObject[]) this.Receive());

            } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException | PrivateKeyException e) {
                e.printStackTrace();
            }

            assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;
            return;
        }
        else if (msg.what == 2) {
            try {
                this.oos.close();
                this.ois.close();
                this.s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.getLooper().quit();
            return;
        }

        // This point will only be reached if the 'what' parameter of the message is not 0
        // Get the request object from the message
        NetworkerService.Request request = (NetworkerService.Request) msg.obj;

        try {
            // Send the request
            this.Send(this.serverKeyPair.encryptBigObject(request.request));
            // Receive the response and perform the callback from the request object
            request.callback.callback(Result.SUCCESS, ((ObjectContainer) this.keyPair.decryptBigObject((EncryptedObject[]) this.Receive())).obj);

        } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException e) {
            e.printStackTrace();
            // Perform callback with failure code
            request.callback.callback(Result.FAILED, null);
        }
    }
}
