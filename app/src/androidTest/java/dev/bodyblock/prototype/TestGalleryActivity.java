package dev.bodyblock.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.BitmapFactory;
import android.widget.*;
import java.io.InputStream;

/** Separate test-APK activity so MediaProjection exercises another application's window. */
public class TestGalleryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0,120,0,80);
        layout.setBackgroundColor(android.graphics.Color.WHITE);
        TextView title = new TextView(this);
        title.setText("BodyBlock Test Gallery"); title.setTextSize(24);
        title.setTextColor(android.graphics.Color.BLACK); layout.addView(title);
        ImageView image = new ImageView(this);
        try (InputStream input = getAssets().open("astronaut.png")) {
            image.setImageBitmap(BitmapFactory.decodeStream(input));
        } catch (Exception e) { throw new RuntimeException(e); }
        image.setAdjustViewBounds(true);
        image.setContentDescription("NASA astronaut test image");
        layout.addView(image,new LinearLayout.LayoutParams(-1,-2));
        Button button = new Button(this);
        button.setText("Tap to verify touch");
        button.setOnClickListener(view -> button.setText("Touch works"));
        layout.addView(button);
        setContentView(layout);
    }
}
