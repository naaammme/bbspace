package com.naaammme.bbspace.feature.settings

import androidx.compose.runtime.Composable
import com.naaammme.bbspace.core.designsystem.component.SettingChoiceCard
import com.naaammme.bbspace.core.designsystem.component.SettingDialogCard
import com.naaammme.bbspace.core.designsystem.component.SettingDiscreteSliderCard
import com.naaammme.bbspace.core.designsystem.component.SettingListCard as CoreSettingListCard
import com.naaammme.bbspace.core.designsystem.component.SettingSwitchCard
import com.naaammme.bbspace.core.designsystem.component.bindSetting
import com.naaammme.bbspace.core.settings.Setting

@Composable
internal fun SettingSwitch(
    title: String,
    setting: Setting<Boolean>,
    subtitle: String? = null
) {
    val (checked, setValue) = setting.flow.bindSetting(setting.current, setting::commit)
    SettingSwitchCard(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = setValue
    )
}

@Composable
internal fun SettingDiscreteSlider(
    title: String,
    setting: Setting<Float>,
    values: List<Float>,
    valueLabel: (Float) -> String,
    fallbackValue: Float = setting.defaultValue,
    subtitle: String? = null,
    minLabel: String? = null,
    midLabel: String? = null,
    maxLabel: String? = null
) {
    val (current, setValue) = setting.flow.bindSetting(setting.current, setting::commit)
    SettingDiscreteSliderCard(
        title = title,
        currentValue = current,
        values = values,
        valueLabel = valueLabel,
        onValueSelected = setValue,
        subtitle = subtitle,
        fallbackValue = fallbackValue,
        minLabel = minLabel,
        midLabel = midLabel,
        maxLabel = maxLabel
    )
}

@Composable
internal fun <T> SettingChoiceChips(
    title: String,
    setting: Setting<T>,
    options: List<T>,
    label: (T) -> String,
    subtitle: String? = null
) {
    val (selected, setValue) = setting.flow.bindSetting(setting.current, setting::commit)
    SettingChoiceCard(
        title = title,
        subtitle = subtitle,
        currentValue = selected,
        options = options,
        label = label,
        onSelect = setValue
    )
}

@Composable
internal fun <T> SettingListCard(
    title: String,
    setting: Setting<T>,
    options: List<T>,
    label: (T) -> String,
    subtitle: String? = null
) {
    val (selected, setValue) = setting.flow.bindSetting(setting.current, setting::commit)
    CoreSettingListCard(
        title = title,
        subtitle = subtitle,
        currentValue = selected,
        options = options,
        label = label,
        onSelect = setValue
    )
}

@Composable
internal fun <T : Any> SettingDialogChoice(
    title: String,
    dialogTitle: String,
    setting: Setting<T>,
    options: List<T>,
    label: (T) -> String,
    subtitle: String? = null,
    valueText: (T) -> String = label,
    description: ((T) -> String?)? = null,
    keyOf: (T) -> Any = { it }
) {
    val (current, setValue) = setting.flow.bindSetting(setting.current, setting::commit)
    SettingDialogCard(
        title = title,
        dialogTitle = dialogTitle,
        subtitle = subtitle,
        currentValue = current,
        options = options,
        label = label,
        valueText = valueText,
        description = description,
        keyOf = keyOf,
        onSelect = setValue,
        dismissText = "关闭"
    )
}
