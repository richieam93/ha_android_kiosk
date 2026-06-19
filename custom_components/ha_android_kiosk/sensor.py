from __future__ import annotations

from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import _async_load_config
from .const import DOMAIN, SIGNAL_DEVICE_REGISTERED
from .entity_base import KioskEntity

SENSORS: tuple[tuple[str, str], ...] = (
    ("status", "Status"),
    ("current_url", "Aktuelle Seite"),
    ("last_command", "Letzter Befehl"),
    ("camera", "Aktive Kamera"),
    ("camera_motion_score", "Frontkamera-Bewegungswert"),
    ("light_lux", "Lichtstärke"),
    ("media_state", "Medienstatus"),
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
        async_add_entities([KioskSensor(hass, device_id, key, name) for key, name in SENSORS])

    config = await _async_load_config(hass)
    for device_id in config.get("devices", {}):
        await add_device(device_id)

    @callback
    def _new_device(device_id: str) -> None:
        hass.async_create_task(add_device(device_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_DEVICE_REGISTERED, _new_device))


class KioskSensor(KioskEntity, SensorEntity):
    def __init__(self, hass: HomeAssistant, device_id: str, key: str, name: str) -> None:
        super().__init__(hass, device_id, key)
        self._attr_name = name

    @property
    def native_value(self) -> Any:
        state = self.device_state
        cfg = self.device_config
        if self._key == "status":
            return state.get("status") or state.get("last_status") or cfg.get("last_status") or "unknown"
        if self._key == "current_url":
            return state.get("current_url") or cfg.get("current_url") or ""
        if self._key == "last_command":
            return state.get("command") or state.get("last_command") or cfg.get("last_command") or ""
        return state.get(self._key)
