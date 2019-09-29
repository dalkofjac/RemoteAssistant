package com.dk.remoteassistantmobileapp.huds;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.dk.remoteassistantmobileapp.R;
import com.dk.remoteassistantmobileapp.interfaces.InstructionsHudActionsListener;
import com.iristick.smartglass.support.app.HudPresentation;

public class InstructionsHud extends HudPresentation implements InstructionsHudActionsListener {

    private Activity mActivity;

    private TextView mDisplayText;
    private ImageView mDisplayImage;

    public InstructionsHud(Activity outerContext, Display display)
    {
        super(outerContext, display);
        this.mActivity = outerContext;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.hub_instructions);
        mDisplayText = findViewById(R.id.tV_remote_display_text);
        mDisplayImage = findViewById(R.id.iV_remote_image);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onClientHangUp() {
        setVisorDisplayToDefault();
    }

    @Override
    public void onDisplayImageReceived(String imageData) {
        final String base64 = "base64";

        String imageBinary = imageData.substring(imageData.indexOf(base64) + base64.length() + 1);
        byte[] imageAsBytes = Base64.decode(imageBinary.getBytes(), 0);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDisplayText.setVisibility(View.GONE);
                mDisplayImage.setVisibility(View.VISIBLE);
                mDisplayImage.setImageBitmap(BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length));
            }
        });
    }

    private void setVisorDisplayToDefault() {
        mDisplayText.setVisibility(View.VISIBLE);
        mDisplayImage.setVisibility(View.GONE);
    }
}
