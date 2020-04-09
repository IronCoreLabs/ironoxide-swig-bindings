package com.ironcorelabs.example_application;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ironcorelabs.sdk.DeviceContext;
import com.ironcorelabs.sdk.DocumentDecryptResult;
import com.ironcorelabs.sdk.DocumentEncryptOpts;
import com.ironcorelabs.sdk.DocumentEncryptResult;
import com.ironcorelabs.sdk.DocumentName;
import com.ironcorelabs.sdk.GroupId;
import com.ironcorelabs.sdk.IronOxide;
import com.ironcorelabs.sdk.IronOxideConfig;
import com.ironcorelabs.sdk.UserId;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements MyRecyclerViewAdapter.ItemClickListener {
    private EditText encryptText;
    private EditText encryptId;
    private TextView decryptView;
    private IronOxide sdk;
    private final ArrayList<DocumentEncryptResult> encryptedList = new ArrayList<>();
    private MyRecyclerViewAdapter adapter;

    private IronOxide initializeSdk() {
        System.loadLibrary("ironoxide_android");
        InputStream in = getResources().openRawResource(R.raw.device_context);
        final java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
        final String deviceString = s.hasNext() ? s.next() : "";
        try {
            DeviceContext device = DeviceContext.fromJsonString(deviceString);
            return IronOxide.initialize(device, new IronOxideConfig());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("JNI", "Failed to load DeviceContext");
            return null;
        }
    }

    private DocumentEncryptResult encryptText(String text, String maybe_name) throws Exception {
        DocumentName name = null;
        if (!maybe_name.equals(""))
            name = DocumentName.validate(maybe_name);
        return sdk.documentEncrypt(text.getBytes(), new DocumentEncryptOpts(null,
                name, true, new UserId[0], new GroupId[0], null));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name_long);
        encryptText = findViewById(R.id.dataText);
        encryptId = findViewById(R.id.nameText);
        decryptView = findViewById(R.id.decryptView);

        RecyclerView recyclerView = findViewById(R.id.documentList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyRecyclerViewAdapter(this, encryptedList);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);

        sdk = initializeSdk();

        findViewById(R.id.encryptButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Editable text = encryptText.getText();
                    Editable id = encryptId.getText();
                    DocumentEncryptResult encryptResult = encryptText(text.toString(), id.toString());
                    encryptedList.add(encryptResult);
                    adapter.notifyDataSetChanged();
                    decryptView.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e("JNI", "Encrypt Error: " + e);
                }
            }
        });
    }

    @Override
    public void onItemClick(int position) {
        try {
            byte[] text = encryptedList.get(position).getEncryptedData();
            DocumentDecryptResult decryptResult = sdk.documentDecrypt(text);
            DecryptDialog dialog = new DecryptDialog(decryptResult);
            dialog.show(getSupportFragmentManager(), "documentInfo");
        } catch (Exception e) {
            Log.e("JNI", "Decrypt Error: " + e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class DecryptDialog extends DialogFragment {
        private final String documentData;

        DecryptDialog(DocumentDecryptResult decryptedDocument) {
            String data = "";
            if (decryptedDocument.getName().isPresent()) {
                data += "Name: " + decryptedDocument.getName().get().getName() + "\n";
            }
            data += "ID: " + decryptedDocument.getId().getId() + "\n";
            data += "Created: " + decryptedDocument.getCreated() + "\n";
            data += "\nData: " + new String(decryptedDocument.getDecryptedData()) + "\n";
            documentData = data;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(Objects.requireNonNull(getActivity()));
            builder.setMessage(documentData)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            Dialog dialog = builder.show();
            TextView textView = dialog.findViewById(android.R.id.message);
            Objects.requireNonNull(textView).setTextSize(15);
            return dialog;
        }
    }
}
