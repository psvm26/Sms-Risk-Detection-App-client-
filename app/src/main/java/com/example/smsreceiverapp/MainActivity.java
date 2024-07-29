package com.example.smsreceiverapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityMessage";
    private SmsReceiver smsReceiver;
    private BroadcastReceiver smsBroadcastReceiver;
    private static final String SERVER_URL = "http://10.249.189.249:1931/test";
    private static final String CHANNEL_ID = "server_response_channel";
    private static final int NOTIFICATION_ID = 1;
    private NotificationManager notificationManager;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();

        // 获取 TextView 引用
        TextView messageTextView = findViewById(R.id.notification_message);

        // 处理通知传递的数据
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("NOTIFICATION_MESSAGE")) {
            String message = intent.getStringExtra("NOTIFICATION_MESSAGE");
            messageTextView.setText(message);  // 显示通知消息
        }

        // 初始化并注册 SmsReceiver
        smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, filter);

        // 注册广播接收器以接收 SmsReceiver 发送的广播
        smsBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SmsReceiver.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                    String message = intent.getStringExtra(SmsReceiver.EXTRA_MESSAGE);

                    // 处理接收到的短信
                    Log.d(TAG,  "message:" + message);
                    sendSmsToServer(message);
                }
            }
        };
        IntentFilter broadcastFilter = new IntentFilter(SmsReceiver.SMS_RECEIVED_ACTION);
        registerReceiver(smsBroadcastReceiver, broadcastFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消注册 SmsReceiver 和广播接收器
        unregisterReceiver(smsReceiver);
        unregisterReceiver(smsBroadcastReceiver);
    }

    // 发送短信到服务器的方法
    public void sendSmsToServer(String message) {
        executorService.execute(() -> {
            String response = doPostRequest(message);
            if (response != null) {
                runOnUiThread(() -> showNotification("风险识别",response));
            }
        });
    }

    private String doPostRequest(String message) {
        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            json.put("text", message);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();
        Log.i(TAG, "wait connect");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.i(TAG, "connect fail: " + response.code());
                throw new IOException("Unexpected code " + response);
            }
            String responseBody = response.body().string();
            JSONObject jsonObject = new JSONObject(responseBody);
            String textValue = jsonObject.getString("text");
            Log.i(TAG, "Response: " + textValue);
            return textValue;
        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
            e.printStackTrace();
            return null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // 展示 AlertDialog
    private void showAlertDialog(String message) {
        Log.i(TAG, "showAlertDialog: " + message);
        try {
            new AlertDialog.Builder(this)
                    .setTitle("服务器响应")
                    .setMessage(message)
                    .setPositiveButton("确定", (dialog, which) -> dialog.dismiss())
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show alert dialog", e);
        }
    }

    private void createNotificationChannel() {
        Log.i(TAG, "createNotificationChannel: Called");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Server Response Channel";
            String description = "Channel for server response notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.i(TAG, "createNotificationChannel: success!");
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        }
    }

    private void showNotification(String title, String message) {
        Log.i(TAG, "showNotification: " + message);
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)  // 你需要在项目中添加一个图标
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            // 如果需要点击通知后打开某个Activity
            // 创建 Intent 和 PendingIntent
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("NOTIFICATION_MESSAGE", message);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE  // 添加 FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);

            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                Log.i(TAG, "showNotification: ok");
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification", e);
        }
    }
}
