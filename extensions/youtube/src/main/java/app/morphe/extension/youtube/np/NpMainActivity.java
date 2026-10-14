package app.morphe.extension.youtube.np;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class NpMainActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final TextView tv = new TextView(this);
        tv.setText("NewPipe downloader: hello");
        tv.setTextSize(20);
        tv.setPadding(48, 96, 48, 48);
        setContentView(tv);
    }
}
