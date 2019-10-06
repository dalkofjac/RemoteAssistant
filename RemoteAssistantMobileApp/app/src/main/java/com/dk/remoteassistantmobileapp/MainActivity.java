package com.dk.remoteassistantmobileapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.dk.remoteassistantmobileapp.ConferenceCallActivity;
import com.dk.remoteassistantmobileapp.R;
import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.IristickBinding;
import com.iristick.smartglass.core.IristickConnection;
import com.iristick.smartglass.support.app.IristickApp;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private final String[] mPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private boolean mIristickError = false;

    @BindView(R.id.et_room)
    public EditText mRoomName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);

        if(!hasPermissions(this, mPermissions)) {
            requestPermissions();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about_app) {
            Intent intent = new Intent(this, AboutAppActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if(!hasPermissions(this, mPermissions)){
            this.finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IristickApp.registerConnectionListener(mIristickListener, null);
    }

    @Override
    protected void onStop() {
        IristickApp.unregisterConnectionListener(mIristickListener);
        super.onStop();
    }

    @OnClick(R.id.btn_start)
    public void onBtnStartClicked() {
        if(mIristickError) {
            Toast.makeText(this, getString(R.string.error_glasses_general), Toast.LENGTH_SHORT).show();
            return;
        }

        if(mRoomName.getText().toString().length() < 2) {
            Toast.makeText(this, getString(R.string.error_room_name_leght), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ConferenceCallActivity.class);
        Bundle dataBundle = new Bundle();
        dataBundle.putString("ROOM_NAME", mRoomName.getText().toString());
        intent.putExtras(dataBundle);
        startActivity(intent);

    }

    private void showError(String message) {
        ((TextView) findViewById(R.id.error)).setText(message);
        findViewById(R.id.error_layout).setVisibility(View.VISIBLE);
    }

    private void requestPermissions(){
        int PERMISSION_ALL = 1;

        if(!hasPermissions(this, mPermissions)){
            ActivityCompat.requestPermissions(this, mPermissions, PERMISSION_ALL);
        }
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private final IristickConnection mIristickListener = new IristickConnection() {
        @Override
        public void onHeadsetConnected(Headset headset) {
        }

        @Override
        public void onHeadsetDisconnected(Headset headset) {
        }

        @Override
        public void onIristickServiceInitialized(IristickBinding binding) {
            if (IristickApp.getHeadset() == null) {
                mIristickError = true;
                showError(getString(R.string.error_no_headset));
            } else {
                mIristickError = false;
            }
        }

        @Override
        public void onIristickServiceError(int error) {
            switch (error) {
                case IristickConnection.ERROR_NOT_INSTALLED:
                    mIristickError = true;
                    showError(getString(R.string.error_not_installed));
                    break;
                case IristickConnection.ERROR_FUTURE_SDK:
                    mIristickError = true;
                    showError(getString(R.string.error_future_sdk));
                    break;
                case IristickConnection.ERROR_DEPRECATED_SDK:
                    mIristickError = true;
                    showError(getString(R.string.error_deprecated_sdk));
                    break;
                default:
                    mIristickError = true;
                    showError(getString(R.string.error_other, error));
            }
        }
    };
}
