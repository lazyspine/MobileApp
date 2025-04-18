package com.nguyenhoanglong.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.splashscreen.SplashScreen;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nguyenhoanglong.ui.Data.DeviceDatabaseHelper;
import com.nguyenhoanglong.ui.Services.MQTTService;
import com.nguyenhoanglong.ui.models.ESPDevice;
import com.espressif.wifi_provisioning.R;
import com.google.android.material.button.MaterialButton;
import com.hivemq.client.mqtt.datatypes.MqttQos;

import java.util.List;

public class NewScreenActivity extends AppCompatActivity implements MQTTService.MQTTCallback {

    private static final String TAG = "HomeActivity";
    private static final int GRID_SPAN_COUNT = 1;

    private DeviceDatabaseHelper dbHelper;
    private RecyclerView deviceRecyclerView;
    private static MQTTService mqttService;
    private DeviceAdapter deviceAdapter;
    private List<ESPDevice> deviceList;
    private MaterialButton addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeSplashScreen();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_screen);
        
        initializeComponents();
        setupRecyclerView();
        setupAddButton();
        initializeMQTT();
        loadDevices();
    }

    private void initializeSplashScreen() {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> 
            deviceRecyclerView.getAdapter() == null && (deviceList != null && !deviceList.isEmpty())
        );
    }

    private void initializeComponents() {
        dbHelper = DeviceDatabaseHelper.getInstance(this);
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView);
        addButton = findViewById(R.id.addButton);
    }

    private void setupRecyclerView() {
        deviceRecyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        deviceRecyclerView.setHasFixedSize(true);
    }

    private void setupAddButton() {
        addButton.setOnClickListener(v -> {
            Toast.makeText(this, "Adding new device...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AddDeviceActivity.class));
        });
    }

    private void initializeMQTT() {
        mqttService = MQTTService.getInstance(this);
        mqttService.setCallback(this);
    }

    private void loadDevices() {
        deviceList = dbHelper.getAllDevices();
        if (deviceList == null || deviceList.isEmpty()) {
            showNoDevicesMessage();
        } else {
            setupDeviceAdapter();
        }
    }

    private void showNoDevicesMessage() {
        Toast.makeText(this, "No devices available. Add a new device!", Toast.LENGTH_LONG).show();
    }

    private void setupDeviceAdapter() {
        deviceAdapter = new DeviceAdapter(this, deviceList, mqttService);
        deviceRecyclerView.setAdapter(deviceAdapter);
    }

    @Override
    public void onMessageReceived(String topic, String message) {
        runOnUiThread(() -> {
            Log.d(TAG, "Received message: " + message + " from topic: " + topic);
        });
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        Log.e(TAG, "MQTT Connection lost: " + cause.getMessage());
    }

    @Override
    public void onConnected() {
        if (deviceList != null && !deviceList.isEmpty()) {
            for (ESPDevice device : deviceList) {
                mqttService.subscribe(device.getCommandTopic(), MqttQos.AT_LEAST_ONCE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttService != null) {
            mqttService.disconnect();
        }
    }
}