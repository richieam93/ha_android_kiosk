from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from homeassistant.core import callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import Entity

from .const import DOMAIN, SIGNAL_DEVICE_STATE_UPDATED


class KioskEntity(Entity):
    _attr_has_entity_name = True

    def __init__(self, hass, device_id: str, key: str, translation_key: str | None = None) -> None:
        self.hass = hass
        self._device_id = device_id
        self._key = key
        self._attr_unique_id = f"{device_id}_{key}"
        if translation_key:
            self._attr_translation_key = translation_key

    @property
    def device_info(self) -> DeviceInfo:
        cfg = self.device_config
        return DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=cfg.get("name") or self._device_id,
            manufacturer=cfg.get("manufacturer") or "Android",
            model=cfg.get("model") or "Kiosk device",
            sw_version=cfg.get("app_version"),
        )

    @property
    def device_config(self) -> dict[str, Any]:
        return self.hass.data.get(DOMAIN, {}).get("config_cache", {}).get("devices", {}).get(self._device_id, {})

    @property
    def device_state(self) -> dict[str, Any]:
        return self.hass.data.get(DOMAIN, {}).get("device_states", {}).get(self._device_id, {})

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass,
                SIGNAL_DEVICE_STATE_UPDATED,
                self._handle_device_update,
            )
        )

    @callback
    def _handle_device_update(self, device_id: str) -> None:
        if device_id == self._device_id:
            self.async_write_ha_state()

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        state = self.device_state
        attrs = {
            "device_id": self._device_id,
            "last_seen": state.get("last_seen") or self.device_config.get("last_seen"),
            "last_command": state.get("last_command") or self.device_config.get("last_command"),
        }
        return {k: v for k, v in attrs.items() if v not in (None, "")}


def is_recent_iso(value: str | None, seconds: int = 180) -> bool:
    if not value:
        return False
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return (datetime.now(timezone.utc) - parsed).total_seconds() < seconds
    except Exception:
        return False
