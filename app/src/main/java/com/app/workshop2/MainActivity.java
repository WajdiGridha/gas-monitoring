package com.app.workshop2;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.eclipse.paho.client.mqttv3.*;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
// Author :Wajdi Gridha
    private EditText brokerAddressEditText;
    private EditText brokerPortEditText;
    private EditText gasTopicEditText;
    private EditText thresholdEditText;
    private TextView gasValueTextView;

    private MqttClient mqttClient;
    private double thresholdValue = 0.0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadlang();
        setContentView(R.layout.activity_main);

        brokerAddressEditText = findViewById(R.id.brokerAddressEditText);
        brokerPortEditText = findViewById(R.id.brokerPortEditText);
        gasTopicEditText = findViewById(R.id.gasTopicEditText);
        thresholdEditText = findViewById(R.id.thresholdEditText);
        gasValueTextView = findViewById(R.id.gasValueTextView);



        Button startButton = findViewById(R.id.startButton);
        Button changeLang = findViewById(R.id.changeMyLang);
        loadSavedValues();
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Set the threshold value
                String thresholdStr = thresholdEditText.getText().toString();
                if (!thresholdStr.isEmpty()) {
                    thresholdValue = Double.parseDouble(thresholdStr);
                }

                Intent serviceIntent = new Intent(MainActivity.this, GasMonitoringService.class);
                serviceIntent.putExtra("brokerAddress", brokerAddressEditText.getText().toString());
                serviceIntent.putExtra("brokerPort", brokerPortEditText.getText().toString());
                serviceIntent.putExtra("gasTopic", gasTopicEditText.getText().toString());
                serviceIntent.putExtra("threshold", thresholdStr);
                startService(serviceIntent);

                saveEnteredValues();
                // Connect to MQTT broker
                startButtonClick(view);
                if (validateInput()) {
                    // Informations valides, démarrer le service
                    startButtonClick(view);
                    startService(new Intent(MainActivity.this, GasMonitoringService.class));
                } else {
                    // Afficher une alerte indiquant que toutes les informations sont nécessaires
                    showAlert(R.string.title_alert, R.string.msg_alert);
                }
            }
        });

        changeLang.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeLanguageDialog(); // Affiche la boîte de dialogue pour changer la langue
            }
        });


    }


    private void showChangeLanguageDialog() {
        final String[] languages = {"English", "français", "العربية"};
        int selectedLanguageIndex = getLanguageIndex(getCurrentLanguage());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Language")
                .setSingleChoiceItems(languages, selectedLanguageIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedLanguage = languages[which];
                        setLocale(getLanguageCode(selectedLanguage));
                        recreate(); // Recrée l'activité pour appliquer le changement de langue
                        dialog.dismiss();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private String getLanguageCode(String language) {
        switch (language) {
            case "English":
                return "en";
            case "français":
                return "fr";
            case "العربية":
                return "ar";
            default:
                return "en"; // Utilisez une langue par défaut au cas où la langue ne serait pas reconnue
        }
    }

    private void setLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration();
        configuration.locale = locale;
        getBaseContext().getResources().updateConfiguration(configuration, getBaseContext().getResources().getDisplayMetrics());

        // Sauvegarde la langue sélectionnée dans SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences("Settings", MODE_PRIVATE).edit();
        editor.putString("MyLang", languageCode);
        editor.apply();
    }

    private void loadlang() {
        SharedPreferences sharedPreferences = getSharedPreferences("Settings", Activity.MODE_PRIVATE);
        String lang = sharedPreferences.getString("MyLang", "");
        setLocale(lang);
    }

    private String getCurrentLanguage() {
        Configuration config = getResources().getConfiguration();
        return config.locale.getLanguage();
    }

    private int getLanguageIndex(String language) {
        String[] languages = {"en", "fr", "ar"};
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(language)) {
                return i;
            }
        }
        return 0; // Retourne 0 (Anglais) si la langue actuelle n'est pas trouvée
    }




    private boolean validateInput() {
        String brokerAddress = brokerAddressEditText.getText().toString();
        String brokerPort = brokerPortEditText.getText().toString();
        String gasTopic = gasTopicEditText.getText().toString();
        String thresholdStr = thresholdEditText.getText().toString();

        return !brokerAddress.isEmpty() && !brokerPort.isEmpty() && !gasTopic.isEmpty() && !thresholdStr.isEmpty();
    }

    private void showAlert(int title, int message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // L'utilisateur a appuyé sur OK
                    }
                });

        // Utilisez create() pour obtenir l'instance de l'alerte avant d'appeler show()
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }





    private void connectToMQTTBroker() {
        String brokerAddress = brokerAddressEditText.getText().toString();
        String brokerPort = brokerPortEditText.getText().toString();
        final String broker = "tcp://" + brokerAddress + ":" + brokerPort;
        final String clientId = "AndroidCl" + System.currentTimeMillis();

        try {
            mqttClient = new MqttClient(broker, clientId, null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // Handle connection lost
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Handle incoming messages
                    if (topic.equals(gasTopicEditText.getText().toString())) {
                        String gasValueStr = new String(message.getPayload());
                        displayGasValue(gasValueStr);

                        // Check if the gas value exceeds the threshold
                        double gasValue = Double.parseDouble(gasValueStr);
                        if (gasValue > thresholdValue) {
                            handleGasExceedingThreshold(gasValue);
                        }
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used in this example
                }
            });

            mqttClient.subscribe(gasTopicEditText.getText().toString(), 0);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void displayGasValue(final String value) {
        final double gasValue = Double.parseDouble(value);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gasValueTextView.setText(value);

                // Change text color based on threshold
                int textColor = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    textColor = (gasValue > thresholdValue) ? getColor(R.color.colorRed) : getColor(R.color.colorGreen);
                }
                gasValueTextView.setTextColor(textColor);

                Toast.makeText(getApplicationContext(), value, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void handleGasExceedingThreshold(final double gasValue) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Display a notification
                if (gasValue < thresholdValue) {
                    displayGasValue(String.valueOf(gasValue));

                }else{
                    showGasExceedingThresholdNotification(gasValue);
                    vibrateDevice();

                }
            }
        });
    }

    private void vibrateDevice() {
        // Get the Vibrator service
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Check if the device supports vibration
        if (vibrator != null && vibrator.hasVibrator()) {
            // Vibrate for 500 milliseconds
            vibrator.vibrate(1000);
        }
    }
    private void showGasExceedingThresholdNotification(double gasValue) {
        // Create a notification channel if the device is running Android Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "GasNotificationChannel";
            CharSequence channelName = "Gas Notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "GasNotificationChannel");
        builder.setSmallIcon(R.drawable.ic_baseline_notifications_active_24);
        builder.setContentTitle(getString(R.string.alert));
        builder.setContentText(getString(R.string.content_text)+" "+ gasValue);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }


    public void startButtonClick(View view) {
        // Set the threshold value
        String thresholdStr = thresholdEditText.getText().toString();
        if (!thresholdStr.isEmpty()) {
            thresholdValue = Double.parseDouble(thresholdStr);
        }

        // Connect to MQTT broker
        connectToMQTTBroker();

    }

    private void loadSavedValues() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        brokerAddressEditText.setText(preferences.getString("brokerAddress", ""));
        brokerPortEditText.setText(preferences.getString("brokerPort", ""));
        gasTopicEditText.setText(preferences.getString("gasTopic", ""));
        thresholdEditText.setText(preferences.getString("threshold", ""));
    }

    private void saveEnteredValues() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("brokerAddress", brokerAddressEditText.getText().toString());
        editor.putString("brokerPort", brokerPortEditText.getText().toString());
        editor.putString("gasTopic", gasTopicEditText.getText().toString());
        editor.putString("threshold", thresholdEditText.getText().toString());
        editor.apply();
    }


}
