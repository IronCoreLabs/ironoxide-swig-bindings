package com.ironcorelabs.example_application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

/**
 * Loads the native library from the published AAR, as a consumer does.
 *
 * This module depends on `com.ironcorelabs:ironoxide-android` from Maven rather than on the local
 * project, so it is the only place a packaging gap in the released artifact surfaces. Anything
 * `JNI_OnLoad` reaches for that the AAR does not carry fails here as an `UnsatisfiedLinkError`,
 * and does so before any SDK call reports a more confusing error.
 */
@RunWith(AndroidJUnit4.class)
public class NativeLibraryLoadTest {

    @Test
    public void nativeLibraryLoadsSuccessfully() {
        try {
            System.loadLibrary("ironoxide_android");
        } catch (final UnsatisfiedLinkError e) {
            fail("Failed to load the ironoxide-android native library: " + e.getMessage());
        }
    }
}
