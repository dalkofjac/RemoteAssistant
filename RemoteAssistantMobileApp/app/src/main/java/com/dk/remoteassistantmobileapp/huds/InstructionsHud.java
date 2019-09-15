package com.dk.remoteassistantmobileapp.huds;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dk.remoteassistantmobileapp.ConferenceCallActivity;
import com.dk.remoteassistantmobileapp.R;
import com.iristick.smartglass.support.app.HudPresentation;

public class InstructionsHud extends HudPresentation {

    private TextView mDisplayText;
    private ImageView mDisplayImage;

    public InstructionsHud(Activity outerContext, Display display)
    {
        super(outerContext, display);
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
}
