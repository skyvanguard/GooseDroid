package com.skyvanguard.goozy;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.skyvanguard.goozy.engine.TheGoose;
import com.skyvanguard.goozy.overlay.GooseOverlayService;

import java.io.File;
import java.util.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private MaterialToolbar DefToolBar;
    private Switch overlaySwitch;
    private Switch SilenceSounds;
    private Switch ShowShadow;
    private EditText DrawSize;
    private MaterialButton SaveConfig;
    private MaterialButton Update;

    // Pet mode UI elements
    private Switch PetModeSwitch;
    private Switch TouchableSwitch;
    private ProgressBar HungerBar;
    private ProgressBar EnergyBar;
    private ProgressBar HappinessBar;
    private TextView PetStatusText;
    private MaterialButton FeedButton;
    private MaterialButton PlayButton;
    private MaterialButton SleepButton;
    private ProgressBar HygieneBar;
    private ProgressBar HealthBar;
    private MaterialButton CleanButton;
    private MaterialButton HealButton;
    private MaterialButton CustomizeButton;
    private Handler petStatusHandler;

    private String ConfigFilePath = "";
    private boolean isTouchable = true;
    private boolean isSyncingOverlaySwitch = false;
    private static final int REQUEST_POST_NOTIFICATIONS = 5005;
    private static final int REQUEST_OVERLAY_PERMISSION = 5003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DefToolBar = findViewById(R.id.DefToolBar);
        overlaySwitch = findViewById(R.id.overlaySwitch);
        SilenceSounds = findViewById(R.id.SilenceSounds);
        ShowShadow = findViewById(R.id.ShowShadow);
        DrawSize = findViewById(R.id.DrawSize);
        SaveConfig = findViewById(R.id.SaveConfig);
        Update = findViewById(R.id.Update);

        loadConfigFile();

        // Initialize notification manager
        PetNotificationManager.init(this);

        // Set up toolbar
        this.setSupportActionBar(DefToolBar);

        overlaySwitch.setChecked(GooseOverlayService.isRunning());
        overlaySwitch.setOnCheckedChangeListener((cb, isEnabled) -> {
            if (isSyncingOverlaySwitch) return;
            if (!isEnabled) {
                GooseOverlayService.stop(MainActivity.this);
                Utils.showToast(MainActivity.this, getText(R.string.GoozyDisabled));
                return;
            }
            if (!OverlayPermission.isGranted(MainActivity.this)) {
                overlaySwitch.setChecked(false);
                Utils.showToast(MainActivity.this, getText(R.string.PleaseEnableFloatingWindowPermission));
                OverlayPermission.openSettings(MainActivity.this, REQUEST_OVERLAY_PERMISSION);
                return;
            }
            requestNotificationPermissionIfNeeded();
            // El servicio lee la configuración de disco: guardar lo que hay en pantalla
            savePetState();
            GooseOverlayService.start(MainActivity.this);
            Utils.showToast(MainActivity.this, getText(R.string.GoozyEnabled));
        });

        ShowShadow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TheGoose.setShowShadow(ShowShadow.isChecked());
            }
        });

        DrawSize.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                try {
                    float size = Float.parseFloat(DrawSize.getText().toString());
                    // Validate input range to prevent rendering issues
                    if (size < 0.1f) {
                        size = 0.1f;
                        DrawSize.setText(String.valueOf(size));
                        Utils.showToast(MainActivity.this, getText(R.string.DrawSizeTooSmall));
                    } else if (size > 10.0f) {
                        size = 10.0f;
                        DrawSize.setText(String.valueOf(size));
                        Utils.showToast(MainActivity.this, getText(R.string.DrawSizeTooLarge));
                    }
                    GooseOverlayService.setWorldScale(size);
                } catch (NumberFormatException e) {
                    // Invalid number format - reset to default
                    GooseOverlayService.setWorldScale(1.0f);
                    DrawSize.setText("1.0");
                    Utils.showToast(MainActivity.this, getText(R.string.InvalidDrawSize));
                } catch (Exception e) {
                    e.printStackTrace();
                    showErrorAlert(e);
                }
                return false;
            }
        });

        SaveConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveConfigFile();
                Utils.showToast(view.getContext(), getText(R.string.SaveSuccessfully));
            }
        });

        Update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });

        // Initialize pet mode UI elements
        initPetModeUI();
    }

    /**
     * Initialize pet mode UI elements and listeners.
     */
    private void initPetModeUI() {
        PetModeSwitch = findViewById(R.id.PetModeSwitch);
        TouchableSwitch = findViewById(R.id.TouchableSwitch);
        HungerBar = findViewById(R.id.HungerBar);
        EnergyBar = findViewById(R.id.EnergyBar);
        HappinessBar = findViewById(R.id.HappinessBar);
        PetStatusText = findViewById(R.id.PetStatusText);
        FeedButton = findViewById(R.id.FeedButton);
        PlayButton = findViewById(R.id.PlayButton);
        SleepButton = findViewById(R.id.SleepButton);
        HygieneBar = findViewById(R.id.HygieneBar);
        HealthBar = findViewById(R.id.HealthBar);
        CleanButton = findViewById(R.id.CleanButton);
        HealButton = findViewById(R.id.HealButton);
        if (CleanButton != null) {
            CleanButton.setOnClickListener(v -> {
                TheGoose.startCleaning();
                Utils.showToast(this, getText(R.string.CleaningPet));
            });
        }
        if (HealButton != null) {
            HealButton.setOnClickListener(v -> {
                TheGoose.startHealing();
                Utils.showToast(this, getText(R.string.HealingPet));
            });
        }

        // Set up pet status update handler
        petStatusHandler = new Handler(Looper.getMainLooper());

        // Pet mode switch listener
        if (PetModeSwitch != null) {
            PetModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                TheGoose.petModeEnabled = isChecked;
                updatePetModeUIVisibility(isChecked);
                savePetState();
            });
        }

        // Touchable switch listener
        if (TouchableSwitch != null) {
            TouchableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isTouchable = isChecked;
                updateWindowTouchability();
            });
        }

        // Feed button
        if (FeedButton != null) {
            FeedButton.setOnClickListener(v -> {
                TheGoose.startEating();
                Utils.showToast(this, getText(R.string.FeedingPet));
            });
        }

        // Play button
        if (PlayButton != null) {
            PlayButton.setOnClickListener(v -> {
                if (PetNeeds.get().energy > 20) {
                    TheGoose.startPlaying();
                    Utils.showToast(this, getText(R.string.PlayingWithPet));
                } else {
                    Utils.showToast(this, getText(R.string.PetTooTired));
                }
            });
        }

        // Sleep button
        if (SleepButton != null) {
            SleepButton.setOnClickListener(v -> {
                TheGoose.startSleeping();
                Utils.showToast(this, getText(R.string.PetSleeping));
            });
        }

        View brainButton = findViewById(R.id.BrainButton);
        if (brainButton != null) {
            brainButton.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, BrainActivity.class)));
        }

        // Customize button
        CustomizeButton = findViewById(R.id.CustomizeButton);
        if (CustomizeButton != null) {
            CustomizeButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CustomizeActivity.class);
                startActivity(intent);
            });
        }

        // Load saved pet state
        loadPetState();

        // Start periodic UI updates
        startPetStatusUpdates();
    }

    /**
     * Update pet status UI visibility based on pet mode.
     */
    private void updatePetModeUIVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (HungerBar != null) HungerBar.setVisibility(visibility);
        if (EnergyBar != null) EnergyBar.setVisibility(visibility);
        if (HappinessBar != null) HappinessBar.setVisibility(visibility);
        if (PetStatusText != null) PetStatusText.setVisibility(visibility);
        if (FeedButton != null) FeedButton.setVisibility(visibility);
        if (PlayButton != null) PlayButton.setVisibility(visibility);
        if (SleepButton != null) SleepButton.setVisibility(visibility);
        if (HygieneBar != null) HygieneBar.setVisibility(visibility);
        if (HealthBar != null) HealthBar.setVisibility(visibility);
        if (CleanButton != null) CleanButton.setVisibility(visibility);
        if (HealButton != null) HealButton.setVisibility(visibility);
        View hygieneLabel = findViewById(R.id.HygieneLabel);
        View healthLabel = findViewById(R.id.HealthLabel);
        if (hygieneLabel != null) hygieneLabel.setVisibility(visibility);
        if (healthLabel != null) healthLabel.setVisibility(visibility);
        if (CustomizeButton != null) CustomizeButton.setVisibility(visibility);
        if (TouchableSwitch != null) TouchableSwitch.setVisibility(visibility);

        // Also show/hide labels
        View hungerLabel = findViewById(R.id.HungerLabel);
        View energyLabel = findViewById(R.id.EnergyLabel);
        View happinessLabel = findViewById(R.id.HappinessLabel);
        if (hungerLabel != null) hungerLabel.setVisibility(visibility);
        if (energyLabel != null) energyLabel.setVisibility(visibility);
        if (happinessLabel != null) happinessLabel.setVisibility(visibility);
    }

    /**
     * Update window touchability at runtime.
     */
    private void updateWindowTouchability() {
        GooseOverlayService.setTouchEnabled(isTouchable);
    }

    /**
     * Android 13+ exige pedir el permiso de notificaciones en runtime. Sin él no
     * se ven los avisos de la mascota ni la notificación del servicio.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }

    /** El servicio puede haberse apagado desde su notificación. */
    private void syncOverlaySwitch() {
        isSyncingOverlaySwitch = true;
        overlaySwitch.setChecked(GooseOverlayService.isRunning());
        isSyncingOverlaySwitch = false;
    }

    /**
     * Start periodic updates of pet status UI.
     */
    private void startPetStatusUpdates() {
        Runnable statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePetStatusUI();
                petStatusHandler.postDelayed(this, 1000); // Update every second
            }
        };
        petStatusHandler.postDelayed(statusUpdateRunnable, 1000);
    }

    /**
     * Update pet status bars and text.
     */
    private void updatePetStatusUI() {
        if (!TheGoose.petModeEnabled) return;

        if (HungerBar != null) {
            // Invert hunger for display (100 = full, 0 = starving)
            HungerBar.setProgress((int) (100 - PetNeeds.get().hunger));
        }
        if (EnergyBar != null) {
            EnergyBar.setProgress((int) PetNeeds.get().energy);
        }
        if (HappinessBar != null) {
            HappinessBar.setProgress((int) PetNeeds.get().happiness);
        }
        if (HygieneBar != null) {
            HygieneBar.setProgress((int) PetNeeds.get().hygiene);
        }
        if (HealthBar != null) {
            HealthBar.setProgress((int) PetNeeds.get().health);
        }
        if (PetStatusText != null) {
            String status = PetPersonality.get().getTitle() + " - " + PetNeeds.get().getMoodStateString();
            PetStatusText.setText(status);
        }
    }

    /**
     * Save pet state to config file.
     */
    private void savePetState() {
        try {
            Properties prop = new Properties();

            // Existing config
            prop.put("SilenceSounds", capitalizeFirst(String.valueOf(SilenceSounds.isChecked())));
            prop.put("ShowShadow", capitalizeFirst(String.valueOf(ShowShadow.isChecked())));
            prop.put("DrawSize", DrawSize.getText().toString());

            prop.put("TouchableEnabled", capitalizeFirst(String.valueOf(isTouchable)));

            // El estado de la mascota lo agrega el repositorio
            PetRepository.save(this, prop);
            PetWidget.updateAllWidgets(this);
        } catch (RuntimeException e) {
            android.util.Log.e("MainActivity", "Error guardando el estado", e);
        }
    }

    /**
     * Load pet state from config file.
     */
    private void loadPetState() {
        try {
            PetConfigFile ca = new PetConfigFile();
            ca.load(new File(ConfigFilePath));

            // Load pet mode setting
            String petModeStr = ca.get("PetModeEnabled");
            boolean petModeEnabled = petModeStr != null && string2boolean(petModeStr);
            TheGoose.petModeEnabled = petModeEnabled;
            if (PetModeSwitch != null) {
                PetModeSwitch.setChecked(petModeEnabled);
            }
            updatePetModeUIVisibility(petModeEnabled);

            // Load touchable setting
            String touchableStr = ca.get("TouchableEnabled");
            isTouchable = touchableStr == null || string2boolean(touchableStr);
            if (TouchableSwitch != null) {
                TouchableSwitch.setChecked(isTouchable);
            }

            // Necesidades, personalidad y apariencia: una sola vez por proceso
            PetRepository.ensureLoaded(this);

        } catch (Exception e) {
            e.printStackTrace();
            // Use defaults on error
            PetNeeds.get().reset();
            PetPersonality.get().reset();
            PetAppearance.get().reset();
        }
    }

    private float parseFloatSafe(String value, float defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLongSafe(String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void showErrorAlert(Exception e) {
        Utils.showDialog(MainActivity.this, MainActivity.class.getName() + " - Error", e.toString());
    }

    private void loadConfigFile() {
        try {
            ConfigFilePath = Utils.getPrivateDir(this) + "/config.ini";

            if (!Utils.fileExists(ConfigFilePath)) {
                Utils.copyAssetFile(this, "config.ini", ConfigFilePath);
            }
            PetConfigFile ca = new PetConfigFile();
            ca.load(new File(ConfigFilePath));
            SilenceSounds.setChecked(string2boolean(ca.get("SilenceSounds")));
            ShowShadow.setChecked(string2boolean(ca.get("ShowShadow")));
            TheGoose.setShowShadow(ShowShadow.isChecked());
            // Set EditText contents
            setEditTextContent(DrawSize, ca.get("DrawSize"));


            Utils.showToast(MainActivity.this, "Configuration loaded successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert(e);
        }
    }

    public static boolean string2boolean(String str) {
        return str != null && Boolean.parseBoolean(str.trim().toLowerCase());
    }

    private void setEditTextContent(EditText editText, String content) {
        if (content != null) {
            editText.setText(content.toCharArray(), 0, content.length());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save pet state when app is paused
        if (TheGoose.petModeEnabled) {
            savePetState();
            // Start notification checks when app goes to background
            PetNotificationManager.startPeriodicCheck();
            // Schedule a reminder for 4 hours later
            PetNotificationManager.scheduleReminder(4 * 60 * 60 * 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncOverlaySwitch();

        // Stop notification checks when app is in foreground
        PetNotificationManager.stopPeriodicCheck();
        PetNotificationManager.cancelScheduledReminder();
        PetNotificationManager.cancelAllNotifications();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop status updates
        if (petStatusHandler != null) {
            petStatusHandler.removeCallbacksAndMessages(null);
            petStatusHandler = null;
        }

        // El overlay pertenece al servicio: cerrar la Activity no lo apaga
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(1, 1, 1, getText(R.string.ConfigFilePath));
        menu.add(1, 2, 2, getText(R.string.ResetDefaultConfig));
        menu.add(1, 3, 3, getText(R.string.SaveConfig));
        menu.add(1, 4, 4, getText(R.string.About));
        return true;
    }

    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                showMessageDialog("Config File Path", ConfigFilePath);
                break;
            case 2:
                if (Utils.fileExists(ConfigFilePath)) {
                    Utils.deleteFile(ConfigFilePath);
                }
                Utils.copyAssetFile(this, "config.ini", ConfigFilePath);
                PetRepository.reload(this);
                Utils.showToast(this, getText(R.string.ResetSuccessfully));
                restartActivity();
                break;
            case 3:
                saveConfigFile();
                Utils.showToast(this, getText(R.string.SaveSuccessfully));
                break;
            case 4:
                String about = getString(R.string.AboutText);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getText(R.string.About))
                        .setMessage(about)
                        .setPositiveButton(getText(R.string.Confirm), null)
                        .setNegativeButton(R.string.Copy,
                                (dialog, which) -> Utils.copyToClipboard(this, about))
                        .show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showMessageDialog(String title, String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getText(R.string.Confirm), (dialog, which) -> {
                    // OK button click
                })
                .setNegativeButton(R.string.Cancel, (dialog, which) -> {
                    // Cancel button click
                });
        builder.show();
    }

    public void saveConfigFile() {
        try {
            Properties prop = new Properties();
            prop.put("SilenceSounds", capitalizeFirst(String.valueOf(SilenceSounds.isChecked())));
            prop.put("ShowShadow", capitalizeFirst(String.valueOf(ShowShadow.isChecked())));
            prop.put("DrawSize", DrawSize.getText().toString());
            PetConfigFile.saveMerged(new File(ConfigFilePath), prop);
        } catch (Exception e) {
            e.printStackTrace();
            showErrorAlert(e);
        }
    }

    private String capitalizeFirst(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
