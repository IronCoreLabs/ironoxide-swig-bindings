package com.ironcorelabs.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FullIntegrationTest {
	BlockingDeviceContext deviceContext;

	public FullIntegrationTest() throws Exception {
		System.loadLibrary("ironoxide_android");
		final java.io.InputStream in = FullIntegrationTest.class.getClassLoader()
				.getResourceAsStream("deviceContext.json");
		final java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
		final String deviceString = s.hasNext() ? s.next() : "";
		deviceContext = BlockingDeviceContext.fromJsonString(deviceString);
	}

	@Test
	public void roundtripData() throws Exception {
		final String data = "Test 123";
		final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
		final DocumentEncryptResult encryptResult = io.documentEncrypt(data.getBytes(), new DocumentEncryptOpts());
		final DocumentDecryptResult decryptResult = io.documentDecrypt(encryptResult.getEncryptedData());
		final String decryptedData = new String(decryptResult.getDecryptedData());
		assertEquals(data, decryptedData);
	}

	@Test
	public void roundtripDataUnmanaged() throws Exception {
		final String data = "Test 123";
		final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
		final DocumentEncryptUnmanagedResult encryptResult = io.advancedDocumentEncryptUnmanaged(data.getBytes(),
				new DocumentEncryptOpts());
		assertEquals(encryptResult.getId().getId().length(), 32);

		final DocumentDecryptUnmanagedResult decryptResult = io
				.advancedDocumentDecryptUnmanaged(encryptResult.getEncryptedData(), encryptResult.getEncryptedDeks());
		assertEquals(encryptResult.getId(), decryptResult.getId());

		final String decryptedData = new String(decryptResult.getDecryptedData());
		assertEquals(data, decryptedData);
	}

	@Test
	public void timeoutInitialize() throws Exception {
		try {
			final IronOxide io = IronOxide.initialize(deviceContext,
					new IronOxideConfig(new PolicyCachingConfig(), Duration.fromMillis(5)));
			org.junit.Assert.fail("Should have failed on timeout");
		} catch (final Exception e) {
			assertTrue("Incorrect exception: " + e.getMessage(), e.getMessage().contains("timed out"));
		}
	}

	@Test
	public void groupCreate() throws Exception {
		final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
		final GroupId groupId = GroupId.validate(UUID.randomUUID().toString());
		final GroupCreateOpts opts = new GroupCreateOpts(groupId, null, true, true, null, new UserId[0], new UserId[0],
				false);
		final GroupCreateResult result = io.groupCreate(opts);
		assertEquals(result.getId(), groupId);
		assertEquals(result.getCreated(), result.getLastUpdated());
	}

	@Test
	public void documentList() throws Exception {
		final IronOxide io = IronOxide.initialize(deviceContext, new IronOxideConfig());
		final DocumentListResult result = io.documentList();
		assertTrue("There should have been some encrypted documents.", result.getResult().length > 0);
	}

}
