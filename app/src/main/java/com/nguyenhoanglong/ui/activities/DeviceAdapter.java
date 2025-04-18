package com.nguyenhoanglong.ui.activities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.nguyenhoanglong.ui.Data.DeviceDatabaseHelper;
import com.nguyenhoanglong.ui.Services.MQTTService;
import com.nguyenhoanglong.ui.models.ESPDevice;
import com.espressif.wifi_provisioning.R;
import com.hivemq.client.mqtt.datatypes.MqttQos;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> implements MQTTService.MQTTCallback {

    private static final String TAG = "DeviceAdapter";
    private static final String DELETE_COMMAND = "deleteNVS";
    private static final String ON_COMMAND = "turn on";
    private static final String OFF_COMMAND = "turn off";
    private static final String ON_RGB_COMMAND = "onRGB";
    private static final String OFF_RGB_COMMAND = "offRGB";

    private final Context context;
    private final List<ESPDevice> deviceList;
    private final MQTTService mqttService;
    private final Handler handler;
    private final DeviceDatabaseHelper dbHelper;

    public DeviceAdapter(Context context, List<ESPDevice> devices, MQTTService mqttService) {
        this.context = context;
        this.deviceList = devices;
        this.mqttService = mqttService;
        this.handler = new Handler(Looper.getMainLooper());
        this.dbHelper = DeviceDatabaseHelper.getInstance(context);
        this.mqttService.setCallback(this);
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        ESPDevice device = deviceList.get(position);
        updateDeviceUI(holder, device);
        setupLightToggle(holder, device);
        setupMenuButton(holder, device, position);
    }

    private void updateDeviceUI(DeviceViewHolder holder, ESPDevice device) {
        holder.lightImageView.setImageResource(device.isLightOn() ? 
            R.drawable.ic_light_on : R.drawable.ic_light_off);
        holder.deviceNameTextView.setText(device.getName());
        holder.cardView.setSelected(device.isRGBMode());
    }

    private void setupLightToggle(DeviceViewHolder holder, ESPDevice device) {
        holder.lightImageView.setOnClickListener(v -> {
            boolean newState = !device.isLightOn();
            device.setLightOn(newState);
            String message = device.isRGBMode() ? 
                (newState ? ON_RGB_COMMAND : OFF_RGB_COMMAND) : 
                (newState ? ON_COMMAND : OFF_COMMAND);
            
            dbHelper.updateDevice(device);
            updateDeviceUI(holder, device);
            publishMqttMessage(device.getCommandTopic(), message);
        });
    }

    private void setupMenuButton(DeviceViewHolder holder, ESPDevice device, int position) {
        holder.menuButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(context, holder.menuButton);
            popupMenu.getMenu().add("Rename");
            popupMenu.getMenu().add("Delete");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if ("Rename".equals(title)) {
                    showRenameDialog(holder, device);
                } else if ("Delete".equals(title)) {
                    showDeleteConfirmationDialog(device, position);
                }
                return true;
            });
            popupMenu.show();
        });
    }

    private void showRenameDialog(DeviceViewHolder holder, ESPDevice device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Rename Device");

        final EditText input = new EditText(context);
        input.setText(device.getName());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                device.setName(newName);
                dbHelper.updateDevice(device);
                updateDeviceUI(holder, device);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteConfirmationDialog(ESPDevice device, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Confirm Deletion");
        builder.setMessage("Are you sure you want to delete device \"" + device.getName() + "\"?");
        
        builder.setPositiveButton("Yes, Delete", (dialog, which) -> {
            dbHelper.removeDevice(device.getDeviceId());
            publishMqttMessage(device.getCommandTopic(), DELETE_COMMAND);
            removeDeviceFromList(position);
        });
        
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void removeDeviceFromList(int position) {
        handler.post(() -> {
            if (position < deviceList.size()) {
                deviceList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, deviceList.size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    @Override
    public void onMessageReceived(String topic, String message) {
        handler.post(() -> {
            for (int i = 0; i < deviceList.size(); i++) {
                ESPDevice device = deviceList.get(i);
                if (topic.equals(device.getCommandTopic())) {
                    handleDeviceMessage(i, device, message);
                    break;
                }
            }
        });
    }

    private void handleDeviceMessage(int position, ESPDevice device, String message) {
        ESPDevice updatedDevice = dbHelper.getDeviceById(device.getDeviceId());
        if (updatedDevice != null) {
            if (DELETE_COMMAND.equals(message)) {
                deviceList.remove(position);
                notifyItemRemoved(position);
            } else if (ON_COMMAND.equals(message) || ON_RGB_COMMAND.equals(message)) {
                updatedDevice.setLightOn(true);
                deviceList.set(position, updatedDevice);
                notifyItemChanged(position);
            } else if (OFF_COMMAND.equals(message) || OFF_RGB_COMMAND.equals(message)) {
                updatedDevice.setLightOn(false);
                deviceList.set(position, updatedDevice);
                notifyItemChanged(position);
            } else if (hasDeviceStateChanged(updatedDevice, device)) {
                deviceList.set(position, updatedDevice);
                notifyItemChanged(position);
            }
        }
    }

    private boolean hasDeviceStateChanged(ESPDevice updatedDevice, ESPDevice currentDevice) {
        return updatedDevice.isLightOn() != currentDevice.isLightOn() ||
               updatedDevice.isRGBMode() != currentDevice.isRGBMode() ||
               !updatedDevice.getName().equals(currentDevice.getName());
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        Log.e(TAG, "MQTT Connection lost: " + cause.getMessage());
    }

    @Override
    public void onConnected() {
        for (ESPDevice device : deviceList) {
            mqttService.subscribe(device.getCommandTopic(), MqttQos.AT_LEAST_ONCE);
        }
    }

    private void publishMqttMessage(String topic, String message) {
        if (mqttService != null) {
            try {
                mqttService.publish(topic, message, MqttQos.AT_LEAST_ONCE);
            } catch (Exception e) {
                Log.e(TAG, "MQTT publish failed: " + e.getMessage());
            }
        }
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        final CardView cardView;
        final ImageView lightImageView;
        final TextView deviceNameTextView;
        final ImageButton menuButton;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            lightImageView = itemView.findViewById(R.id.lightImageView);
            deviceNameTextView = itemView.findViewById(R.id.deviceNameTextView);
            menuButton = itemView.findViewById(R.id.menuButton);
        }
    }
}