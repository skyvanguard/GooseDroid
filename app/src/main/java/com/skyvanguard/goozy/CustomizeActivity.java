package com.skyvanguard.goozy;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Activity para personalizar la apariencia de la mascota.
 */
public class CustomizeActivity extends AppCompatActivity {

    private TextInputEditText petNameInput;
    private RadioGroup creatureTypeGroup;
    private TextInputEditText bodyColorInput, accentColorInput, outlineColorInput;
    private View bodyColorPreview, accentColorPreview, outlineColorPreview;
    private Spinner hatSpinner, accessorySpinner;
    private TextView previewDescription;
    private View colorsCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize);

        initViews();
        loadCurrentSettings();
        setupListeners();
        updatePreview();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.CustomizeToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        petNameInput = findViewById(R.id.PetNameInput);
        creatureTypeGroup = findViewById(R.id.CreatureTypeGroup);

        bodyColorInput = findViewById(R.id.BodyColorInput);
        accentColorInput = findViewById(R.id.AccentColorInput);
        outlineColorInput = findViewById(R.id.OutlineColorInput);

        bodyColorPreview = findViewById(R.id.BodyColorPreview);
        accentColorPreview = findViewById(R.id.AccentColorPreview);
        outlineColorPreview = findViewById(R.id.OutlineColorPreview);

        hatSpinner = findViewById(R.id.HatSpinner);
        accessorySpinner = findViewById(R.id.AccessorySpinner);
        previewDescription = findViewById(R.id.PreviewDescription);
        colorsCard = findViewById(R.id.ColorsCard);

        // Setup spinners
        ArrayAdapter<String> hatAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PetAppearance.HAT_NAMES);
        hatSpinner.setAdapter(hatAdapter);

        ArrayAdapter<String> accessoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PetAppearance.ACCESSORY_NAMES);
        accessorySpinner.setAdapter(accessoryAdapter);

        MaterialButton saveButton = findViewById(R.id.SaveCustomizeButton);
        saveButton.setOnClickListener(v -> saveAndClose());

        MaterialButton resetButton = findViewById(R.id.ResetCustomizeButton);
        resetButton.setOnClickListener(v -> resetToDefaults());
    }

    private void loadCurrentSettings() {
        petNameInput.setText(PetAppearance.get().petName);

        // Set creature type radio button
        switch (PetAppearance.get().creatureType) {
            case 0: ((RadioButton) findViewById(R.id.CreatureGoose)).setChecked(true); break;
            case 1: ((RadioButton) findViewById(R.id.CreatureDuck)).setChecked(true); break;
            case 2: ((RadioButton) findViewById(R.id.CreatureChick)).setChecked(true); break;
            case 3: ((RadioButton) findViewById(R.id.CreatureCustom)).setChecked(true); break;
        }

        // Load colors
        bodyColorInput.setText(formatColor(PetAppearance.get().bodyColor));
        accentColorInput.setText(formatColor(PetAppearance.get().accentColor));
        outlineColorInput.setText(formatColor(PetAppearance.get().outlineColor));

        updateColorPreviews();

        // Load accessory selections
        hatSpinner.setSelection(PetAppearance.get().hatId);
        accessorySpinner.setSelection(PetAppearance.get().accessoryId);

        // Show/hide colors based on creature type
        updateColorsVisibility();
    }

    private void setupListeners() {
        // Pet name change
        petNameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        });

        // Creature type change
        creatureTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int type = 0;
            if (checkedId == R.id.CreatureGoose) type = 0;
            else if (checkedId == R.id.CreatureDuck) type = 1;
            else if (checkedId == R.id.CreatureChick) type = 2;
            else if (checkedId == R.id.CreatureCustom) type = 3;

            applyCreaturePreset(type);
            updateColorsVisibility();
            updatePreview();
        });

        // Color input changes
        TextWatcher colorWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateColorPreviews();
            }
        };
        bodyColorInput.addTextChangedListener(colorWatcher);
        accentColorInput.addTextChangedListener(colorWatcher);
        outlineColorInput.addTextChangedListener(colorWatcher);

        // Spinner changes
        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        hatSpinner.setOnItemSelectedListener(spinnerListener);
        accessorySpinner.setOnItemSelectedListener(spinnerListener);
    }

    private void applyCreaturePreset(int type) {
        if (type == 3) return; // Custom - don't change colors

        int[][] presets = {
            {0xFFFFFFFF, 0xFFFFA500, 0xFFD3D3D3}, // Goose
            {0xFFFFEB3B, 0xFFFFA500, 0xFFE0E0E0}, // Duck
            {0xFFFFF9C4, 0xFFFF7043, 0xFFFFE0B2}  // Chick
        };

        bodyColorInput.setText(formatColor(presets[type][0]));
        accentColorInput.setText(formatColor(presets[type][1]));
        outlineColorInput.setText(formatColor(presets[type][2]));
        updateColorPreviews();
    }

    private void updateColorsVisibility() {
        int checkedId = creatureTypeGroup.getCheckedRadioButtonId();
        boolean isCustom = (checkedId == R.id.CreatureCustom);

        // Enable/disable color inputs based on custom selection
        bodyColorInput.setEnabled(isCustom);
        accentColorInput.setEnabled(isCustom);
        outlineColorInput.setEnabled(isCustom);
    }

    private void updateColorPreviews() {
        try {
            int bodyColor = parseColor(bodyColorInput.getText().toString());
            bodyColorPreview.setBackgroundColor(bodyColor);
        } catch (Exception e) {
            bodyColorPreview.setBackgroundColor(Color.WHITE);
        }

        try {
            int accentColor = parseColor(accentColorInput.getText().toString());
            accentColorPreview.setBackgroundColor(accentColor);
        } catch (Exception e) {
            accentColorPreview.setBackgroundColor(Color.parseColor("#FFA500"));
        }

        try {
            int outlineColor = parseColor(outlineColorInput.getText().toString());
            outlineColorPreview.setBackgroundColor(outlineColor);
        } catch (Exception e) {
            outlineColorPreview.setBackgroundColor(Color.LTGRAY);
        }
    }

    private void updatePreview() {
        String name = petNameInput.getText().toString();
        if (name.isEmpty()) name = "Goozy";

        int creatureType = getSelectedCreatureType();
        String creatureName = PetAppearance.CREATURE_NAMES[creatureType];

        int hatId = hatSpinner.getSelectedItemPosition();
        int accessoryId = accessorySpinner.getSelectedItemPosition();

        StringBuilder desc = new StringBuilder();
        desc.append(name).append(" the ").append(creatureName);

        if (hatId > 0) {
            desc.append("\nwith ").append(PetAppearance.HAT_NAMES[hatId]);
        }
        if (accessoryId > 0) {
            desc.append(hatId > 0 ? " and " : "\nwith ");
            desc.append(PetAppearance.ACCESSORY_NAMES[accessoryId]);
        }

        previewDescription.setText(desc.toString());
    }

    private int getSelectedCreatureType() {
        int checkedId = creatureTypeGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.CreatureGoose) return 0;
        if (checkedId == R.id.CreatureDuck) return 1;
        if (checkedId == R.id.CreatureChick) return 2;
        return 3; // Custom
    }

    private void saveAndClose() {
        // Save name
        String name = petNameInput.getText().toString();
        if (name.isEmpty()) name = "Goozy";
        PetAppearance.get().petName = name;

        // Save creature type
        PetAppearance.get().creatureType = getSelectedCreatureType();

        // Save colors
        try {
            PetAppearance.get().bodyColor = parseColor(bodyColorInput.getText().toString());
            PetAppearance.get().accentColor = parseColor(accentColorInput.getText().toString());
            PetAppearance.get().outlineColor = parseColor(outlineColorInput.getText().toString());
        } catch (Exception e) {
            // Keep existing colors on error
        }

        // Save accessories
        PetAppearance.get().hatId = hatSpinner.getSelectedItemPosition();
        PetAppearance.get().accessoryId = accessorySpinner.getSelectedItemPosition();

        // Apply changes
        PetAppearance.get().applyToGoose();

        Utils.showToast(this, "Customization saved!");
        finish();
    }

    private void resetToDefaults() {
        PetAppearance.get().reset();
        loadCurrentSettings();
        updatePreview();
        Utils.showToast(this, "Reset to defaults");
    }

    private String formatColor(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private int parseColor(String colorStr) {
        if (!colorStr.startsWith("#")) {
            colorStr = "#" + colorStr;
        }
        return Color.parseColor(colorStr);
    }

    /**
     * Simple TextWatcher that only requires afterTextChanged implementation.
     */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
