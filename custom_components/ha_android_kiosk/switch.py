from __future__ import annotations

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect, async_dispatcher_send
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import _async_load_config, _fire_command
from .const import DOMAIN, SIGNAL_DEVICE_REGISTERED, SIGNAL_DEVICE_STATE_UPDATED
from .entity_base import KioskEntity

SWITCHES: tuple[tuple[str, str], ...] = (
    ("screen", "Bildschirm ein"),
    ("camera_front", "Frontkamera"),
    ("camera_motion", "Frontkamera-Bewegungserkennung"),
    ("accelerometer_motion", "Bewegungssensor"),
    ("rotation", "Seitenrotation"),
)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    added: set[str] = set()

    async def add_device(device_id: str) -> None:
        if device_id in added:
            return
        config = await _async_load_config(hass)
        if device_id not in config.get("devices", {}):
            return
        added.add(device_id)
        async_add_entities([KioskSwitch(hass, device_id, key, name) for key, name in SWITCHES])

    config = await _async_load_config(hass)
    for device_id in config.get("devices", {}):
        await add_device(device_id)

    @callback
    def _new_device(device_id: str) -> None:
        hass.async_create_task(add_device(device_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_DEVICE_REGISTERED, _new_device))


class KioskSwitch(KioskEntity, SwitchEntity):
    def __init__(self, hass: HomeAssistant, device_id: str, key: str, name: str) -> None:
        super().__init__(hass, device_id, key)
        self._attr_name = name

    @property
    def is_on(self) -> bool | None:
        state = self.device_state
        if self._key == "screen":
            if "screen_blank" not in state:
                return None
            return not bool(state.get("screen_blank"))
        if self._key == "camera_front":
            return bool(state.get("camera_front_active", False))
        if self._key == "camera_motion":
            return bool(state.get("camera_motion_enabled", False))
        if self._key == "accelerometer_motion":
            return bool(state.get("motion_enabled", False))
        if self._key == "rotation":
            return bool(state.get("rotation_enabled", False))
        return None

    async def async_turn_on(self, **kwargs) -> None:
        await self._set(True)

    async def async_turn_off(self, **kwargs) -> None:
        await self._set(False)

    async def _set(self, enabled: bool) -> None:
        state = self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})
        if self._key == "screen":
            state["screen_blank"] = not enabled
            await _fire_command(self.hass, self._device_id, "screen", {"value": "on" if enabled else "blank"})
        elif self._key == "camera_front":
            state["camera_front_active"] = enabled
            await _fire_command(self.hass, self._device_id, "camera_front", {"enabled": enabled, "motion": bool(state.get("camera_motion_enabled", False))})
        elif self._key == "camera_motion":
            state["camera_motion_enabled"] = enabled
            await _fire_command(self.hass, self._device_id, "camera_motion_detection", {"enabled": enabled, "facing": "front"})
        elif self._key == "accelerometer_motion":
            state["motion_enabled"] = enabled
            await _fire_command(self.hass, self._device_id, "motion_detection", {"enabled": enabled})
        elif self._key == "rotation":
            state["rotation_enabled"] = enabled
            await _fire_command(self.hass, self._device_id, "rotation_start" if enabled else "rotation_stop", {})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)
