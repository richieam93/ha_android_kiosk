from __future__ import annotations

from homeassistant.components.number import NumberEntity, NumberMode
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect, async_dispatcher_send
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import _async_load_config, _fire_command
from .const import DOMAIN, SIGNAL_DEVICE_REGISTERED, SIGNAL_DEVICE_STATE_UPDATED
from .entity_base import KioskEntity

NUMBERS: tuple[tuple[str, str, str], ...] = (
    ("brightness", "Bildschirmhelligkeit", "brightness"),
    ("volume", "Lautstärke", "volume"),
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
        async_add_entities([KioskNumber(hass, device_id, key, name, command) for key, name, command in NUMBERS])

    config = await _async_load_config(hass)
    for device_id in config.get("devices", {}):
        await add_device(device_id)

    @callback
    def _new_device(device_id: str) -> None:
        hass.async_create_task(add_device(device_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_DEVICE_REGISTERED, _new_device))


class KioskNumber(KioskEntity, NumberEntity):
    _attr_native_min_value = 0
    _attr_native_max_value = 100
    _attr_native_step = 1
    _attr_mode = NumberMode.SLIDER
    _attr_native_unit_of_measurement = "%"

    def __init__(self, hass: HomeAssistant, device_id: str, key: str, name: str, command: str) -> None:
        super().__init__(hass, device_id, key)
        self._attr_name = name
        self._command = command

    @property
    def native_value(self) -> float | None:
        value = self.device_state.get(self._key)
        if value is None:
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None

    async def async_set_native_value(self, value: float) -> None:
        clipped = max(0, min(100, float(value)))
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})[self._key] = clipped
        if self._command == "brightness":
            await _fire_command(self.hass, self._device_id, "brightness", {"value": clipped, "system": False})
        else:
            await _fire_command(self.hass, self._device_id, "volume", {"value": clipped, "stream": "music"})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)
