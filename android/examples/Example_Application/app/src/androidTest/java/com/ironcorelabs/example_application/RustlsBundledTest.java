package com.ironcorelabs.example_application;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies that the rustls-platform-verifier classes are bundled in the
 * ironoxide-android AAR and available at runtime. If these classes are missing,
 * JNI_OnLoad will fail when loading the native library, causing a silent TLS
 * failure for consumers.
 */
@RunWith(AndroidJUnit4.class)
public class RustlsBundledTest {

    @Test
    public void rustlsCertificateVerifierClassIsAvailable() {
        try {
            Class<?> clazz = Class.forName("org.rustls.platformverifier.CertificateVerifier");
            assertNotNull("CertificateVerifier class should be loadable", clazz);
        } catch (ClassNotFoundException e) {
            fail("rustls CertificateVerifier class is not bundled in the AAR. "
                    + "Consumers will get TLS failures at runtime.");
        }
    }

    @Test
    public void nativeLibraryLoadsSuccessfully() {
        // JNI_OnLoad calls init_rustls_platform_verifier, which looks up
        // org/rustls/platformverifier/CertificateVerifier via JNI.
        // If the class isn't on the classpath, this will throw UnsatisfiedLinkError.
        try {
            System.loadLibrary("ironoxide_android");
        } catch (UnsatisfiedLinkError e) {
            fail("Failed to load native library. This may indicate the rustls "
                    + "CertificateVerifier class is missing: " + e.getMessage());
        }
    }
}
