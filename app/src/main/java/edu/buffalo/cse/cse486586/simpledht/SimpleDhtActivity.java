package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class SimpleDhtActivity extends Activity {

    private Uri mUri;

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        //PTest
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));

        //LDump
        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor cursor = getContentResolver().query(mUri, null, "@", null, null);
                if(cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        String key1 = cursor.getString(0);
                        String value1 = cursor.getString(1);
                        tv.append(key1 + "\n");
                    } while (cursor.moveToNext());
                }
            }
        });


        //gdump
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor cursor = getContentResolver().query(mUri, null, "*", null, null);
                if(cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        String key1 = cursor.getString(0);
                        String value1 = cursor.getString(1);
                        tv.append(key1 + "\n");
                    } while (cursor.moveToNext());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

}
