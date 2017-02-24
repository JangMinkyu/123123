package com.example.jyw.myservice;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;

public class TransparentActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 100;
    LocalBroadcastManager mLBM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLBM = LocalBroadcastManager.getInstance(this);
        Intent intent = getIntent().getParcelableExtra("data");
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            Intent intent = new Intent(TransparentActivity.class.getName());
            intent.putExtra("data", data);
            mLBM.sendBroadcast(intent);
        }
        finish();
    }

}
