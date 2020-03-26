package com.ironcorelabs.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FullIntegrationTest {
    DeviceContext deviceContext;

    public FullIntegrationTest() throws Exception {
        System.loadLibrary("ironoxide_android");
        final java.io.InputStream in = FullIntegrationTest.class.getClassLoader()
                .getResourceAsStream("deviceContext.json");
        final java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
        final String deviceString = s.hasNext() ? s.next() : "";
        deviceContext = DeviceContext.fromJsonString(deviceString);
    }

    @Test
    public void roundtripData() throws Exception {
        final String data = "Test 123";
        final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
        final DocumentEncryptResult encryptedData = io.documentEncrypt(data.getBytes(), new DocumentEncryptOpts());
        final DocumentDecryptResult decryptedResult = io.documentDecrypt(encryptedData.getEncryptedData());
        final String decryptedData = new String(decryptedResult.getDecryptedData());
        assertEquals(data, decryptedData);
    }

    @Test
    public void createUser() throws Exception {
        final String userId = java.util.UUID.randomUUID().toString();
        final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
    }
}
