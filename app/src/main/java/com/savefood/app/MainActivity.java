package com.savefood.app;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.savefood.app.ui.home.HomeFragment;
import com.savefood.app.ui.recipe.RecipeFragment;
import com.savefood.app.ui.scan.ScanFragment;
import com.savefood.app.ui.settings.SettingsFragment;

/** 하단 탭(홈/스캔/레시피/설정)을 가진 메인 화면. */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_home) f = new HomeFragment();
            else if (id == R.id.nav_scan) f = new ScanFragment();
            else if (id == R.id.nav_recipe) f = new RecipeFragment();
            else f = new SettingsFragment();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    /** 다른 화면에서 탭을 전환할 때 사용 (예: 홈 → 스캔) */
    public void switchTab(@IdRes int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }
}
