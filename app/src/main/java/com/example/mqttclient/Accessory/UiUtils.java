package com.example.mqttclient.Accessory;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.mqttclient.R;

public class UiUtils {
    public static void showToast(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showToast(Fragment fragment, String message) {
        if (fragment != null && fragment.getContext() != null) {
            showToast(fragment.getContext(), message);
        }
    }

    public static void showSuccessToast(Context context, String message) {
        showToast(context, message);
    }

    public static void showErrorToast(Context context, String message) {
        showToast(context, message);
    }

    public static void showAlertDialog(Context context, String title, String message) {
        if (context == null) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_MQTTClient_AlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show();
    }

    public static void showAlertDialog(Fragment fragment, String title, String message) {
        if (fragment != null && fragment.getContext() != null) {
            showAlertDialog(fragment.getContext(), title, message);
        }
    }

    public static void showSuccessDialog(Context context, String message) {
        showAlertDialog(context, "Успешно", message);
    }

    public static void showSuccessDialog(Fragment fragment, String message) {
        showAlertDialog(fragment, "Успешно", message);
    }

    public static void showErrorDialog(Context context, String title, String message) {
        showAlertDialog(context, title, message);
    }

    public static void showErrorDialog(Fragment fragment, String title, String message) {
        showAlertDialog(fragment, title, message);
    }

    public static void showError(Context context, String message) {
        showErrorDialog(context, "Ошибка", message);
    }

    public static void showError(Fragment fragment, String message) {
        showErrorDialog(fragment, "Ошибка", message);
    }
}
