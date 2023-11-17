package com.app.workshop2;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class GasMonitoringService extends Service {
    // Author :Wajdi Gridha
    private static final String TAG = "GasMonitoringService";
    private static final String CHANNEL_ID = "GasNotificationChannel";

    private MqttClient mqttClient;
    private double thresholdValue = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String brokerAddress = intent.getStringExtra("brokerAddress");
            String brokerPort = intent.getStringExtra("brokerPort");
            String gasTopic = intent.getStringExtra("gasTopic");
            String thresholdValueStr = intent.getStringExtra("threshold");

            if (brokerAddress != null && brokerPort != null && gasTopic != null && thresholdValueStr != null && !thresholdValueStr.isEmpty()) {
                try {
                    double thresholdValue = Double.parseDouble(thresholdValueStr);
                    connectToMQTTBroker(brokerAddress, brokerPort, gasTopic, thresholdValue);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse threshold value", e);
                }
            } else {
                Log.e(TAG, "Incomplete or null parameters in the intent");
            }
        }

        return START_STICKY;
    }


    private void connectToMQTTBroker(String brokerAddress, String brokerPort, String gasTopic, double threshold) {
        String broker = "tcp://" + brokerAddress + ":" + brokerPort;
        String clientId = "AndroidClient" + System.currentTimeMillis();

        try {
            mqttClient = new MqttClient(broker, clientId, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.d(TAG, "Connection lost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (topic.equals(gasTopic)) {
                        String gasValueStr = new String(message.getPayload());
                        double gasValue = Double.parseDouble(gasValueStr);

                        if (gasValue > threshold) {
                            handleGasExceedingThreshold(gasValue);
                        }
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used in this example
                }
            });

            mqttClient.subscribe(gasTopic, 0);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void handleGasExceedingThreshold(double gasValue) {
        showGasExceedingThresholdNotification(gasValue);
        vibrateDevice();
    }

    private void vibrateDevice() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(500);
        }
    }

    private void showGasExceedingThresholdNotification(double gasValue) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
                .setContentTitle("Gas Alert!")
                .setContentText("Gas value exceeds threshold: " + gasValue)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Gas Notifications";
            String description = "Channel for gas notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}
