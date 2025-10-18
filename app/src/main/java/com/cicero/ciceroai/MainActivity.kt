package com.cicero.ciceroai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import com.cicero.ciceroai.R
import com.cicero.ciceroai.settings.PresetOption
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modelSpinnerAdapter: ArrayAdapter<String>
    private lateinit var standardModelSpinnerAdapter: ArrayAdapter<String>
    private var suppressModelSelection = false
    private var suppressStandardModelSelection = false
    private val presetButtonIdToOption = mapOf(
        R.id.presetBatterySaverButton to PresetOption.BATTERY_SAVER,
        R.id.presetBalancedButton to PresetOption.BALANCED,
        R.id.presetTurboButton to PresetOption.TURBO,
        R.id.presetCustomButton to PresetOption.CUSTOM
    )
    private val optionToPresetButtonId = presetButtonIdToOption.entries.associate { (id, preset) -> preset to id }
    private var suppressPresetSelection = false
    private lateinit var modelSettingAdapter: ArrayAdapter<String>
    private lateinit var runtimeSettingAdapter: ArrayAdapter<String>
    private lateinit var samplingSettingAdapter: ArrayAdapter<String>
    private lateinit var promptPersonaAdapter: ArrayAdapter<String>
    private lateinit var memorySettingAdapter: ArrayAdapter<String>
    private lateinit var codingWorkspaceAdapter: ArrayAdapter<String>
    private lateinit var privacySettingAdapter: ArrayAdapter<String>
    private lateinit var storageSettingAdapter: ArrayAdapter<String>
    private lateinit var diagnosticsSettingAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        modelSettingAdapter = setupDropdown(
            binding.modelSettingInput,
            R.array.settings_model_options
        ) { value ->
            viewModel.onModelSettingChanged(value)
        }
        runtimeSettingAdapter = setupDropdown(
            binding.runtimeInput,
            R.array.settings_runtime_options
        ) { value ->
            viewModel.onRuntimeSettingChanged(value)
        }
        samplingSettingAdapter = setupDropdown(
            binding.samplingInput,
            R.array.settings_sampling_options
        ) { value ->
            viewModel.onSamplingSettingChanged(value)
        }
        promptPersonaAdapter = setupDropdown(
            binding.promptPersonaInput,
            R.array.settings_prompt_persona_options
        ) { value ->
            viewModel.onPromptPersonaSettingChanged(value)
        }
        memorySettingAdapter = setupDropdown(
            binding.memoryInput,
            R.array.settings_memory_options
        ) { value ->
            viewModel.onMemorySettingChanged(value)
        }
        codingWorkspaceAdapter = setupDropdown(
            binding.codingWorkspaceInput,
            R.array.settings_coding_workspace_options
        ) { value ->
            viewModel.onCodingWorkspaceSettingChanged(value)
        }
        privacySettingAdapter = setupDropdown(
            binding.privacyInput,
            R.array.settings_privacy_options
        ) { value ->
            viewModel.onPrivacySettingChanged(value)
        }
        storageSettingAdapter = setupDropdown(
            binding.storageInput,
            R.array.settings_storage_options
        ) { value ->
            viewModel.onStorageSettingChanged(value)
        }
        diagnosticsSettingAdapter = setupDropdown(
            binding.diagnosticsInput,
            R.array.settings_diagnostics_options
        ) { value ->
            viewModel.onDiagnosticsSettingChanged(value)
        }

        binding.contextSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val contextSize = value.toInt()
                binding.contextValueLabel.text = getString(R.string.settings_context_value, contextSize)
                viewModel.onContextSizeChanged(contextSize)
            }
        }
        binding.gpuLayersSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val gpuLayers = value.toInt()
                binding.gpuLayersValueLabel.text = getString(R.string.settings_gpu_layers_value, gpuLayers)
                viewModel.onGpuLayersChanged(gpuLayers)
            }
        }
        binding.batchSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val batchSize = value.toInt()
                binding.batchValueLabel.text = getString(R.string.settings_batch_value, batchSize)
                viewModel.onBatchSizeChanged(batchSize)
            }
        }
        binding.temperatureSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.temperatureValueLabel.text = getString(R.string.settings_temperature_value, value)
                viewModel.onTemperatureChanged(value)
            }
        }
        binding.topPSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.topPValueLabel.text = getString(R.string.settings_top_p_value, value)
                viewModel.onTopPChanged(value)
            }
        }

        modelSpinnerAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.modelSpinner.adapter = adapter
        }

        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressModelSelection) {
                    return
                }
                val modelName = modelSpinnerAdapter.getItem(position) ?: return
                viewModel.onModelSelected(modelName)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op
            }
        }

        binding.topAppBar.setNavigationOnClickListener { showNavigationMenu() }

        binding.presetToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressPresetSelection) {
                return@addOnButtonCheckedListener
            }
            val preset = presetButtonIdToOption[checkedId] ?: return@addOnButtonCheckedListener
            viewModel.onPresetSelected(preset)
        }

        standardModelSpinnerAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.standardModelSpinner.adapter = adapter
        }

        binding.standardModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressStandardModelSelection) {
                    return
                }
                viewModel.onStandardModelSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op
            }
        }

        binding.standardModelDownloadButton.setOnClickListener {
            viewModel.onStandardModelDownloadRequested()
        }

        binding.manualDownloadInputLayout.setEndIconOnClickListener {
            val url = binding.manualDownloadInput.text?.toString().orEmpty()
            viewModel.onManualDownloadRequested(url)
        }

        binding.runButton.setOnClickListener {
            hideKeyboard()
            val prompt = binding.promptInput.text?.toString().orEmpty()
            viewModel.runInference(prompt)
        }

        binding.promptInput.doAfterTextChanged {
            viewModel.onPromptTextChanged()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        binding.homeContainer.isVisible = state.currentPage == MainPage.HOME
        binding.downloadContainer.isVisible = state.currentPage == MainPage.DOWNLOAD
        binding.settingsContainer.isVisible = state.currentPage == MainPage.SETTINGS

        binding.topAppBar.title = when (state.currentPage) {
            MainPage.HOME -> getString(R.string.title_home)
            MainPage.DOWNLOAD -> getString(R.string.title_download)
            MainPage.SETTINGS -> getString(R.string.title_settings)
        }

        binding.modelStatus.text = state.modelStatus
        binding.runButton.isEnabled = state.isRunButtonEnabled

        binding.downloadProgressIndicator.isVisible = state.downloadProgressVisible
        binding.downloadProgressIndicator.isIndeterminate = state.downloadProgressIndeterminate
        if (!state.downloadProgressIndeterminate) {
            binding.downloadProgressIndicator.setProgressCompat(state.downloadProgressValue, true)
        }
        binding.downloadProgressInfo.isVisible = state.downloadProgressVisible
        binding.downloadProgressPercent.text = state.downloadProgressPercentText
        binding.downloadProgressData.text = state.downloadProgressDataText

        binding.standardModelDownloadButton.isEnabled = state.isDownloadButtonEnabled
        binding.manualDownloadInputLayout.setEndIconVisible(state.isDownloadButtonEnabled)

        updateStandardModelSpinner(state.standardModels, state.selectedStandardModelIndex, state.isDownloadButtonEnabled)
        renderSelectedStandardModel(state)

        binding.promptInputLayout.error = state.promptError
        binding.outputView.text = state.outputText
        binding.logTicker.text = state.logMessages.lastOrNull() ?: getString(R.string.log_placeholder)

        updateModelSpinner(state.downloadedModels, state.selectedModelName)
        updateDownloadedModels(state.downloadedModels, state.selectedModelName)

        val expectedButtonId = optionToPresetButtonId[state.selectedPreset]
        if (expectedButtonId != null && binding.presetToggleGroup.checkedButtonId != expectedButtonId) {
            suppressPresetSelection = true
            binding.presetToggleGroup.check(expectedButtonId)
            suppressPresetSelection = false
        }
        binding.presetDescription.text = getString(state.selectedPreset.descriptionRes)
        updateSettingInput(binding.modelSettingInput, modelSettingAdapter, state.modelSetting)
        updateSettingInput(binding.runtimeInput, runtimeSettingAdapter, state.runtimeSetting)
        updateSettingInput(binding.samplingInput, samplingSettingAdapter, state.samplingSetting)
        updateSettingInput(binding.promptPersonaInput, promptPersonaAdapter, state.promptPersonaSetting)
        updateSettingInput(binding.memoryInput, memorySettingAdapter, state.memorySetting)
        updateSettingInput(binding.codingWorkspaceInput, codingWorkspaceAdapter, state.codingWorkspaceSetting)
        updateSettingInput(binding.privacyInput, privacySettingAdapter, state.privacySetting)
        updateSettingInput(binding.storageInput, storageSettingAdapter, state.storageSetting)
        updateSettingInput(binding.diagnosticsInput, diagnosticsSettingAdapter, state.diagnosticsSetting)

        val vulkanStatus = state.isVulkanAvailable
        if (vulkanStatus != null) {
            val (statusTextRes, statusColorAttr) = if (vulkanStatus) {
                R.string.settings_vulkan_status_on to AppCompatR.attr.colorPrimary
            } else {
                R.string.settings_vulkan_status_off to AppCompatR.attr.colorError
            }
            binding.vulkanStatusTitle.isVisible = true
            binding.vulkanStatusLabel.isVisible = true
            binding.vulkanStatusLabel.text = getString(statusTextRes)
            val statusColor = MaterialColors.getColor(binding.vulkanStatusLabel, statusColorAttr)
            binding.vulkanStatusLabel.setTextColor(statusColor)
        } else {
            binding.vulkanStatusTitle.isVisible = false
            binding.vulkanStatusLabel.isVisible = false
        }

        if (!binding.contextSlider.isPressed) {
            binding.contextSlider.value = state.contextSize.toFloat()
        }
        binding.contextValueLabel.text = getString(R.string.settings_context_value, state.contextSize)

        if (!binding.gpuLayersSlider.isPressed) {
            binding.gpuLayersSlider.value = state.nGpuLayers.toFloat()
        }
        binding.gpuLayersValueLabel.text = getString(R.string.settings_gpu_layers_value, state.nGpuLayers)

        if (!binding.batchSlider.isPressed) {
            binding.batchSlider.value = state.batchSize.toFloat()
        }
        binding.batchValueLabel.text = getString(R.string.settings_batch_value, state.batchSize)

        if (!binding.temperatureSlider.isPressed) {
            binding.temperatureSlider.value = state.temperature
        }
        binding.temperatureValueLabel.text = getString(R.string.settings_temperature_value, state.temperature)

        if (!binding.topPSlider.isPressed) {
            binding.topPSlider.value = state.topP
        }
        binding.topPValueLabel.text = getString(R.string.settings_top_p_value, state.topP)
    }

    private fun updateDownloadedModels(models: List<String>, selectedModel: String?) {
        binding.downloadedModelsList.removeAllViews()
        if (models.isEmpty()) {
            binding.downloadedModelsList.isVisible = false
            binding.downloadedModelsEmpty.isVisible = true
            return
        }

        binding.downloadedModelsList.isVisible = true
        binding.downloadedModelsEmpty.isVisible = false
        models.forEachIndexed { index, modelName ->
            val card = createDownloadedModelCard(modelName, modelName == selectedModel)
            if (index == models.lastIndex) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = 0
            }
            binding.downloadedModelsList.addView(card)
        }
    }

    private fun updateModelSpinner(models: List<String>, selectedModel: String?) {
        suppressModelSelection = true
        modelSpinnerAdapter.clear()
        modelSpinnerAdapter.addAll(models)
        modelSpinnerAdapter.notifyDataSetChanged()

        val hasModels = models.isNotEmpty()
        binding.modelSpinner.isEnabled = hasModels
        binding.modelSpinner.isVisible = hasModels
        binding.modelSelectorLabel.isVisible = hasModels

        if (hasModels) {
            val selectedIndex = selectedModel?.let { models.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            binding.modelSpinner.setSelection(selectedIndex, false)
        }

        suppressModelSelection = false
    }

    private fun updateStandardModelSpinner(
        models: List<StandardModelInfo>,
        selectedIndex: Int,
        isDownloadEnabled: Boolean
    ) {
        suppressStandardModelSelection = true
        standardModelSpinnerAdapter.clear()
        standardModelSpinnerAdapter.addAll(models.map { it.name })
        standardModelSpinnerAdapter.notifyDataSetChanged()

        val hasModels = models.isNotEmpty()
        binding.standardModelSpinner.isEnabled = hasModels && isDownloadEnabled
        binding.standardModelSpinner.isVisible = hasModels

        if (hasModels) {
            val index = selectedIndex.coerceIn(0, models.lastIndex)
            binding.standardModelSpinner.setSelection(index, false)
        } else {
            binding.standardModelName.text = getString(R.string.standard_models_empty)
            binding.standardModelSize.text = ""
            binding.standardModelDescription.text = ""
            binding.standardModelLinks.removeAllViews()
        }

        suppressStandardModelSelection = false
    }

    private fun renderSelectedStandardModel(state: MainUiState) {
        val model = state.standardModels.getOrNull(state.selectedStandardModelIndex)
        if (model == null) {
            binding.standardModelName.text = getString(R.string.standard_models_empty)
            binding.standardModelSize.text = ""
            binding.standardModelDescription.text = ""
            binding.standardModelLinks.removeAllViews()
            binding.standardModelDownloadButton.isEnabled = false
            return
        }

        binding.standardModelName.text = model.name
        binding.standardModelSize.text = getString(R.string.standard_model_size, model.sizeLabel)
        binding.standardModelDescription.text = model.description
        binding.standardModelDownloadButton.isEnabled = state.isDownloadButtonEnabled
        renderRepositoryLinks(model)
    }

    private fun renderRepositoryLinks(model: StandardModelInfo) {
        binding.standardModelLinks.removeAllViews()
        if (model.repositoryLinks.isEmpty()) {
            return
        }

        model.repositoryLinks.forEach { link ->
            val chip = Chip(this).apply {
                text = link.label
                isCheckable = false
                isClickable = true
                isFocusable = true
                setOnClickListener { openLink(link.url) }
            }
            binding.standardModelLinks.addView(chip)
        }
    }

    private fun updateSettingInput(
        input: MaterialAutoCompleteTextView,
        adapter: ArrayAdapter<String>,
        value: String
    ) {
        if (value.isNotEmpty() && adapter.getPosition(value) == -1) {
            adapter.add(value)
        }
        if (input.isPopupShowing || input.isFocused) {
            return
        }
        val currentValue = input.text?.toString().orEmpty()
        if (currentValue != value) {
            input.setText(value, false)
        }
    }

    private fun setupDropdown(
        input: MaterialAutoCompleteTextView,
        @ArrayRes optionsRes: Int,
        onSelected: (String) -> Unit
    ): ArrayAdapter<String> {
        val options = resources.getStringArray(optionsRes).toMutableList()
        return ArrayAdapter(this, android.R.layout.simple_list_item_1, options).also { adapter ->
            input.setAdapter(adapter)
            input.setOnItemClickListener { _, _, position, _ ->
                adapter.getItem(position)?.let(onSelected)
            }
            input.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    (view as MaterialAutoCompleteTextView).showDropDown()
                }
            }
            input.setOnClickListener {
                input.showDropDown()
            }
        }
    }

    private fun createDownloadedModelCard(modelName: String, isSelected: Boolean): MaterialCardView {
        val context = this
        val card = MaterialCardView(context).apply {
            val padding = resources.getDimensionPixelSize(R.dimen.spacing_medium)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            this.layoutParams = layoutParams
            radius = resources.getDimension(R.dimen.downloaded_model_card_radius)
            strokeWidth = if (isSelected) resources.getDimensionPixelSize(R.dimen.downloaded_model_card_stroke) else 0
            strokeColor = MaterialColors.getColor(binding.root, AppCompatR.attr.colorPrimary)
            setContentPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            setOnClickListener { viewModel.onModelSelected(modelName) }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(context).apply {
            text = modelName
            TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_MaterialComponents_Subtitle1)
        }

        val subtitle = TextView(context).apply {
            text = if (isSelected) getString(R.string.downloaded_model_active) else getString(R.string.downloaded_model_select)
            TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_MaterialComponents_Body2)
        }

        container.addView(title)
        container.addView(subtitle)
        card.addView(container)
        return card
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun showNavigationMenu() {
        PopupMenu(this, binding.topAppBar, Gravity.START).apply {
            menuInflater.inflate(R.menu.main_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_home -> {
                        viewModel.showHomePage()
                        true
                    }
                    R.id.action_download -> {
                        viewModel.showDownloadPage()
                        true
                    }
                    R.id.action_settings -> {
                        viewModel.showSettingsPage()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun hideKeyboard() {
        binding.promptInput.clearFocus()
        val view: View = binding.promptInput
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
    }
}
