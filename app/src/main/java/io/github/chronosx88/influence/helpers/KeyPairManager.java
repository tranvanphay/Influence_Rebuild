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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public class KeyPairManager {
    private File keyPairDir;
    private JavaSerializer<KeyPair> serializer;

    public KeyPairManager() {
        this.keyPairDir = new File(AppHelper.getContext().getFilesDir().getAbsoluteFile(), "keyPairs");
        if(!this.keyPairDir.exists()) {
            this.keyPairDir.mkdir();
        }
        this.serializer = new JavaSerializer<>();
    }

    public KeyPair openMainKeyPair() {
        return getKeyPair("mainKeyPair");
    }

    public KeyPair getKeyPair(String keyPairName) {
        keyPairName = keyPairName + ".kp";
        File keyPairFile = new File(keyPairDir, keyPairName);
        if (!keyPairFile.exists()) {
            return createKeyPairFile(keyPairFile);
        }
        return openKeyPairFile(keyPairFile);
    }

    private synchronized KeyPair openKeyPairFile(File keyPairFile) {
        KeyPair keyPair = null;
        try {
            FileInputStream inputStream = new FileInputStream(keyPairFile);
            byte[] serializedKeyPair = new byte[(int) keyPairFile.length()];
            inputStream.read(serializedKeyPair);
            inputStream.close();
            keyPair = serializer.deserialize(serializedKeyPair);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return keyPair;
    }

    private synchronized KeyPair createKeyPairFile(File keyPairFile) {
        KeyPair keyPair = null;
        try {
            keyPairFile.createNewFile();
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            keyPair = keyPairGenerator.generateKeyPair();
            FileOutputStream outputStream = new FileOutputStream(keyPairFile);
            outputStream.write(serializer.serialize(keyPair));
            outputStream.close();
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return keyPair;
    }

    public synchronized void saveKeyPair(String keyPairID, KeyPair keyPair) {
        File keyPairFile = new File(keyPairDir, keyPairID + ".kp");
        if(!keyPairFile.exists()) {
            try {
                FileOutputStream outputStream = new FileOutputStream(keyPairFile);
                outputStream.write(serializer.serialize(keyPair));
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
