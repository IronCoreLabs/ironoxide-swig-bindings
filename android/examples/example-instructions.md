Steps to create an example

- create a new "Basic Application" through Android Studio. Make sure to choose Java as the language.
- inside of `app/src/main` create a directory `jniLibs/x86`
- copy `libironoxide_android.so` built for `i686-linux-android` into the `x86` directory
- in app/build.gradle add the following to the `dependencies`
  - `implementation 'com.ironcorelabs:ironoxide-android:0.12.2'`
- add the following to `AndroidManifest.xml` (top level) to enable network access

```
    <uses-permission android:name="android.permission.INTERNET" />=
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

- in MainActivity.java replace `onCreate` with:

```
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            try {
                System.loadLibrary("ironoxide_java");
                Log.i("JNI", "ironoxide_java library loaded sucessfully");
            } catch (UnsatisfiedLinkError e) {
                Log.e("JNI", "Load library ERROR: " + e);
                return;
            }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        // TODO paste your own prod device here, or read from somewhere
        String deviceString ="{" +
                "  \"accountId\": \"ctest\"," +
                "  \"segmentId\": 1825," +
                "  \"signingPrivateKey\": \"NOT REAL KEY"," +
                "  \"devicePrivateKey\": \"NOT REAL KEY=\"" +
                "}";
        String decryptedData = null;
        try {

            DeviceContext context = DeviceContext.fromJsonString(deviceString);
            Log.i("JNI", "Device context constructed: "+ context.toJsonString());
            IronOxide io = IronOxide.initialize(context, new IronOxideConfig(new PolicyCachingConfig(), null));
            Log.i("JNI", "IronOxide " + io);
            DocumentEncryptResult encryptedData = io.documentEncrypt("Test 123".getBytes(), new DocumentEncryptOpts());
            Log.i("JNI", "encryptedData" + encryptedData);
            DocumentDecryptResult decryptedResult = io.documentDecrypt(encryptedData.getEncryptedData());
            decryptedData = new String(decryptedResult.getDecryptedData());


        } catch (Exception e) {
            Log.e("JNI", "JNI error", e);
        }


        final String finalDecryptedData = decryptedData;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Decrypted Data: " + finalDecryptedData, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
```

- Run you app on an x86 emulator with the `Run` menu at the top of the window (you will have to setup an emulator the first time)
