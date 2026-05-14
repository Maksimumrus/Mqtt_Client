package com.example.mqttclient;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabs = findViewById(R.id.tabs);
        SettingsPagerAdapter adapter = new SettingsPagerAdapter(this);
        viewPager.setAdapter(adapter);
        new TabLayoutMediator(tabs, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Все топики" : "Серверы");
        }).attach();
    }

    private static class SettingsPagerAdapter extends FragmentStateAdapter {
        public SettingsPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == 0 ? new AllTopicsFragment() : new ServerSettingsFragment();
        }
        @Override
        public int getItemCount() {
            return 2;
        }
    }
}