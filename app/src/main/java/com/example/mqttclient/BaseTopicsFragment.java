package com.example.mqttclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mqttclient.Accessory.MqttService;
public abstract class BaseTopicsFragment extends Fragment implements MainActivity.MqttServiceConsumer {
    protected MqttService mqttService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(getLayoutResourceId(), container, false);
    }

    protected abstract int getLayoutResourceId();

    @Override
    public void onMqttServiceReady(MqttService service) {
        this.mqttService = service;
        onMqttServiceReadyExtended(service);
    }

    protected abstract void onMqttServiceReadyExtended(MqttService service);

    protected abstract void refreshList();

    protected abstract void applyFilters();

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity && MainActivity.isMqttServiceBound()) {
            MqttService service = MainActivity.getMqttService();
            if (service != null) {
                onMqttServiceReady(service);
                refreshList();
            }
        }
        refreshList();
    }
}