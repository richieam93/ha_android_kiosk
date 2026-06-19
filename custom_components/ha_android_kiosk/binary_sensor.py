from __future__ import annotations

from typing import Any

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import _async_load_config
from .const import SIGNAL_DEVICE_REGISTERED
from .entity_base import KioskEntity, is_recent_iso

BINARY_SENSORS: tuple[tuple[str, str, BinarySensorDeviceClass | None], ...] = (
    ("online", "Online", BinarySensorDeviceClass.CONNECTIVITY),
    ("screen_blank", "Bildschirm schwarz", None),
    ("motion_enabled", "Bewegungssensor aktiv", None),
    ("motion_detected", "Bewegung erkannt", BinarySensorDeviceClass.MOTION),
    ("camera_front_active", "Frontkamera aktiv", None),
    ("camera_motion_enabled", "Frontkamera-Bewegung aktiv", None),
    ("camera_motion_detected", "Frontkamera-Bewegung erkannt", BinarySensorDeviceClass.MOTION),
    ("light_sensor_available", "Lichtsensor verfügbar", None),
    ("camera_permission", "Kamera-Berechtigung", None),
    ("microphone_permission", "Mikrofon-Berechtigung", None),
    ("write_settings_permission", "Systemhelligkeit erlaubt", None),
    ("media_muted", "Medien stumm", None),
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
        async_add_entities([KioskBinarySensor(hass, device_id, key, name, device_class) for key, name, device_class in BINARY_SENSORS])

    config = await _async_load_config(hass)
    for device_id in config.get("devices", {}):
        await add_device(device_id)

    @callback
    def _new_device(device_id: str) -> None:
        hass.async_create_task(add_device(device_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_DEVICE_REGISTERED, _new_device))


class KioskBinarySensor(KioskEntity, BinarySensorEntity):
    def __init__(self, hass: HomeAssistant, device_id: str, key: str, name: str, device_class: BinarySensorDeviceClass | None) -> None:
        super().__init__(hass, device_id, key)
        self._attr_name = name
        self._attr_device_class = device_class

    @property
    def is_on(self) -> bool | None:
        state = self.device_state
        if self._key == "online":
            return is_recent_iso(state.get("last_seen") or self.device_config.get("last_seen"), 180)
        value: Any = state.get(self._key)
        if value is None:
            return None
        return bool(value)
