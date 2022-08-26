package com.nathcat.RSA;

import java.io.*;
import java.math.BigInteger;

public class EncryptedObject implements Serializable {
    public final boolean flipSign;   // Determine whether the sign should be flipped
    public BigInteger object;        // The object as a big integer

    public EncryptedObject(boolean flipSign, BigInteger object) {
        this.flipSign = flipSign;
        this.object = object;
    }

    public EncryptedObject(Object object) {
        BigInteger o = new BigInteger(SerializeObject(object));

        if (o.compareTo(new BigInteger("0")) < 0) {
            this.flipSign = o.compareTo(new BigInteger("0")) < 0;
            this.object = o.abs();
        }
        else {
            this.flipSign = false;
            this.object = o;
        }
    }

    public BigInteger GetInteger() {
        if (this.flipSign) {
            return this.object.multiply(new BigInteger("-1"));
        }
        else {
            return this.object;
        }
    }

    public BigInteger GetNaturalNumber() {
        return this.object;
    }

    public Object GetObject() {
        BigInteger o = this.GetInteger();
        return DeserializeObject(o.toByteArray());
    }

    public static byte[] SerializeObject(Object obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.flush();
            byte[] objBytes = baos.toByteArray();
            oos.close();
            baos.close();

            return objBytes;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Object DeserializeObject(byte[] bytes) {
        try {
            ByteArrayInputStream baos = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(baos);
            Object obj = ois.readObject();
            ois.close();
            baos.close();

            return obj;

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int[] byteToIntArray(byte[] objBytes) {
        int[] objInts = new int[objBytes.length / 4];
        int intCounter = 0;
        for (int i = 0; i < objBytes.length - 4; i += 4) {
            int currentInt = objBytes[i];
            currentInt <<= 24;
            currentInt += objBytes[i + 1];
            currentInt <<= 16;
            currentInt += objBytes[i + 2];
            currentInt <<= 8;
            currentInt += objBytes[i + 3];
            objInts[intCounter] = currentInt;
            intCounter++;
        }

        return objInts;
    }

    public static byte[] intToByteArray(int[] objInts) {
        byte[] objBytes = new byte[objInts.length * 4];
        int byteCounter = 0;
        for (int i = 0; i < objInts.length; i++) {
            objBytes[byteCounter] = (byte) (objInts[i] >> 24);
            objBytes[byteCounter + 1] = (byte) (objInts[i] >> 16);
            objBytes[byteCounter + 2] = (byte) (objInts[i] >> 8);
            objBytes[byteCounter + 3] = (byte) objInts[i];
            byteCounter += 4;
        }

        return objBytes;
    }
}
