package com.jostmate.signaturepad;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.joesmate.signaturepad.views.SignaturePad;

public class MainActivity extends AppCompatActivity {

    SignaturePad signaturePad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        signaturePad = (SignaturePad) findViewById(R.id.SignaturePad);
        signaturePad.setOnSignedListener(new SignaturePad.OnSignedListener() {
            @Override
            public void onStartSigning() {

            }

            @Override
            public void onSigned() {

            }

            @Override
            public void onClear() {

            }
        });
    }
}
