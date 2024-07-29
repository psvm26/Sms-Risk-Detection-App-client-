package com.example.smsreceiverapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiverMessage";
    public static final String SMS_RECEIVED_ACTION = "com.example.smsreceiverapp.SMS_RECEIVED";
    private static final Set<String> processedMessages = Collections.synchronizedSet(new HashSet<>());
    private static final int SMS_TIMEOUT = 5000; // 5 seconds
    private Map<String, StringBuilder> smsMap = new HashMap<>();
    private Map<String, Timer> smsTimers = new HashMap<>();
    public static final String EXTRA_SENDER = "sender";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format"); // 获取短信格式
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                    String sender = smsMessage.getDisplayOriginatingAddress();
                    String message = smsMessage.getDisplayMessageBody();
                    String timestamp = String.valueOf(smsMessage.getTimestampMillis());
                    String messageHash = sender + message + timestamp;
                    if (processedMessages.contains(messageHash)) {
                        Log.d(TAG, "Duplicate message detected, skipping.");
                        continue;
                    }
                    processedMessages.add(messageHash);

                    Log.d(TAG,"Message: " + message);
                    handleSms(sender, message, timestamp, context);
                }
            }
        }
    }

    private void handleSms(String sender, String message, String timestamp, Context context) {
        if (!smsMap.containsKey(sender)) {
            smsMap.put(sender, new StringBuilder());
        }
        smsMap.get(sender).append(message);

        if (smsTimers.containsKey(sender)) {
            smsTimers.get(sender).cancel();
        }

        Timer timer = new Timer();
        smsTimers.put(sender, timer);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String completeMessage = smsMap.get(sender).toString();
                smsMap.remove(sender);
                smsTimers.remove(sender);

                // 在这里处理组装后的完整短信
                Log.d(TAG, "Complete SMS from: " + sender + ", content: " + completeMessage);

                // 发送广播，将短信内容传递到 MainActivity
                Intent broadcastIntent = new Intent(SMS_RECEIVED_ACTION);
                broadcastIntent.putExtra(EXTRA_MESSAGE, completeMessage);
                context.sendBroadcast(broadcastIntent);

            }
        }, SMS_TIMEOUT);
    }
}
