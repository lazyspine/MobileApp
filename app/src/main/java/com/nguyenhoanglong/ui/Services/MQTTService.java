package com.nguyenhoanglong.ui.Services;

import android.content.Context;
import android.util.Log;
import com.nguyenhoanglong.ui.Data.DeviceDatabaseHelper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

public class MQTTService {
    private static final String TAG = "MQTTService";
    private static final String BROKER_URL = "192.168.1.13";
    private static final int BROKER_PORT = 1883;
    private static final String DEFAULT_TOPIC_1 = "/led/control";
    private static final String DEFAULT_TOPIC_2 = "/speech/control";
    private static MQTTService instance;
    private final Mqtt3AsyncClient client;
    private final String clientId;
    private MQTTCallback callback;
    private final DeviceDatabaseHelper dbHelper;
    private final Context context;
    private boolean isInitialized = false;

    public interface MQTTCallback {
        void onMessageReceived(String topic, String message);
        void onConnectionLost(Throwable cause);
        void onConnected();
    }


    private MQTTService(Context context) {
        this.context = context.getApplicationContext();
        this.clientId = "AndroidClient_" + System.currentTimeMillis();
        this.dbHelper = DeviceDatabaseHelper.getInstance(context); // Lấy instance
        this.client = MqttClient.builder()
                .useMqttVersion3()
                .serverHost(BROKER_URL)
                .serverPort(BROKER_PORT)
                .identifier(clientId)
                .buildAsync();
        connect();
        isInitialized = true;
    }

    public static MQTTService getInstance(Context context) {
        if (instance == null) {
            synchronized (MQTTService.class) {
                if (instance == null) {
                    instance = new MQTTService(context);
                }
            }
        }
        return instance;
    }


    public void setCallback(MQTTCallback callback) {
        this.callback = callback;
    }


    public void connect() {
        if (!isConnected()) {
            client.connectWith()
                    .send()
                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to connect: " + throwable.getMessage());
                            if (callback != null) {
                                callback.onConnectionLost(throwable);
                            }
                        } else {
                            Log.d(TAG, "Connected to MQTT broker");

                            subscribe(DEFAULT_TOPIC_1, MqttQos.AT_LEAST_ONCE);
                            subscribe(DEFAULT_TOPIC_2, MqttQos.AT_LEAST_ONCE);

                            client.subscribeWith()
                                    .topicFilter(DEFAULT_TOPIC_1)
                                    .qos(MqttQos.AT_LEAST_ONCE)
                                    .send()
                                    .thenCompose(v -> client.subscribeWith()
                                            .topicFilter(DEFAULT_TOPIC_2)
                                            .qos(MqttQos.AT_LEAST_ONCE)
                                            .send())
                                    .whenComplete((subAck, subThrowable) -> {
                                        if (subThrowable != null) {
                                            Log.e(TAG, "Subscription failed: " + subThrowable.getMessage());
                                        } else if (callback != null) {
                                            callback.onConnected();
                                        }
                                    });
                        }
                    });
        } else {
            Log.d(TAG, "Already connected to MQTT broker");

            subscribe(DEFAULT_TOPIC_1, MqttQos.AT_LEAST_ONCE);
            subscribe(DEFAULT_TOPIC_2, MqttQos.AT_LEAST_ONCE);
            if (callback != null) {
                callback.onConnected();
            }
        }
    }


    public void subscribe(String topic, MqttQos qos) {
        if (isConnected()) {
            client.subscribeWith()
                    .topicFilter(topic)
                    .qos(qos)
                    .callback(publish -> {
                        String message = new String(publish.getPayloadAsBytes());
//                        Log.d(TAG, "Received message: " + message + " from topic: " + topic);
                        dbHelper.handleMqttMessage(topic,message);
                        if (callback != null) {
                            callback.onMessageReceived(topic, message);
                        }
                    })
                    .send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to subscribe to " + topic + ": " + throwable.getMessage());
                        } else {
                            Log.d(TAG, "Subscribed to topic: " + topic);
                        }
                    });
        } else {
            Log.w(TAG, "Cannot subscribe to " + topic + ": MQTT not connected");
        }
    }


    public void publish(String topic, String message, MqttQos qos) {
        if (isConnected()) {
            client.publishWith()
                    .topic(topic)
                    .qos(qos)
                    .payload(message.getBytes())
                    .send()
                    .whenComplete((publish, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to publish to " + topic + ": " + throwable.getMessage());
                        } else {
                            Log.d(TAG, "Published: " + message + " to " + topic);
                        }
                    });
        } else {
            Log.w(TAG, "Cannot publish to " + topic + ": MQTT not connected");
        }
    }

    public void disconnect() {
        if (isConnected()) {
            client.disconnect()
                    .whenComplete((voidResult, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to disconnect: " + throwable.getMessage());
                        } else {
                            Log.d(TAG, "Disconnected from MQTT broker");
                        }
                    });
        } else {
            Log.d(TAG, "Already disconnected from MQTT broker");
        }
    }

    public boolean isConnected() {
        return client.getState().isConnected();
    }
}